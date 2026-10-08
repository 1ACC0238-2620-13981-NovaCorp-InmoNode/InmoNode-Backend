package com.novacorp.inmonode.inmonodebackend.iam.infrastructure.authorization.sfs.pipeline;

import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenClaims;
import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenService;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;
import com.novacorp.inmonode.inmonodebackend.iam.infrastructure.authorization.sfs.model.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final TokenService tokenService = mock(TokenService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokenService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validTokenInjectsIdentityAndRoleAuthority() throws Exception {
        when(tokenService.parseToken("good")).thenReturn(Optional.of(new TokenClaims(7L, "ana@mail.com", Role.BUYER)));

        run("Bearer good");

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals(new AuthenticatedUser(7L, "ana@mail.com", Role.BUYER), authentication.getPrincipal());
        assertTrue(authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_BUYER")));
    }

    @Test
    void invalidTokenLeavesContextEmpty() throws Exception {
        when(tokenService.parseToken("bad")).thenReturn(Optional.empty());

        run("Bearer bad");

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void missingOrNonBearerHeaderLeavesContextEmpty() throws Exception {
        run(null);
        run("Basic abc");

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private void run(String authorization) throws Exception {
        var request = new MockHttpServletRequest();
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }
}
