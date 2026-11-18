package com.theblastbeast.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.theblastbeast.exception.RateLimitException;
import com.theblastbeast.properties.AuthProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LoginRateLimiter")
class LoginRateLimiterTest {

    private static final AuthProperties PROPERTIES = new AuthProperties(
            "clave-de-pruebas-con-mas-de-32-caracteres",
            Duration.ofMinutes(15),
            Duration.ofDays(30),
            "bb_refresh",
            false,
            null,
            Duration.ofMinutes(1),
            2,
            3);

    private MutableClock clock;
    private LoginRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-17T10:00:00Z"));
        limiter = new LoginRateLimiter(PROPERTIES, clock);
    }

    @Test
    @DisplayName("bloquea la cuenta tras superar el máximo de fallos")
    void bloqueaLaCuenta() {
        limiter.recordFailure("1.2.3.4", "redaccion@theblastbeast.com");
        limiter.recordFailure("1.2.3.4", "redaccion@theblastbeast.com");

        assertThatThrownBy(() -> limiter.check("1.2.3.4", "redaccion@theblastbeast.com"))
                .isInstanceOfSatisfying(RateLimitException.class, exception -> {
                    assertThat(exception.getErrorCode().getCode()).isEqualTo("AUTH-009");
                    assertThat(exception.getRetryAfterSeconds()).isEqualTo(60);
                });
    }

    @Test
    @DisplayName("bloquea la IP aunque el atacante cambie de email")
    void bloqueaLaIpConVariosEmails() {
        limiter.recordFailure("1.2.3.4", "uno@theblastbeast.com");
        limiter.recordFailure("1.2.3.4", "dos@theblastbeast.com");
        limiter.recordFailure("1.2.3.4", "tres@theblastbeast.com");

        assertThatThrownBy(() -> limiter.check("1.2.3.4", "cuatro@theblastbeast.com"))
                .isInstanceOf(RateLimitException.class);
        assertThatCode(() -> limiter.check("5.6.7.8", "cuatro@theblastbeast.com"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("vuelve a permitir cuando caduca la ventana")
    void desbloqueaAlPasarLaVentana() {
        limiter.recordFailure("1.2.3.4", "redaccion@theblastbeast.com");
        limiter.recordFailure("1.2.3.4", "redaccion@theblastbeast.com");
        assertThatThrownBy(() -> limiter.check("1.2.3.4", "redaccion@theblastbeast.com"))
                .isInstanceOf(RateLimitException.class);

        clock.advance(Duration.ofSeconds(61));

        assertThatCode(() -> limiter.check("1.2.3.4", "redaccion@theblastbeast.com"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un login correcto limpia los fallos de la cuenta y la IP")
    void loginCorrectoReiniciaContadores() {
        limiter.recordFailure("1.2.3.4", "redaccion@theblastbeast.com");
        limiter.recordFailure("1.2.3.4", "redaccion@theblastbeast.com");

        limiter.reset("1.2.3.4", "redaccion@theblastbeast.com");

        assertThatCode(() -> limiter.check("1.2.3.4", "redaccion@theblastbeast.com"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("no bloquea antes de alcanzar el límite")
    void noBloqueaAntesDelLimite() {
        limiter.recordFailure("1.2.3.4", "redaccion@theblastbeast.com");

        assertThatCode(() -> limiter.check("1.2.3.4", "redaccion@theblastbeast.com"))
                .doesNotThrowAnyException();
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
