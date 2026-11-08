package com.theblastbeast.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.theblastbeast.domain.entity.RefreshToken;
import com.theblastbeast.domain.entity.Role;
import com.theblastbeast.domain.entity.User;
import com.theblastbeast.domain.entity.UserStatus;
import com.theblastbeast.exception.BusinessException;
import com.theblastbeast.exception.ErrorCode;
import com.theblastbeast.properties.AuthProperties;
import com.theblastbeast.repository.RefreshTokenRepository;
import com.theblastbeast.repository.UserRepository;
import com.theblastbeast.security.JwtService;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    private static final AuthProperties PROPERTIES = new AuthProperties(
            "clave-de-pruebas-con-mas-de-32-caracteres",
            Duration.ofMinutes(15),
            Duration.ofDays(30),
            "bb_refresh",
            false,
            null);

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthenticationService authenticationService;

    private User user;

    @BeforeEach
    void setUp() {
        authenticationService = new AuthenticationService(
                userRepository, refreshTokenRepository, passwordEncoder, jwtService, PROPERTIES);

        user = new User();
        user.setId(7L);
        user.setEmail("redaccion@theblastbeast.com");
        user.setDisplayName("Redacción");
        user.setPasswordHash("hash");
        user.setRole(Role.ADMIN);
        user.setStatus(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("login rechaza credenciales incorrectas sin revelar si el usuario existe")
    void loginRechazaCredencialesIncorrectas() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticationService.login(user.getEmail(), "secreta"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("login rechaza cuentas desactivadas")
    void loginRechazaCuentaDesactivada() {
        user.setStatus(UserStatus.DISABLED);
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secreta", user.getPasswordHash())).thenReturn(true);

        assertThatThrownBy(() -> authenticationService.login(user.getEmail(), "secreta"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_DISABLED));
    }

    @Test
    @DisplayName("login emite access token y guarda solo el hash del refresco")
    void loginEmiteTokens() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secreta", user.getPasswordHash())).thenReturn(true);
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(jwtService.accessTokenTtlSeconds()).thenReturn(900L);

        AuthenticationService.AuthSession session =
                authenticationService.login(user.getEmail(), "secreta");

        assertThat(session.accessToken()).isEqualTo("access-token");
        assertThat(session.expiresIn()).isEqualTo(900L);
        assertThat(session.refreshToken()).isNotBlank();

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken stored = captor.getValue();
        assertThat(stored.getTokenHash()).hasSize(64).isNotEqualTo(session.refreshToken());
        assertThat(stored.getExpiresAt()).isAfter(OffsetDateTime.now());
        assertThat(stored.isRevoked()).isFalse();
    }

    @Test
    @DisplayName("refresh detecta reutilización y revoca toda la familia")
    void refreshDetectaReutilizacion() {
        RefreshToken stored = token("hash", true, OffsetDateTime.now().plusDays(1));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authenticationService.refresh("token-reutilizado"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID));

        verify(refreshTokenRepository).revokeAllByUserId(user.getId());
    }

    @Test
    @DisplayName("refresh rechaza tokens caducados")
    void refreshRechazaTokenCaducado() {
        RefreshToken stored = token("hash", false, OffsetDateTime.now().minusMinutes(1));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authenticationService.refresh("token-caducado"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TOKEN_EXPIRED));

        assertThat(stored.isRevoked()).isTrue();
    }

    @Test
    @DisplayName("refresh rota el token y emite uno nuevo")
    void refreshRotaToken() {
        RefreshToken stored = token("hash", false, OffsetDateTime.now().plusDays(1));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(stored));
        when(jwtService.generateAccessToken(user)).thenReturn("nuevo-access");
        when(jwtService.accessTokenTtlSeconds()).thenReturn(900L);

        AuthenticationService.AuthSession session = authenticationService.refresh("token-vigente");

        assertThat(stored.isRevoked()).isTrue();
        assertThat(session.accessToken()).isEqualTo("nuevo-access");
        assertThat(session.refreshToken()).isNotBlank();
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    private RefreshToken token(String hash, boolean revoked, OffsetDateTime expiresAt) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hash);
        token.setRevoked(revoked);
        token.setExpiresAt(expiresAt);
        return token;
    }
}
