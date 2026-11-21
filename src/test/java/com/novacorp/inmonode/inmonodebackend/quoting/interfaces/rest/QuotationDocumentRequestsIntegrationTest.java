package com.novacorp.inmonode.inmonodebackend.quoting.interfaces.rest;

import com.jayway.jsonpath.JsonPath;
import com.novacorp.inmonode.inmonodebackend.S3TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FinancingRules;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotBoundary;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotDimensions;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenService;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.UserStatus;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A buyer has the PDF of their quotation generated in the background (US-45, Scenario 1): the API answers at once
 * "En proceso" and the PDF ends in the file repository, against a real PostgreSQL and S3-compatible storage.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, S3TestcontainersConfiguration.class})
class QuotationDocumentRequestsIntegrationTest {

    private static final AtomicLong BUYERS = new AtomicLong(8_500);

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private LotRepository lotRepository;

    @Test
    void theApiAnswersInProcessAndThePdfOfTheQuotationEndsInTheFileRepository() throws Exception {
        var buyer = BUYERS.incrementAndGet();
        var quotationId = quotation(buyer);

        var answer = mockMvc.perform(post("/api/v1/quotations/{id}/document-requests", quotationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.quotationId").value(quotationId))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andReturn().getResponse().getContentAsString();
        String statusUrl = JsonPath.read(answer, "$.statusUrl");
        assertEquals("/api/v1/document-requests/" + JsonPath.read(answer, "$.requestId"), statusUrl);

        var ready = awaitReady(statusUrl, bearer(buyer));
        assertEquals("QUOTATION", JsonPath.read(ready, "$.kind"));
        assertEquals(String.valueOf(quotationId), JsonPath.read(ready, "$.reference"));
        var pdf = http.send(HttpRequest.newBuilder(URI.create(JsonPath.read(ready, "$.downloadUrl"))).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, pdf.statusCode());
        assertEquals(((Number) JsonPath.read(ready, "$.sizeBytes")).longValue(), pdf.body().length);
        try (var document = Loader.loadPDF(pdf.body())) {
            var text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Cotización de financiamiento"), text);
            assertTrue(text.contains("Cotización: " + quotationId), text);
            assertFalse(document.getCurrentAccessPermission().canModify(), "read-only, like the download");
        }

        mockMvc.perform(get(statusUrl).header(HttpHeaders.AUTHORIZATION, bearer(BUYERS.incrementAndGet())))
                .andExpect(status().isNotFound());
    }

    @Test
    void anotherBuyersQuotationIsNotFound() throws Exception {
        var quotationId = quotation(BUYERS.incrementAndGet());

        mockMvc.perform(post("/api/v1/quotations/{id}/document-requests", quotationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(BUYERS.incrementAndGet())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUOTATION_NOT_FOUND"));
    }

    private String awaitReady(String statusUrl, String authorization) throws Exception {
        var deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            var answer = mockMvc.perform(get(statusUrl).header(HttpHeaders.AUTHORIZATION, authorization))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            String state = JsonPath.read(answer, "$.status");
            if (!"PROCESSING".equals(state)) {
                assertEquals("READY", state, answer);
                return answer;
            }
            Thread.sleep(100);
        }
        return fail(statusUrl + " was still processing after 30 seconds");
    }

    /** A quotation of 9 000 down over 12 months on a lot of its own project. */
    private long quotation(long buyer) throws Exception {
        var answer = mockMvc.perform(post("/api/v1/lots/{lotId}/quotations", lot().getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"initialPayment\": 9000, \"termMonths\": 12}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(answer, "$.id")).longValue();
    }

    private Lot lot() {
        var rules = new FinancingRules(new BigDecimal("20"), new BigDecimal("12"), 120, BigDecimal.ONE);
        var project = Project.create("Cotizaciones en PDF", "Chilca", null, null, rules);
        project.publish(1);
        var projectId = projectRepository.save(project).getId();
        var boundary = LotBoundary.fromPolygonRings(List.of(List.of(
                List.of(0.0, 0.0), List.of(0.001, 0.0), List.of(0.001, 0.001), List.of(0.0, 0.0))));
        return lotRepository.saveAll(List.of(Lot.register(projectId, "P-01",
                new LotDimensions(new BigDecimal("120"), null, null), Money.of(new BigDecimal("45000")), boundary)))
                .getFirst();
    }

    private String bearer(long buyer) {
        var user = User.restore(buyer, "buyer" + buyer + "@mail.com", "hash", Role.BUYER, UserStatus.ACTIVE,
                null, null, 0, null);
        return "Bearer " + tokenService.generateToken(user);
    }
}
