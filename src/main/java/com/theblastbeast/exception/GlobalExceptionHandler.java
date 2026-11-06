package com.theblastbeast.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String ERROR_TYPE_BASE = "https://theblastbeast.com/errors/";

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusinessException(BusinessException exception, HttpServletRequest request) {
        ErrorCode errorCode = exception.getErrorCode();
        log.warn("INIT - GlobalExceptionHandler -> handleBusinessException() - {} {} -> {}",
                request.getMethod(), request.getRequestURI(), errorCode.getCode());
        return buildProblem(errorCode, exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<Map<String, String>> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> Map.of(
                        "field", fieldError.getField(),
                        "message", String.valueOf(fieldError.getDefaultMessage())))
                .toList();

        ProblemDetail problem = buildProblem(ErrorCode.VALIDATION_ERROR, "Revisa los campos indicados", request);
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException exception,
            HttpServletRequest request) {
        return buildProblem(ErrorCode.VALIDATION_ERROR, exception.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedException(Exception exception, HttpServletRequest request) {
        log.error("INIT - GlobalExceptionHandler -> handleUnexpectedException() - {} {}",
                request.getMethod(), request.getRequestURI(), exception);
        return buildProblem(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getTitle(), request);
    }

    private ProblemDetail buildProblem(ErrorCode errorCode, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.getStatus(), detail);
        problem.setTitle(errorCode.getTitle());
        problem.setType(URI.create(ERROR_TYPE_BASE + errorCode.getCode().toLowerCase(Locale.ROOT)));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", errorCode.getCode());
        if (errorCode.getStatus() == HttpStatus.INTERNAL_SERVER_ERROR) {
            problem.setDetail(ErrorCode.INTERNAL_ERROR.getTitle());
        }
        return problem;
    }
}
