package com.theblastbeast.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // Genéricos
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "GEN-001", "Datos de entrada inválidos"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "GEN-500", "Error interno del servidor"),

    // Artículos
    ARTICLE_NOT_FOUND(HttpStatus.NOT_FOUND, "ART-001", "Artículo no encontrado"),
    ARTICLE_SLUG_ALREADY_EXISTS(HttpStatus.CONFLICT, "ART-002", "Ya existe un artículo con ese slug"),
    ARTICLE_NOT_PUBLISHED(HttpStatus.GONE, "ART-003", "El artículo ya no está disponible"),

    // Bandas
    BAND_NOT_FOUND(HttpStatus.NOT_FOUND, "BAND-001", "Banda no encontrada"),

    // Autenticación
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH-001", "Credenciales incorrectas"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH-002", "La sesión ha caducado"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH-003", "No tienes permisos para esta operación"),

    // Medios
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "MED-001", "Formato de archivo no admitido"),
    MEDIA_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "MED-002", "El archivo supera el tamaño máximo");

    private final HttpStatus status;
    private final String code;
    private final String title;

    ErrorCode(HttpStatus status, String code, String title) {
        this.status = status;
        this.code = code;
        this.title = title;
    }
}
