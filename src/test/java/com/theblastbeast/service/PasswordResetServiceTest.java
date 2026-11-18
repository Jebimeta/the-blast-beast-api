package com.theblastbeast.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.theblastbeast.domain.entity.Role;
import com.theblastbeast.domain.entity.User;
import com.theblastbeast.domain.entity.UserStatus;
import com.theblastbeast.repository.RefreshTokenRepository;
import com.theblastbeast.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordResetService")
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private PasswordResetService passwordResetService;

    private User user;

    @BeforeEach
    void setUp() {
        passwordResetService = new PasswordResetService(
                userRepository, refreshTokenRepository, passwordEncoder);

        user = new User();
        user.setId(9L);
        user.setEmail("redaccion@theblastbeast.com");
        user.setPasswordHash("hash-antiguo");
        user.setDisplayName("Redacción");
        user.setRole(Role.ADMIN);
        user.setStatus(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("actualiza el hash y revoca las sesiones del usuario")
    void actualizaHashYRevocaSesiones() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NuevaClave2026!")).thenReturn("hash-nuevo");
        when(refreshTokenRepository.revokeAllByUserId(user.getId())).thenReturn(3);

        boolean reset = passwordResetService.reset(user.getEmail(), "NuevaClave2026!");

        assertThat(reset).isTrue();
        assertThat(user.getPasswordHash()).isEqualTo("hash-nuevo");
        verify(userRepository).save(user);
        verify(refreshTokenRepository).revokeAllByUserId(user.getId());
    }

    @Test
    @DisplayName("devuelve false si el usuario no existe")
    void usuarioInexistente() {
        when(userRepository.findByEmailIgnoreCase("nadie@theblastbeast.com"))
                .thenReturn(Optional.empty());

        boolean reset = passwordResetService.reset("nadie@theblastbeast.com", "NuevaClave2026!");

        assertThat(reset).isFalse();
        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllByUserId(any());
    }

    @Test
    @DisplayName("rechaza contraseñas que no cumplen la política")
    void rechazaContrasenaCorta() {
        boolean reset = passwordResetService.reset(user.getEmail(), "corta");

        assertThat(reset).isFalse();
        verify(userRepository, never()).findByEmailIgnoreCase(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("rechaza un email vacío")
    void rechazaEmailVacio() {
        boolean reset = passwordResetService.reset("  ", "NuevaClave2026!");

        assertThat(reset).isFalse();
        verify(userRepository, never()).findByEmailIgnoreCase(any());
    }
}
