package com.novacorp.inmonode.inmonodebackend.iam.infrastructure.authorization.sfs.pipeline;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Writes security failures in the same shape as the shared {@code ErrorResource}.
 */
public final class SecurityErrorHandlers {

    private SecurityErrorHandlers() {}

    /** 401 for missing, invalid or expired tokens. */
    public static AuthenticationEntryPoint unauthorized() {
        return (request, response, ex) -> write(response, HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED", "Authentication is required or the token is invalid or expired");
    }

    /** 403 when the caller is authenticated but the role is not admitted. */
    public static AccessDeniedHandler forbidden() {
        return (request, response, ex) -> write(response, HttpStatus.FORBIDDEN,
                "FORBIDDEN", "The caller is not allowed to perform this operation");
    }

    private static void write(HttpServletResponse response, HttpStatus status, String code, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":\"%s\",\"message\":\"%s\"}".formatted(code, message));
    }
}
