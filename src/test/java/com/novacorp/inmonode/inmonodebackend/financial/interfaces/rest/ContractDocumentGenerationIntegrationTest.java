package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

import com.jayway.jsonpath.JsonPath;
import com.novacorp.inmonode.inmonodebackend.S3TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ReceiveVoucherEvidenceCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FinancingRules;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotBoundary;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotDimensions;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ReservationCommandService;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl.LotAvailabilityFacade;
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
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The back office has the backend generate the contract of a verified web reservation in the background (US-45) and
 * issues it with the usual step (US-21), over HTTP against a real PostgreSQL and S3-compatible storage. Without a
 * broker configured, the worker inside the backend is the queue.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, S3TestcontainersConfiguration.class})
class ContractDocumentGenerationIntegrationTest {

    private static final long ISSUER = 77L;
    private static final long OTHER_ISSUER = 78L;
    private static final long BUYER = 41L;

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private LotRepository lotRepository;

    @Autowired
    private ReservationCommandService reservationCommandService;

    @Autowired
    private LotAvailabilityFacade lotAvailabilityFacade;

    @Test
    void theBackendWritesTheContractInTheBackgroundAndTheBackOfficeIssuesIt() throws Exception {
        var transactionId = verifiedWebReservation(lot("G-01"));

        var answer = requestDocument(transactionId)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.kind").value("CONTRACT"))
                .andExpect(jsonPath("$.reference").value(transactionId.toString()))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.downloadUrl", nullValue()))
                .andReturn().getResponse().getContentAsString();
        var requestId = UUID.fromString(JsonPath.read(answer, "$.requestId"));
        assertEquals("/api/v1/document-requests/" + requestId, JsonPath.read(answer, "$.statusUrl"));

        var ready = awaitReady(requestId, bearer(Role.FINANCE_ADMIN, ISSUER));
        long sizeBytes = ((Number) JsonPath.read(ready, "$.sizeBytes")).longValue();
        var pdf = download(JsonPath.read(ready, "$.downloadUrl"));
        assertEquals(sizeBytes, pdf.length);
        var text = text(pdf);
        assertTrue(text.contains("Contrato preliminar de compraventa"), text);
        assertTrue(text.contains("lote G-01"), text);
        assertTrue(text.contains("S/ 45,000.00"), text);
        assertTrue(text.contains("S/ 9,000.00"), text);
        assertTrue(text.contains("buyer41@mail.com") || text.contains("comprador"), text);
        // US-56: the issued PDF carries the agreement to sign it electronically; wrapped lines are joined again.
        var flowing = text.replaceAll("\\s+", " ");
        assertTrue(flowing.contains("SÉPTIMA: FIRMA ELECTRÓNICA"), text);
        assertTrue(flowing.contains("conforme a la Ley N.° 27269, Ley de Firmas y Certificados Digitales"), text);
        assertTrue(flowing.contains("LA EMPRESA conserva el contrato emitido y la hoja firmada."), text);

        mockMvc.perform(post("/api/v1/reservations/{id}/contract", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.FINANCE_ADMIN, ISSUER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentId\": \"%s\", \"sizeBytes\": %d}".formatted(requestId, sizeBytes)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ISSUED"));

        requestDocument(transactionId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_CONFLICT"));
    }

    @Test
    void anyoneInFinanceReadsAGeneratedContractButNobodyElseLearnsItExists() throws Exception {
        var transactionId = verifiedWebReservation(lot("G-02"));
        var requestId = JsonPath.read(requestDocument(transactionId).andReturn().getResponse().getContentAsString(),
                "$.requestId");

        mockMvc.perform(get("/api/v1/document-requests/{id}", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.FINANCE_ADMIN, OTHER_ISSUER)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/document-requests/{id}", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, BUYER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_REQUEST_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/document-requests/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.FINANCE_ADMIN, ISSUER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyAVerifiedWebReservationGetsItsContractGenerated() throws Exception {
        requestDocument(webReservation(lot("G-03"))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
        requestDocument(UUID.randomUUID()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/reservations/{id}/contract/document-requests", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, BUYER)))
                .andExpect(status().isForbidden());
    }

    private ResultActions requestDocument(UUID transactionId) throws Exception {
        return mockMvc.perform(post("/api/v1/reservations/{id}/contract/document-requests", transactionId)
                .header(HttpHeaders.AUTHORIZATION, bearer(Role.FINANCE_ADMIN, ISSUER)));
    }

    private String awaitReady(UUID requestId, String authorization) throws Exception {
        var deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            var answer = mockMvc.perform(get("/api/v1/document-requests/{id}", requestId)
                            .header(HttpHeaders.AUTHORIZATION, authorization))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            String state = JsonPath.read(answer, "$.status");
            if (!"PROCESSING".equals(state)) {
                assertEquals("READY", state, answer);
                return answer;
            }
            Thread.sleep(100);
        }
        return fail("document request " + requestId + " was still processing after 30 seconds");
    }

    private byte[] download(String url) throws Exception {
        var response = http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, response.statusCode());
        return response.body();
    }

    private static String text(byte[] pdf) throws Exception {
        try (var document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    /** A web separation of 9 000 down whose voucher the back office approved. */
    private UUID verifiedWebReservation(Lot lot) throws Exception {
        var transactionId = webReservation(lot);
        var voucherId = UUID.randomUUID();
        var evidenceId = reservationCommandService.handle(new ReceiveVoucherEvidenceCommand(transactionId, voucherId,
                Money.of(new BigDecimal("9000")), LocalDate.now().minusDays(1), "00123456", false,
                "vouchers/%s/%s.jpg".formatted(transactionId, voucherId), Instant.now())).getId();
        mockMvc.perform(post("/api/v1/verifications/{id}/approve", evidenceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.FINANCE_ADMIN, ISSUER)))
                .andExpect(status().isOk());
        return transactionId;
    }

    private UUID webReservation(Lot lot) {
        var transactionId = UUID.randomUUID();
        assertEquals("BLOCKED", lotAvailabilityFacade.blockLot(transactionId, lot.getId(), BUYER,
                new BigDecimal("9000"), "PEN", 12, new BigDecimal("12"), Instant.now()).result());
        return transactionId;
    }

    /** A lot of its own published project. */
    private Lot lot(String code) {
        var rules = new FinancingRules(new BigDecimal("20"), new BigDecimal("12"), 120, BigDecimal.ONE);
        var project = Project.create("Contratos generados", "Chilca", null, null, rules);
        project.publish(1);
        var projectId = projectRepository.save(project).getId();
        var boundary = LotBoundary.fromPolygonRings(List.of(List.of(
                List.of(0.0, 0.0), List.of(0.001, 0.0), List.of(0.001, 0.001), List.of(0.0, 0.0))));
        return lotRepository.saveAll(List.of(Lot.register(projectId, code,
                new LotDimensions(new BigDecimal("120"), null, null), Money.of(new BigDecimal("45000")), boundary)))
                .getFirst();
    }

    private String bearer(Role role, long userId) {
        var user = User.restore(userId, role.name().toLowerCase() + userId + "@mail.com", "hash", role,
                UserStatus.ACTIVE, null, null, 0, null);
        return "Bearer " + tokenService.generateToken(user);
    }
}
