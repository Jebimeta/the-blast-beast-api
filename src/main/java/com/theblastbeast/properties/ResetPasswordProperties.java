package com.theblastbeast.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credenciales para el perfil {@code reset-password}. Solo se leen cuando ese
 * perfil está activo y el proceso se ejecuta como tarea de mantenimiento.
 */
@ConfigurationProperties(prefix = "blastbeast.reset-password")
public record ResetPasswordProperties(String email, String password) {

    public boolean isEnabled() {
        return email != null && !email.isBlank()
                && password != null && !password.isBlank();
    }
}
