package com.theblastbeast.security;

import com.theblastbeast.exception.ErrorCode;
import com.theblastbeast.exception.RateLimitException;
import com.theblastbeast.properties.AuthProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Límite de intentos fallidos de login en memoria.
 *
 * <p>Se cuentan los fallos por IP y por IP + email dentro de una ventana
 * deslizante. Al superar {@code blastbeast.auth.login-max-attempts}, el login
 * responde {@code 429} con {@code Retry-After}. Un login correcto limpia ambos
 * contadores.
 *
 * <p>Es un límite por instancia: con una sola instancia de la API es suficiente.
 * Si algún día se escala horizontalmente, habrá que compartir el estado (por
 * ejemplo, en Redis) o limitar en el proxy.
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_TRACKED_KEYS = 10_000;

    private final AuthProperties properties;
    private final Clock clock;
    private final Map<String, Deque<Instant>> failuresByIp = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> failuresByIpAndEmail = new ConcurrentHashMap<>();

    public LoginRateLimiter(AuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** Lanza {@link RateLimitException} si la IP o la cuenta ya están bloqueadas. */
    public void check(String ip, String email) {
        Instant now = clock.instant();
        Duration window = properties.loginWindow();
        long retryAfter = Math.max(
                retryAfter(failuresByIp.get(ipKey(ip)), now, window,
                        properties.loginMaxAttemptsPerIp()),
                retryAfter(failuresByIpAndEmail.get(accountKey(ip, email)), now, window,
                        properties.loginMaxAttempts()));
        if (retryAfter > 0) {
            throw new RateLimitException(ErrorCode.TOO_MANY_ATTEMPTS, retryAfter);
        }
    }

    /** Registra un intento fallido en ambos contadores. */
    public void recordFailure(String ip, String email) {
        Instant now = clock.instant();
        record(failuresByIp, ipKey(ip), now);
        record(failuresByIpAndEmail, accountKey(ip, email), now);
        evictIfNeeded(now);
    }

    /** Un login correcto limpia los fallos de esa IP y de esa cuenta. */
    public void reset(String ip, String email) {
        failuresByIp.remove(ipKey(ip));
        failuresByIpAndEmail.remove(accountKey(ip, email));
    }

    /** Solo para tests y herramientas de mantenimiento. */
    public void clear() {
        failuresByIp.clear();
        failuresByIpAndEmail.clear();
    }

    private long retryAfter(Deque<Instant> attempts, Instant now, Duration window,
            int maxAttempts) {
        if (attempts == null) {
            return 0;
        }
        synchronized (attempts) {
            prune(attempts, now, window);
            if (attempts.size() < maxAttempts) {
                return 0;
            }
            Instant unlockAt = attempts.peekFirst().plus(window);
            return Math.max(1, Duration.between(now, unlockAt).toSeconds());
        }
    }

    private void record(Map<String, Deque<Instant>> buckets, String key, Instant now) {
        Deque<Instant> attempts = buckets.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (attempts) {
            prune(attempts, now, properties.loginWindow());
            attempts.addLast(now);
        }
    }

    private void prune(Deque<Instant> attempts, Instant now, Duration window) {
        Instant threshold = now.minus(window);
        while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(threshold)) {
            attempts.removeFirst();
        }
    }

    /**
     * Evita que un ataque con muchas IPs haga crecer los mapas sin límite.
     * Primero retira ventanas ya caducadas; si aun así se supera el tope, se
     * vacía todo (el límite es una defensa, no un registro histórico).
     */
    private void evictIfNeeded(Instant now) {
        if (failuresByIp.size() + failuresByIpAndEmail.size() <= MAX_TRACKED_KEYS) {
            return;
        }
        Instant threshold = now.minus(properties.loginWindow());
        failuresByIp.entrySet().removeIf(entry -> isEmptyAfterPrune(entry.getValue(), threshold));
        failuresByIpAndEmail.entrySet()
                .removeIf(entry -> isEmptyAfterPrune(entry.getValue(), threshold));
        if (failuresByIp.size() + failuresByIpAndEmail.size() > MAX_TRACKED_KEYS) {
            clear();
        }
    }

    private boolean isEmptyAfterPrune(Deque<Instant> attempts, Instant threshold) {
        synchronized (attempts) {
            while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(threshold)) {
                attempts.removeFirst();
            }
            return attempts.isEmpty();
        }
    }

    private String ipKey(String ip) {
        return ip == null || ip.isBlank() ? "unknown" : ip;
    }

    private String accountKey(String ip, String email) {
        return ipKey(ip) + "|" + (email == null ? "" : email.trim().toLowerCase(Locale.ROOT));
    }
}
