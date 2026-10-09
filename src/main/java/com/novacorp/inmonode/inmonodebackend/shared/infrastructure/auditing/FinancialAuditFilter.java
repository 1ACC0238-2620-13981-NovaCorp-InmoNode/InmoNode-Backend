package com.novacorp.inmonode.inmonodebackend.shared.infrastructure.auditing;

import com.novacorp.inmonode.inmonodebackend.iam.infrastructure.authorization.sfs.model.AuthenticatedUser;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.*;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;

public class FinancialAuditFilter extends OncePerRequestFilter {
    private static final Logger AUDIT = LoggerFactory.getLogger("inmonode.audit");
    private final boolean enabled;
    public FinancialAuditFilter(boolean enabled) { this.enabled = enabled; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled || !("POST".equals(request.getMethod()) || "PUT".equals(request.getMethod())
                || "PATCH".equals(request.getMethod()))) return true;
        var path = request.getRequestURI();
        return !(path.startsWith("/api/v1/reservations") || path.startsWith("/api/v1/contracts")
                || path.startsWith("/api/v1/account-statements") || path.startsWith("/api/v1/separation-requests")
                || path.startsWith("/api/v1/vouchers") || path.startsWith("/api/v1/field-sync")
                || path.startsWith("/api/v1/payment-evidences") || path.startsWith("/api/v1/verifications")
                || path.matches("/api/v1/lots/[^/]+/separation-requests"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var cachedRequest = new ContentCachingRequestWrapper(request, 16384);
        var cachedResponse = new ContentCachingResponseWrapper(response);
        try { chain.doFilter(cachedRequest, cachedResponse); }
        finally {
            var entry = new LinkedHashMap<String, Object>();
            entry.put("eventId", java.util.UUID.randomUUID().toString());
            entry.put("occurredAt", Instant.now().toString());
            entry.put("method", request.getMethod());
            entry.put("path", request.getRequestURI());
            entry.put("ip", request.getRemoteAddr());
            var auth = SecurityContextHolder.getContext().getAuthentication();
            entry.put("userId", auth != null && auth.getPrincipal() instanceof AuthenticatedUser user ? user.userId() : null);
            entry.put("status", cachedResponse.getStatus());
            entry.put("request", AuditJsonRedactor.redact(cachedRequest.getContentAsByteArray()));
            entry.put("response", AuditJsonRedactor.redact(cachedResponse.getContentAsByteArray()));
            cachedResponse.copyBodyToResponse();
            AUDIT.info(AuditJsonRedactor.serialize(entry));
        }
    }
}
