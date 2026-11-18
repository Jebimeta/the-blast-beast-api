package com.theblastbeast.exception;

import lombok.Getter;

/**
 * Error de negocio que además indica cuántos segundos debe esperar el cliente.
 * El {@link GlobalExceptionHandler} lo traduce a un {@code 429} con
 * {@code Retry-After}.
 */
@Getter
public class RateLimitException extends BusinessException {

    private final long retryAfterSeconds;

    public RateLimitException(ErrorCode errorCode, long retryAfterSeconds) {
        super(errorCode);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }
}
