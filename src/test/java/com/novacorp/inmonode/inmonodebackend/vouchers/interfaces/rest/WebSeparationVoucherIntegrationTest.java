package com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.rest;

import com.jayway.jsonpath.JsonPath;
import com.novacorp.inmonode.inmonodebackend.S3TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FinancingRules;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotBoundary;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotDimensions;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ReservationRepository;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.repositories.LotJpaRepository;
import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenService;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.UserStatus;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects.OperationChannel;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.repositories.ReservationOperationRepository;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A buyer's payment voucher from the web portal (US-20), end to end over HTTP against a real PostgreSQL and a real
 * S3-compatible storage: the buyer simulates the financing, separates the lot, uploads the voucher with a presigned
 * URL and registers it; financial receives it as payment evidence, as for a field reservation. Each test uses its own
 * buyers and lot.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, S3TestcontainersConfiguration.class})
class WebSeparationVoucherIntegrationTest {

    private static final AtomicLong BUYERS = new AtomicLong(10_000);
    private static final LocalDate YESTERDAY = LocalDate.now(ZoneId.of("America/Lima")).minusDays(1);

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
    private LotJpaRepository lotJpaRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationOperationRepository operationRepository;

    @Test
    void aBuyerSendsTheVoucherOfTheirSeparationAndItWaitsForVerification() throws Exception {
        var buyer = BUYERS.incrementAndGet();
        var lot = lot();
        var separation = separate(buyer, lot);
        var transactionId = UUID.fromString(JsonPath.read(separation, "$.transactionId"));

        var operation = operationRepository.findByReservationId(transactionId).orElseThrow();
        assertEquals(OperationChannel.WEB, operation.getChannel());
        assertEquals(buyer, operation.getOwnerId());
        assertEquals(lot.getId(), operation.getLotId());
        assertEquals(0, new BigDecimal("9000").compareTo(operation.getInitialAmount()));
        assertEquals(Instant.parse(JsonPath.read(separation, "$.lockExpiresAt")), operation.getEvidenceDueAt());

        var voucherId = UUID.randomUUID();
        sendVoucher(buyer, voucherId, transactionId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SYNCED"))
                .andExpect(jsonPath("$.result").value("RECEIVED"));

        var reservation = reservationRepository.findBySourceEventId(transactionId).orElseThrow();
        assertEquals(ReservationStatus.PENDING_VERIFICATION, reservation.getStatus());
        var evidence = reservation.findEvidence(voucherId).orElseThrow();
        assertFalse(evidence.isLate());
        assertEquals(new Money(new BigDecimal("9000"), "PEN"), evidence.getAmount());
        var waiting = reload(lot);
        assertEquals(LotStatus.PENDING_VERIFICATION, waiting.getStatus());
        assertEquals(reservation.getId(), waiting.getCurrentReservationId());
        assertNull(waiting.getBlockedUntil());
    }

    @Test
    void aFileOverFiveMegabytesOrOfAnotherTypeIsRejected() throws Exception {
        var buyer = BUYERS.incrementAndGet();
        var transactionId = UUID.fromString(JsonPath.read(separate(buyer, lot()), "$.transactionId"));

        requestUrl(buyer, UUID.randomUUID(), transactionId, "image/jpeg", 5 * 1024 * 1024 + 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").value(containsString("sizeBytes")));
        requestUrl(buyer, UUID.randomUUID(), transactionId, "image/gif", 2048)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").value(containsString("contentType")));
    }

    @Test
    void aVoucherAfterTheHourIsKeptAsLateAndTheLotIsReleased() throws Exception {
        var buyer = BUYERS.incrementAndGet();
        var lot = lot();
        var transactionId = UUID.fromString(JsonPath.read(separate(buyer, lot), "$.transactionId"));
        forceBlockExpiry(lot);
        var voucherId = UUID.randomUUID();

        sendVoucher(buyer, voucherId, transactionId).andExpect(status().isCreated());

        var reservation = reservationRepository.findBySourceEventId(transactionId).orElseThrow();
        assertEquals(ReservationStatus.EXPIRED, reservation.getStatus());
        assertTrue(reservation.findEvidence(voucherId).orElseThrow().isLate());
        assertEquals(LotStatus.AVAILABLE, reload(lot).getStatus());
    }

    @Test
    void onlyTheBuyerWhoSeparatedTheLotSendsItsVoucher() throws Exception {
        var buyer = BUYERS.incrementAndGet();
        var otherBuyer = BUYERS.incrementAndGet();
        var transactionId = UUID.fromString(JsonPath.read(separate(buyer, lot()), "$.transactionId"));

        mockMvc.perform(post("/api/v1/vouchers/upload-url")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, otherBuyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(urlBody(UUID.randomUUID(), transactionId, "image/jpeg", 2048)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_OPERATION_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/vouchers/upload-url")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.FIELD_AGENT, 7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(urlBody(UUID.randomUUID(), transactionId, "image/jpeg", 2048)))
                .andExpect(status().isNotFound());
    }

    /** Simulates 9 000 down in 12 months and separates the lot, as the portal does; returns the separation. */
    private String separate(long buyer, Lot lot) throws Exception {
        var quotation = mockMvc.perform(post("/api/v1/lots/{lotId}/quotations", lot.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"initialPayment\": 9000, \"termMonths\": 12}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mockMvc.perform(post("/api/v1/lots/{lotId}/separation-requests", lot.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quotationId\": %d}".formatted(JsonPath.<Integer>read(quotation, "$.id"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    /** Asks for the upload URL, uploads a JPEG with it and registers the voucher typed in the portal. */
    private ResultActions sendVoucher(long buyer, UUID voucherId, UUID transactionId) throws Exception {
        var answer = requestUrl(buyer, voucherId, transactionId, "image/jpeg", 2048)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var upload = http.send(HttpRequest.newBuilder(URI.create(JsonPath.read(answer, "$.uploadUrl")))
                        .header("Content-Type", "image/jpeg")
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(new byte[2048]))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, upload.statusCode(), upload.body());
        return mockMvc.perform(post("/api/v1/vouchers")
                .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, buyer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"voucherId": "%s", "reservationId": "%s", "contentType": "image/jpeg", "sizeBytes": 2048,
                         "amount": 9000, "currency": "PEN", "operationDate": "%s", "operationCode": "TRX-778812",
                         "manuallyCorrected": false}""".formatted(voucherId, transactionId, YESTERDAY)));
    }

    private ResultActions requestUrl(long buyer, UUID voucherId, UUID transactionId, String contentType,
                                     long sizeBytes) throws Exception {
        return mockMvc.perform(post("/api/v1/vouchers/upload-url")
                .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, buyer))
                .contentType(MediaType.APPLICATION_JSON)
                .content(urlBody(voucherId, transactionId, contentType, sizeBytes)));
    }

    private static String urlBody(UUID voucherId, UUID transactionId, String contentType, long sizeBytes) {
        return """
                {"voucherId": "%s", "reservationId": "%s", "contentType": "%s", "sizeBytes": %d}"""
                .formatted(voucherId, transactionId, contentType, sizeBytes);
    }

    private Lot reload(Lot lot) {
        return lotRepository.findByProjectId(lot.getProjectId()).getFirst();
    }

    private void forceBlockExpiry(Lot lot) {
        var entity = lotJpaRepository.findById(lot.getId()).orElseThrow();
        entity.setBlockedUntil(Instant.now().minusSeconds(60));
        lotJpaRepository.save(entity);
    }

    /** A lot of its own published project, with a 20 % minimum down payment, 12 % a year and up to 120 months. */
    private Lot lot() {
        var rules = new FinancingRules(new BigDecimal("20"), new BigDecimal("12"), 120, BigDecimal.ONE);
        var project = Project.create("Comprobantes web", "Chilca", null, null, rules);
        project.publish(1);
        var projectId = projectRepository.save(project).getId();
        var boundary = LotBoundary.fromPolygonRings(List.of(List.of(
                List.of(0.0, 0.0), List.of(0.001, 0.0), List.of(0.001, 0.001), List.of(0.0, 0.0))));
        return lotRepository.saveAll(List.of(Lot.register(projectId, "B-01",
                new LotDimensions(new BigDecimal("120"), null, null), Money.of(new BigDecimal("45000")), boundary)))
                .getFirst();
    }

    private String bearer(Role role, long userId) {
        var user = User.restore(userId, role.name().toLowerCase() + userId + "@mail.com", "hash", role,
                UserStatus.ACTIVE, null, null, 0, null);
        return "Bearer " + tokenService.generateToken(user);
    }
}
