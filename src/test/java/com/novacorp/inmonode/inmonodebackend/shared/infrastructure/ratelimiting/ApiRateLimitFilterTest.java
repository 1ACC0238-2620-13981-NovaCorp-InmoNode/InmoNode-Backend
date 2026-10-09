package com.novacorp.inmonode.inmonodebackend.shared.infrastructure.ratelimiting;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import jakarta.servlet.FilterChain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiRateLimitFilterTest {
    private final IpRateLimiter limiter = new IpRateLimiter(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), 2, 1, 1, 100);
    private final ApiRateLimitFilter filter = new ApiRateLimitFilter(limiter, true);
    private final FilterChain chain = mock(FilterChain.class);

    private MockHttpServletResponse call(String method, String path, String forwarded) throws Exception {
        var request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("192.0.2.1");
        if (forwarded != null) request.addHeader("X-Forwarded-For", forwarded);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void pdfHasALowerLimitAndDenialsNeverReachTheController() throws Exception {
        assertEquals(200, call("GET", "/api/v1/quotations/1/download", null).getStatus());
        var denied = call("GET", "/api/v1/quotations/2/download", null);
        assertEquals(429, denied.getStatus());
        assertEquals("60", denied.getHeader("Retry-After"));
        assertEquals("no-store", denied.getHeader("Cache-Control"));
        assertTrue(denied.getContentAsString().contains("RATE_LIMIT_EXCEEDED"));
        assertEquals(429, call("GET", "/api/v1/projects", null).getStatus());
        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    void changingUntrustedForwardedHeadersDoesNotResetTheIp() throws Exception {
        assertEquals(200, call("POST", "/api/v1/auth/sign-in", "10.0.0.1").getStatus());
        assertEquals(429, call("POST", "/api/v1/auth/sign-in", "10.0.0.2").getStatus());
    }

    @Test
    void healthSwaggerAndPreflightAreExemptEvenWhileTheIpIsFrozen() throws Exception {
        call("POST", "/api/v1/private", null);
        call("POST", "/api/v1/private", null);
        assertEquals(200, call("GET", "/health", null).getStatus());
        assertEquals(200, call("GET", "/v3/api-docs", null).getStatus());
        assertEquals(200, call("OPTIONS", "/api/v1/private", null).getStatus());
    }

    @Test
    void contextPathDoesNotBypassTheLimiter() throws Exception {
        var request = new MockHttpServletRequest("POST", "/backend/api/v1/private");
        request.setContextPath("/backend");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        assertEquals(429, response.getStatus());
    }
}
