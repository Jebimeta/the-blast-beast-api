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
    ARTICLE_REVISION_NOT_FOUND(HttpStatus.NOT_FOUND, "ART-004", "Revisión no encontrada"),
    ARTICLE_NOT_READY(HttpStatus.BAD_REQUEST, "ART-005", "El artículo no está listo para publicarse"),

    // Bandas
    BAND_NOT_FOUND(HttpStatus.NOT_FOUND, "BAND-001", "Banda no encontrada"),
    BAND_SLUG_ALREADY_EXISTS(HttpStatus.CONFLICT, "BAND-002", "Ya existe una banda con ese slug"),
    BAND_IN_USE(HttpStatus.CONFLICT, "BAND-003", "La banda tiene artículos asociados"),

    // Categorías
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "CAT-001", "Categoría no encontrada"),
    CATEGORY_SLUG_ALREADY_EXISTS(HttpStatus.CONFLICT, "CAT-002", "Ya existe una categoría con ese slug"),
    CATEGORY_IN_USE(HttpStatus.CONFLICT, "CAT-003", "La categoría tiene artículos asociados"),

    // Etiquetas
    TAG_NOT_FOUND(HttpStatus.NOT_FOUND, "TAG-001", "Etiqueta no encontrada"),
    TAG_SLUG_ALREADY_EXISTS(HttpStatus.CONFLICT, "TAG-002", "Ya existe una etiqueta con ese slug"),

    // Autenticación
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH-001", "Credenciales incorrectas"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH-002", "La sesión ha caducado"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH-003", "No tienes permisos para esta operación"),
    AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "AUTH-004", "Debes iniciar sesión"),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH-005", "La sesión ya no es válida"),
    USER_DISABLED(HttpStatus.FORBIDDEN, "AUTH-006", "La cuenta está desactivada"),

    // Medios
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "MED-001", "Formato de archivo no admitido"),
    MEDIA_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "MED-002", "El archivo supera el tamaño máximo"),
    MEDIA_NOT_FOUND(HttpStatus.NOT_FOUND, "MED-003", "Medio no encontrado"),
    MEDIA_IN_USE(HttpStatus.CONFLICT, "MED-004", "El medio se usa como portada de un artículo"),
    MEDIA_PROCESSING_ERROR(HttpStatus.BAD_REQUEST, "MED-005", "No se pudo procesar la imagen");

    private final HttpStatus status;
    private final String code;
    private final String title;

    ErrorCode(HttpStatus status, String code, String title) {
        this.status = status;
        this.code = code;
        this.title = title;
    }
}
