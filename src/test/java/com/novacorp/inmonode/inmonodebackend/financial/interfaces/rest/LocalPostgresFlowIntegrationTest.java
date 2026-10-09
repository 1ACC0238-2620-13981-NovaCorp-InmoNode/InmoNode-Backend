package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

import com.jayway.jsonpath.JsonPath;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ReceiveVoucherEvidenceCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.*;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.*;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ReservationCommandService;
import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenService;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.*;
import com.novacorp.inmonode.inmonodebackend.shared.application.storage.ObjectStorage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.services.ReservationOperationCommandService;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.commands.RecordFieldReservationCommand;
import org.springframework.test.web.servlet.*;
import java.math.BigDecimal;
import java.net.URI;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Opt-in real PostgreSQL validation when Docker is unavailable. S3 is mocked explicitly, not claimed as tested. */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "INMONODE_VALIDATION_DB_URL", matches = "jdbc:postgresql:.*")
class LocalPostgresFlowIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("INMONODE_VALIDATION_DB_URL"));
        properties.add("spring.datasource.username", () -> System.getenv("INMONODE_VALIDATION_DB_USER"));
        properties.add("spring.datasource.password", () -> "");
    }
    @Autowired MockMvc mvc;
    @Autowired TokenService tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReservationCommandService reservations;
    @Autowired ReservationRepository reservationRepository;
    @Autowired LotRepository lots;
    @MockitoBean ObjectStorage storage;
    @MockitoSpyBean ReservationOperationCommandService operations;
    private final long buyerId = 91000L;
    private final String polygon = "[[[-76.7,-12.5],[-76.69,-12.5],[-76.69,-12.49],[-76.7,-12.49],[-76.7,-12.5]]]";

    @BeforeEach void storage() {
        when(storage.describe(anyString())).thenAnswer(call -> Optional.of(new ObjectStorage.StoredObject(3000,
                call.<String>getArgument(0).endsWith(".pdf") ? "application/pdf" : "image/jpeg")));
        when(storage.presignDownload(anyString(), any())).thenReturn(new ObjectStorage.PresignedDownload(
                URI.create("https://storage.example.test/test-only"), Instant.now().plusSeconds(600)));
    }

    @Test void persistedCatalogQuoteCoOwnerContractStatementAndOutboxCompleteTheWebFlow() throws Exception {
        var lot = publishedLot();
        var quote = call(post("/api/v1/lots/{id}/quotations", lot), Role.BUYER,
                "{\"initialPayment\":9000,\"termMonths\":12}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.monthlyInstallment").value(3188.23)).andReturn().getResponse().getContentAsString();
        var quoteId = JsonPath.<Number>read(quote, "$.id").longValue();
        var pdf = call(get("/api/v1/quotations/{id}/download", quoteId), Role.BUYER, null)
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals("%PDF-", new String(Arrays.copyOf(pdf, 5), java.nio.charset.StandardCharsets.US_ASCII));
        var body = "{\"quotationId\":" + quoteId + "}";
        var separated = call(post("/api/v1/lots/{id}/separation-requests", lot), Role.BUYER, body)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var transaction = UUID.fromString(JsonPath.read(separated, "$.transactionId"));
        call(post("/api/v1/lots/{id}/separation-requests", lot), Role.BUYER, body).andExpect(status().isCreated());
        assertEquals(1, count("web-reservation:" + transaction));
        call(post("/api/v1/reservations/{id}/co-owner", transaction), Role.BUYER,
                "{\"fullName\":\"Copropietario de prueba\",\"documentType\":\"DNI\",\"documentNumber\":\"12345678\"}")
                .andExpect(status().isOk());
        var voucher = receive(transaction, Instant.now());
        call(post("/api/v1/verifications/{id}/approve", voucher), Role.FINANCE_ADMIN, null).andExpect(status().isOk());
        var issued = call(post("/api/v1/reservations/{id}/contract", transaction), Role.FINANCE_ADMIN,
                "{\"documentId\":\"" + UUID.randomUUID() + "\",\"sizeBytes\":3000}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.coOwner.documentNumber").value("12345678"))
                .andReturn().getResponse().getContentAsString();
        var contract = JsonPath.<Number>read(issued, "$.id").longValue();
        var statement = call(get("/api/v1/reservations/{id}/account-statement", transaction), Role.BUYER, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.paidAmount").value(0.0))
                .andExpect(jsonPath("$.totalAmount").value(38258.81))
                .andExpect(jsonPath("$.installments[11].amount").value(3188.28))
                .andReturn().getResponse().getContentAsString();
        var statementId = JsonPath.<Number>read(statement, "$.id").longValue();
        call(post("/api/v1/reservations/{id}/co-owner", transaction), Role.BUYER,
                "{\"fullName\":\"Cambio prohibido\",\"documentType\":\"DNI\",\"documentNumber\":\"87654321\"}")
                .andExpect(status().isConflict());
        call(post("/api/v1/contracts/{id}/acknowledgment", contract), Role.BUYER, null).andExpect(status().isOk());
        call(post("/api/v1/contracts/{id}/acknowledgment", contract), Role.BUYER, null).andExpect(status().isOk());
        assertEquals(1, count("contract-acknowledged:" + contract));
        for (int quota = 1; quota <= 12; quota++) {
            call(post("/api/v1/account-statements/{id}/installments/{quota}/payment", statementId, quota),
                    Role.FINANCE_ADMIN, "{\"amount\":" + (quota == 12 ? "3188.28" : "3188.23") + "}")
                    .andExpect(status().isOk());
        }
        call(get("/api/v1/reservations/{id}/account-statement", transaction), Role.BUYER, null)
                .andExpect(jsonPath("$.fullyPaid").value(true)).andExpect(jsonPath("$.balance").value(0.0));
        assertEquals(LotStatus.RESERVED, lots.findById(lot).orElseThrow().getStatus(), "payment does not constitute a verified signature");
    }

    @Test void rejectedEvidenceKeepsTheLotPendingAndTimelyReplacementPreservesHistory() throws Exception {
        var lot = publishedLot();
        var quote = call(post("/api/v1/lots/{id}/quotations", lot), Role.BUYER, "{\"initialPayment\":9000,\"termMonths\":12}")
                .andReturn().getResponse().getContentAsString();
        var separation = call(post("/api/v1/lots/{id}/separation-requests", lot), Role.BUYER,
                "{\"quotationId\":" + JsonPath.<Number>read(quote, "$.id") + "}")
                .andReturn().getResponse().getContentAsString();
        var transaction = UUID.fromString(JsonPath.read(separation, "$.transactionId"));
        var original = receive(transaction, Instant.now());
        call(post("/api/v1/verifications/{id}/reject", original), Role.FINANCE_ADMIN, "{\"reason\":\"Unreadable test voucher\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.reservationStatus").value("REJECTED"))
                .andExpect(jsonPath("$.lotStatus").value("PENDING_VERIFICATION"));
        var rejected = reservationRepository.findBySourceEventId(transaction).orElseThrow();
        assertEquals(rejected.getResubmissionDeadline().plusSeconds(900), lots.findById(lot).orElseThrow().getBlockedUntil());
        receive(transaction, Instant.now());
        var replaced = reservationRepository.findBySourceEventId(transaction).orElseThrow();
        assertEquals(ReservationStatus.PENDING_VERIFICATION, replaced.getStatus());
        assertEquals(2, replaced.getEvidences().size());
        assertNull(lots.findById(lot).orElseThrow().getBlockedUntil());
    }

    @Test void fieldValidationRejectsTheWholeBatchAndTechnicalFailureRollsBackAllContexts() throws Exception {
        var lot = publishedLot();
        var prospect = UUID.randomUUID();
        var reservation = UUID.randomUUID();
        var valid = """
            {"prospects":[{"id":"%s","document":"12345678","fullName":"Test prospect","phone":"987654321"}],
             "reservations":[{"id":"%s","lotId":%d,"prospectId":"%s","initialAmount":1500,"reservedAt":"%s"}]}
            """.formatted(prospect, reservation, lot, prospect, Instant.now());
        call(post("/api/v1/field-sync"), Role.FIELD_AGENT, valid.replace("987654321", "bad-phone"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details").value(org.hamcrest.Matchers.containsString("prospects[0]")));
        call(post("/api/v1/field-sync"), Role.FIELD_AGENT, valid.replace("\"lotId\":" + lot, "\"lotId\":999999999"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details").value(org.hamcrest.Matchers.containsString("reservations[0].lotId")));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM catalog_management.prospects WHERE prospect_id = ?", Integer.class, prospect));
        doThrow(new IllegalStateException("simulated listener failure")).when(operations).handle(any(RecordFieldReservationCommand.class));
        call(post("/api/v1/field-sync"), Role.FIELD_AGENT, valid).andExpect(status().isInternalServerError());
        assertTrue(reservationRepository.findBySourceEventId(reservation).isEmpty());
        assertEquals(LotStatus.AVAILABLE, lots.findById(lot).orElseThrow().getStatus());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM catalog_management.prospects WHERE prospect_id = ?", Integer.class, prospect));
        reset(operations);
        call(post("/api/v1/field-sync"), Role.FIELD_AGENT, valid).andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservations[0].result").value("SYNCED"));
    }

    private long publishedLot() throws Exception {
        var created = call(post("/api/v1/catalog/projects"), Role.CATALOG_ADMIN, """
            {"name":"Local PostgreSQL validation","location":"Lima","stages":["Norte","Sur"],
             "financingRules":{"minDownPaymentPercentage":20,"annualInterestRate":12,"maxTermMonths":120,"lateFeeRate":1.5}}
            """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var project = JsonPath.<Number>read(created, "$.id").longValue();
        var registered = call(post("/api/v1/catalog/projects/{id}/lots", project), Role.CATALOG_ADMIN,
                "{\"code\":\"N-01\",\"stageName\":\"Norte\",\"area\":120,\"price\":45000,\"polygon\":" + polygon + "}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        var lot = JsonPath.<Number>read(registered, "$.id").longValue();
        call(put("/api/v1/catalog/lots/{id}/publish", lot), Role.CATALOG_ADMIN, null).andExpect(status().isUnprocessableContent());
        call(put("/api/v1/catalog/projects/{id}/publish", project), Role.CATALOG_ADMIN, null).andExpect(status().isOk());
        mvc.perform(get("/api/v1/projects/{id}/lots", project)).andExpect(jsonPath("$.features.length()").value(0));
        call(put("/api/v1/catalog/lots/{id}/publish", lot), Role.CATALOG_ADMIN, null).andExpect(status().isOk());
        mvc.perform(get("/api/v1/projects/{id}/lots", project)).andExpect(jsonPath("$.features[0].properties.status").value("AVAILABLE"));
        return lot;
    }
    private Long receive(UUID transaction, Instant submittedAt) {
        return reservations.handle(new ReceiveVoucherEvidenceCommand(transaction, UUID.randomUUID(),
                Money.of(new BigDecimal("9000")), LocalDate.now().minusDays(1), "TEST-OP", false,
                "vouchers/test-only.jpg", submittedAt)).getId();
    }
    private int count(String key) {
        return jdbc.queryForObject("SELECT count(*) FROM financial_document_control.notification_outbox WHERE event_key = ?", Integer.class, key);
    }
    private ResultActions call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, Role role, String body) throws Exception {
        var user = User.restore(role == Role.BUYER ? buyerId : 77L, role.name().toLowerCase() + "@example.test", "hash", role,
                UserStatus.ACTIVE, null, null, 0, null);
        request.header("Authorization", "Bearer " + tokens.generateToken(user));
        if (body != null) request.contentType("application/json").content(body);
        return mvc.perform(request);
    }
}
