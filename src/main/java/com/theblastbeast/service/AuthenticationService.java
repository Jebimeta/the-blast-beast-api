package com.theblastbeast.service;

import com.theblastbeast.domain.entity.RefreshToken;
import com.theblastbeast.domain.entity.User;
import com.theblastbeast.domain.vo.AdminRole;
import com.theblastbeast.domain.vo.AuthUser;
import com.theblastbeast.exception.BusinessException;
import com.theblastbeast.exception.ErrorCode;
import com.theblastbeast.properties.AuthProperties;
import com.theblastbeast.repository.RefreshTokenRepository;
import com.theblastbeast.repository.UserRepository;
import com.theblastbeast.security.JwtService;
import com.theblastbeast.security.UserPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private static final int REFRESH_TOKEN_BYTES = 48;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthProperties authProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public AuthSession login(String email, String password) {
        log.debug("INIT - AuthenticationService -> login() - email={}", email);
        User user = userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }

        AuthSession session = issue(user);
        log.info("END - AuthenticationService -> login() - user={}", user.getId());
        return session;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public AuthSession refresh(String rawToken) {
        log.debug("INIT - AuthenticationService -> refresh()");
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));

        if (stored.isRevoked()) {
            // Un token ya rotado vuelve a usarse: se invalida toda la familia.
            refreshTokenRepository.revokeAllByUserId(stored.getUser().getId());
            log.warn("INIT - AuthenticationService -> refresh() - reutilización de token, user={}",
                    stored.getUser().getId());
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        if (!stored.isUsable(OffsetDateTime.now())) {
            stored.setRevoked(true);
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }
        if (!stored.getUser().isActive()) {
            stored.setRevoked(true);
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }

        stored.setRevoked(true);
        AuthSession session = issue(stored.getUser());
        log.debug("END - AuthenticationService -> refresh() - user={}", stored.getUser().getId());
        return session;
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            token.setRevoked(true);
            log.debug("END - AuthenticationService -> logout() - user={}", token.getUser().getId());
        });
    }

    @Transactional(readOnly = true)
    public AuthUser currentUser(UserPrincipal principal) {
        User user = userRepository.findById(principal.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REQUIRED));
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }
        return toAuthUser(user);
    }

    private AuthSession issue(User user) {
        String rawToken = newToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hash(rawToken));
        refreshToken.setExpiresAt(OffsetDateTime.now().plus(authProperties.refreshTokenTtl()));
        refreshTokenRepository.save(refreshToken);

        String accessToken = jwtService.generateAccessToken(user);
        return new AuthSession(accessToken, jwtService.accessTokenTtlSeconds(), rawToken, user);
    }

    private String newToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no disponible", exception);
        }
    }

    private AuthUser toAuthUser(User user) {
        AuthUser response = new AuthUser();
        response.setId(user.getId());
        response.setEmail(user.getEmail());
        response.setDisplayName(user.getDisplayName());
        response.setRole(AdminRole.valueOf(user.getRole().name()));
        return response;
    }

    public record AuthSession(String accessToken, long expiresIn, String refreshToken, User user) {
    }
}
