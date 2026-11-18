package com.theblastbeast.security;

/**
 * Reglas mínimas para las contraseñas nuevas. El login acepta contraseñas
 * antiguas de 8 caracteres, pero cualquier contraseña nueva debe cumplir esto.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 200;

    private PasswordPolicy() {
    }

    public static boolean isAcceptable(String rawPassword) {
        return rawPassword != null
                && rawPassword.length() >= MIN_LENGTH
                && rawPassword.length() <= MAX_LENGTH;
    }
}
