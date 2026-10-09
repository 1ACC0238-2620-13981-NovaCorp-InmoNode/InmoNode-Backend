package com.novacorp.inmonode.inmonodebackend.financial.application.internal.eventhandlers;

import com.jayway.jsonpath.JsonPath;
import com.novacorp.inmonode.inmonodebackend.S3TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ReleaseExpiredLotBlocksCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FinancingRules;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotBoundary;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotDimensions;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PaymentEvidenceSource;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PaymentEvidenceStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ReservationRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ReservationCommandService;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.repositories.LotJpaRepository;
import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenService;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.UserStatus;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.commands.RecordFieldReservationCommand;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.repositories.VoucherRepository;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.services.ReservationOperationCommandService;
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
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Comprobante de pago recibido" reaching financial, end to end over HTTP against a real PostgreSQL and a real
 * S3-compatible storage: the agent synchronizes a reservation, asks for the upload URL, uploads the voucher and
 * registers it; the reservation receives it as payment evidence (2.6.4.1). Each test uses its own agents and lot.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, S3TestcontainersConfiguration.class})
class PaymentVoucherReceivedEventHandlerIntegrationTest {

    private static final AtomicLong AGENTS = new AtomicLong(7_000);
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
    private ReservationCommandService reservationCommandService;

    @Autowired
    private VoucherRepository voucherRepository;

    @Autowired
    private ReservationOperationCommandService operationCommandService;

    @Test
    void aVoucherOnTimeLeavesTheReservationAndItsLotWaitingForVerification() throws Exception {
        var agent = AGENTS.incrementAndGet();
        var lot = publishedLot();
        var reservationId = sync(agent, lot);
        var voucherId = UUID.randomUUID();

        sendVoucher(agent, voucherId, reservationId).andExpect(status().isCreated());

        var reservation = reservationOf(reservationId);
        assertEquals(ReservationStatus.PENDING_VERIFICATION, reservation.getStatus());
        assertEquals(1, reservation.getEvidences().size());
        var evidence = reservation.getEvidences().getFirst();
        assertNotNull(evidence.getId());
        assertEquals(voucherId, evidence.getReference());
        assertEquals(PaymentEvidenceSource.VOUCHER, evidence.getSource());
        assertEquals(new Money(new BigDecimal("1500.50"), "PEN"), evidence.getAmount());
        assertEquals(YESTERDAY, evidence.getOperationDate());
        assertEquals("00123456", evidence.getOperationCode());
        assertTrue(evidence.isManuallyCorrected());
        assertEquals("vouchers/%s/%s.jpg".formatted(reservationId, voucherId), evidence.getObjectKey());
        assertEquals(PaymentEvidenceStatus.PENDING, evidence.getStatus());
        assertFalse(evidence.isLate());
        assertEquals(voucherRepository.findByVoucherId(voucherId).orElseThrow().getReceivedAt(),
                evidence.getSubmittedAt());

        var waiting = lotOf(lot);
        assertEquals(LotStatus.PENDING_VERIFICATION, waiting.getStatus());
        assertEquals(reservation.getId(), waiting.getCurrentReservationId());
        assertNull(waiting.getBlockedUntil(), "the lot waits for the verification with no deadline");
    }

    @Test
    void theReleaseJobNoLongerFreesALotWaitingForVerification() throws Exception {
        var agent = AGENTS.incrementAndGet();
        var lot = publishedLot();
        var reservationId = sync(agent, lot);
        sendVoucher(agent, UUID.randomUUID(), reservationId).andExpect(status().isCreated());

        reservationCommandService.handle(new ReleaseExpiredLotBlocksCommand());

        assertEquals(LotStatus.PENDING_VERIFICATION, lotOf(lot).getStatus());
        assertEquals(ReservationStatus.PENDING_VERIFICATION, reservationOf(reservationId).getStatus());
    }

