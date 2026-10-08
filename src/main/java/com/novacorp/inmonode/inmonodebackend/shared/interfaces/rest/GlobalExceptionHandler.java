package com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest.transform.ErrorResponseAssembler;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Global exception handler for REST API.
 * Provides centralized exception handling for the entire application,
 * ensuring all unhandled exceptions are translated to consistent
 * HTTP responses via the shared error assembly pattern.
 */
@RestControllerAdvice
@NullMarked
public class GlobalExceptionHandler {

    private static final String MESSAGES_BASENAME = "messages";
    // Fall back to the base bundle, never to the JVM default locale.
    private static final ResourceBundle.Control NO_FALLBACK_CONTROL =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    private String resolveMessageOrDefault(String key, String defaultValue, Object... args){
        try {
            var bundle = ResourceBundle.getBundle(MESSAGES_BASENAME, LocaleContextHolder.getLocale(), NO_FALLBACK_CONTROL);
            if (!bundle.containsKey(key)) {
                return defaultValue;
            }
            return MessageFormat.format(bundle.getString(key), args);
        } catch (MissingResourceException ex) {
            return  defaultValue;
        }
    }
    /**
     * Handles validation exceptions from Spring's request body validation.
     * Maps validation failure to a standardized error response.
     *
     * @param ex the validation exception from @Valid binding
     * @return error response with BAD_REQUEST status
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        var fieldErrors = ex.getBindingResult().getFieldErrors();
        var validationPrefix = resolveMessageOrDefault("validation.field.prefix", "Field");
        var errorDetails = fieldErrors.isEmpty()
                ? resolveMessageOrDefault("validation.request.failed", "Request validation failed")
                : fieldErrors.stream()
                .map(error -> "%s %s: %s".formatted(
                        validationPrefix,
                        error.getField(),
                        error.getDefaultMessage()
                ))
                .reduce((a, b) -> a+ "; " +b)
                .orElse(resolveMessageOrDefault("validation.request.failed", "Request validation failed"));
        var applicationError = ApplicationError.validationError("request-body", errorDetails);
        return ErrorResponseAssembler.toErrorResponseFromApplicationError(applicationError);
    }

    /**
     * Keeps a body that is not valid JSON, or has a value of the wrong type, as 400 instead of the generic 500 fallback.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        var applicationError = ApplicationError.validationError("request-body",
                resolveMessageOrDefault("validation.request.malformed",
                        "The request body is not valid JSON or has a value of the wrong type"));
        return ErrorResponseAssembler.toErrorResponseFromApplicationError(applicationError);
    }

    /**
     * Keeps a path or query parameter of the wrong type (e.g. a non-numeric id) as 400 instead of 500.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> handleArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        var applicationError = ApplicationError.validationError(ex.getName(),
                resolveMessageOrDefault("validation.request.parameter-type",
                        "Parameter %s has an invalid value".formatted(ex.getName()), ex.getName()));
        return ErrorResponseAssembler.toErrorResponseFromApplicationError(applicationError);
    }

    /**
     * Keeps method-security denials ({@code @PreAuthorize}) as 403 instead of the generic 500 fallback.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDenied(AccessDeniedException ex) {
        return ErrorResponseAssembler.toErrorResponseFromApplicationError(
                new ApplicationError("FORBIDDEN", "The caller is not allowed to perform this operation"));
    }

    /**
     * Keeps authentication failures raised inside controllers as 401 instead of the generic 500 fallback.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<?> handleAuthentication(AuthenticationException ex) {
        return ErrorResponseAssembler.toErrorResponseFromApplicationError(
                new ApplicationError("UNAUTHORIZED", "Authentication is required"));
    }

    /**
     * Handles invalid request arguments such as malformed UUID path or payload values.
     *
     * @param ex the illegal argument exception
     * @return error response with BAD_REQUEST status
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgumentException(IllegalArgumentException ex) {
        var applicationError = ApplicationError.validationError(
                resolveMessageOrDefault("validation.request.argument", "request-argument"),
                ex.getMessage() != null ? ex.getMessage() : resolveMessageOrDefault("validation.request.failed",
                        "Request validation failed")
        );
        return ErrorResponseAssembler.toErrorResponseFromApplicationError(applicationError);
    }

    /**
     * Handles unexpected runtime exceptions not caught by specific handlers.
     * Maps to a generic unexpected error response.
     *
     * @param ex the unhandled runtime exception
     * @return error response with INTERNAL_SERVER_ERROR status
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntimeException(RuntimeException ex) {
        var applicationError = ApplicationError.unexpected(
                resolveMessageOrDefault("error.unexpected.context", "global-exception-handler"),
                ex.getMessage() != null ? ex.getMessage() : "An unexpected error occurred");
        return ErrorResponseAssembler.toErrorResponseFromApplicationError(applicationError);
    }

    /**
     * Handles all other exceptions not matched by specific handlers.
     * Provides a final fallback for any unexpected exception type.
     *
     * @param ex the exception
     * @return error response with INTERNAL_SERVER_ERROR status
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception ex) {
        var applicationError = ApplicationError.unexpected(
                resolveMessageOrDefault("error.unexpected.context", "global-exception-handler"),
                ex.getMessage() != null ? ex.getMessage() : "An unexpected error occurred");
        return ErrorResponseAssembler.toErrorResponseFromApplicationError(applicationError);
    }
}
