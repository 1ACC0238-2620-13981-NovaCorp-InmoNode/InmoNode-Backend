package com.novacorp.inmonode.inmonodebackend.shared.infrastructure.auditing;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.novacorp.inmonode.inmonodebackend.iam.infrastructure.authorization.sfs.model.AuthenticatedUser;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FinancialAuditFilterTest {
    @Test void actualSeparationAndVerificationRoutesAreAuditedButCatalogReadsAreNot() {
        var filter = new FinancialAuditFilter(true);
        assertFalse(filter.shouldNotFilter(new MockHttpServletRequest("POST", "/api/v1/lots/4/separation-requests")));
        assertFalse(filter.shouldNotFilter(new MockHttpServletRequest("POST", "/api/v1/verifications/8/approve")));
        assertFalse(filter.shouldNotFilter(new MockHttpServletRequest("POST", "/api/v1/verifications/8/reject")));
        assertTrue(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/v1/projects")));
    }

    @Test void writesAMaskedStructuredEntryAndPreservesTheBusinessResponse() throws Exception {
        var logger = (Logger) LoggerFactory.getLogger("inmonode.audit");
        var entries = new ListAppender<ILoggingEvent>();
        entries.start(); logger.addAppender(entries);
        try {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    new AuthenticatedUser(41L, "buyer@example.test", Role.BUYER), null, List.of()));
            var request = new MockHttpServletRequest("POST", "/api/v1/lots/4/separation-requests");
            request.setContent("{\"password\":\"secret-password\",\"token\":\"private-token\",\"quotationId\":3}".getBytes(StandardCharsets.UTF_8));
            var response = new MockHttpServletResponse();
            var body = "{\"status\":\"BLOCKED\",\"documentNumber\":\"12345678\"}";
            new FinancialAuditFilter(true).doFilter(request, response, (req, res) -> {
                req.getInputStream().readAllBytes();
                res.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
            });
            assertEquals(body, response.getContentAsString());
            assertEquals(1, entries.list.size());
            var entry = entries.list.getFirst().getFormattedMessage();
            assertTrue(entry.contains("\"userId\":41"), entry);
            assertTrue(entry.contains("\"quotationId\":3"), entry);
            assertFalse(entry.contains("secret-password"));
            assertFalse(entry.contains("private-token"));
            assertFalse(entry.contains("12345678"));
        } finally {
            logger.detachAppender(entries); entries.stop(); SecurityContextHolder.clearContext();
        }
    }
}
