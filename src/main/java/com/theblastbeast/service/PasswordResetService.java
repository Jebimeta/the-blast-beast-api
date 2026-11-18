package com.theblastbeast.service;

import com.theblastbeast.domain.entity.User;
import com.theblastbeast.repository.RefreshTokenRepository;
import com.theblastbeast.repository.UserRepository;
import com.theblastbeast.security.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Restablecimiento de contraseña sin correo, para cuando el administrador no
 * puede entrar al panel. Se usa desde el perfil {@code reset-password} y el
 * script {@code ops/reset-admin-password.sh}; nunca se expone por HTTP.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public boolean reset(String email, String rawPassword) {
        if (email == null || email.isBlank()) {
            log.error("INIT - PasswordResetService -> reset() - falta el email");
            return false;
        }
        if (!PasswordPolicy.isAcceptable(rawPassword)) {
            log.error("INIT - PasswordResetService -> reset() - la contraseña no cumple la política");
            return false;
        }

        return userRepository.findByEmailIgnoreCase(email.trim())
                .map(user -> resetUser(user, rawPassword))
                .orElseGet(() -> {
                    log.error("INIT - PasswordResetService -> reset() - no existe el usuario {}",
                            email.trim());
                    return false;
                });
    }

    private boolean resetUser(User user, String rawPassword) {
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        userRepository.save(user);
        int revoked = refreshTokenRepository.revokeAllByUserId(user.getId());
        log.info("END - PasswordResetService -> reset() - usuario={}, sesionesRevocadas={}",
                user.getEmail(), revoked);
        return true;
    }
}
