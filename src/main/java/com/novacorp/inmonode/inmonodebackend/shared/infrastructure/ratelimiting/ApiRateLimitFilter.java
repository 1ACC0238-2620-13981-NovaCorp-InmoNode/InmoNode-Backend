package com.novacorp.inmonode.inmonodebackend.shared.infrastructure.ratelimiting;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** US-36. Runs after CORS and before JWT; trusts the connection address, never client-supplied forwarding headers. */
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private final IpRateLimiter limiter;
    private final boolean enabled;

    public ApiRateLimitFilter(IpRateLimiter limiter, boolean enabled) {
        this.limiter = limiter;
        this.enabled = enabled;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || "OPTIONS".equals(request.getMethod()) || !path(request).startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var decision = limiter.consume(request.getRemoteAddr(), category(request));
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"code\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"Too many requests; retry after the indicated delay\",\"details\":null}");
    }

    private static IpRateLimiter.Category category(HttpServletRequest request) {
        var path = path(request);
        if (path.endsWith("/download") || path.endsWith("/download-url") || path.endsWith(".pdf")
                || path.endsWith("/no-debt-certificate")) {
            return IpRateLimiter.Category.PDF;
        }
        if ("GET".equals(request.getMethod()) && (path.equals("/api/v1/projects")
                || path.startsWith("/api/v1/projects/") || path.startsWith("/api/v1/field-sync/"))) {
            return IpRateLimiter.Category.CATALOG;
        }
        return IpRateLimiter.Category.API;
    }

    private static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