    @Test
    void thePortfolioShowsTheLotWaitingForVerification() throws Exception {
        var agent = AGENTS.incrementAndGet();
        var lot = publishedLot();
        sendVoucher(agent, UUID.randomUUID(), sync(agent, lot)).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/field-sync/portfolio").header(HttpHeaders.AUTHORIZATION, bearer(agent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projects[?(@.id == %d)].lots.features[?(@.id == %d)].properties.status",
                        lot.getProjectId(), lot.getId()).value("PENDING_VERIFICATION"));
    }

    @Test
    void aVoucherThatArrivesAfterTheBlockRanOutIsKeptAsLateEvidence() throws Exception {
        var agent = AGENTS.incrementAndGet();
        var lot = publishedLot();
        var reservationId = sync(agent, lot);
        var voucherId = UUID.randomUUID();
        forceBlockExpiry(lot);

        sendVoucher(agent, voucherId, reservationId).andExpect(status().isCreated());

        var reservation = reservationOf(reservationId);
        assertEquals(ReservationStatus.EXPIRED, reservation.getStatus(),
                "the block ran out before the voucher, even if the release job had not run yet");
        assertTrue(reservation.findEvidence(voucherId).orElseThrow().isLate());
        var released = lotOf(lot);
        assertEquals(LotStatus.AVAILABLE, released.getStatus());
        assertNull(released.getCurrentReservationId());
    }

    @Test
    void aLateVoucherLeavesTheLotToTheReservationThatTookIt() throws Exception {
        var agent = AGENTS.incrementAndGet();
        var otherAgent = AGENTS.incrementAndGet();
        var lot = publishedLot();
        var reservationId = sync(agent, lot);
        forceBlockExpiry(lot);
        var otherReservationId = sync(otherAgent, lot);
        var taken = lotOf(lot);
        var voucherId = UUID.randomUUID();

        sendVoucher(agent, voucherId, reservationId).andExpect(status().isCreated());

        var reservation = reservationOf(reservationId);
        assertEquals(ReservationStatus.EXPIRED, reservation.getStatus());
        assertTrue(reservation.findEvidence(voucherId).orElseThrow().isLate());
        var unchanged = lotOf(lot);
        assertEquals(LotStatus.BLOCKED, unchanged.getStatus());
        assertEquals(reservationOf(otherReservationId).getId(), unchanged.getCurrentReservationId());
        assertEquals(taken.getBlockedUntil(), unchanged.getBlockedUntil());
        assertEquals(ReservationStatus.BLOCKED, reservationOf(otherReservationId).getStatus());
    }

    @Test
    void theVoucherIsNotRegisteredWhenItsPaymentEvidenceCannotBeStored() throws Exception {
        var agent = AGENTS.incrementAndGet();
        var reservationId = UUID.randomUUID();
        operationCommandService.handle(new RecordFieldReservationCommand(reservationId, agent, publishedLot().getId(),
                new BigDecimal("1500.00"), Instant.now(), Instant.now().plus(Duration.ofHours(24))));
        var voucherId = UUID.randomUUID();

        sendVoucher(agent, voucherId, reservationId).andExpect(status().isInternalServerError());

        assertTrue(voucherRepository.findByVoucherId(voucherId).isEmpty(),
                "with no reservation in financial, the voucher is rolled back with its evidence");
    }

    /** Synchronizes one reservation of the lot as the agent, as the field app does; it must take the lot. */
    private UUID sync(long agent, Lot lot) throws Exception {
        var reservationId = UUID.randomUUID();
        var prospect = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/field-sync")
                        .header(HttpHeaders.AUTHORIZATION, bearer(agent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prospects": [{"id": "%s", "document": "12345678", "fullName": "Ana Quispe"}],
                                 "reservations": [{"id": "%s", "lotId": %d, "prospectId": "%1$s",
                                                   "initialAmount": 1500, "reservedAt": "%s"}]}"""
                                .formatted(prospect, reservationId, lot.getId(), Instant.now().minusSeconds(3600))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservations[0].result").value("SYNCED"));
        return reservationId;
    }

    /** Asks for the upload URL, uploads a JPEG with it and registers the voucher, as the field app does. */
    private ResultActions sendVoucher(long agent, UUID voucherId, UUID reservationId) throws Exception {
        var answer = mockMvc.perform(post("/api/v1/vouchers/upload-url")
                        .header(HttpHeaders.AUTHORIZATION, bearer(agent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"voucherId": "%s", "reservationId": "%s", "contentType": "image/jpeg",
                                 "sizeBytes": 2048}""".formatted(voucherId, reservationId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var upload = http.send(HttpRequest.newBuilder(URI.create(JsonPath.read(answer, "$.uploadUrl")))
                        .header("Content-Type", "image/jpeg")
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(new byte[2048]))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, upload.statusCode(), upload.body());
        return mockMvc.perform(post("/api/v1/vouchers")
                .header(HttpHeaders.AUTHORIZATION, bearer(agent))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"voucherId": "%s", "reservationId": "%s", "contentType": "image/jpeg", "sizeBytes": 2048,
                         "amount": 1500.5, "currency": "PEN", "operationDate": "%s", "operationCode": "00123456",
                         "ocrConfidence": 0.91, "manuallyCorrected": true}"""
                        .formatted(voucherId, reservationId, YESTERDAY)));
    }

    private Reservation reservationOf(UUID reservationId) {
        return reservationRepository.findBySourceEventId(reservationId).orElseThrow();
    }

    private Lot lotOf(Lot lot) {
        return lotRepository.findByProjectId(lot.getProjectId()).getFirst();
    }

    private void forceBlockExpiry(Lot lot) {
        var entity = lotJpaRepository.findById(lot.getId()).orElseThrow();
        entity.setBlockedUntil(Instant.now().minusSeconds(60));
        lotJpaRepository.save(entity);
    }

    /** A lot of its own published project, so it is in the field portfolio. */
    private Lot publishedLot() {
        var rules = new FinancingRules(BigDecimal.TEN, BigDecimal.TEN, 60, BigDecimal.ONE);
        var project = Project.create("Evidencias", "Chilca", null, null, rules);
        project.publish(1);
        var projectId = projectRepository.save(project).getId();
        var boundary = LotBoundary.fromPolygonRings(List.of(List.of(
                List.of(0.0, 0.0), List.of(0.001, 0.0), List.of(0.001, 0.001), List.of(0.0, 0.0))));
        return lotRepository.saveAll(List.of(Lot.register(projectId, "P-01",
                new LotDimensions(new BigDecimal("120"), null, null), Money.of(new BigDecimal("45000")), boundary)))
                .getFirst();
    }

    private String bearer(long agent) {
        var user = User.restore(agent, "agent" + agent + "@mail.com", "hash", Role.FIELD_AGENT, UserStatus.ACTIVE,
                null, null, 0, null);
        return "Bearer " + tokenService.generateToken(user);
    }
}
