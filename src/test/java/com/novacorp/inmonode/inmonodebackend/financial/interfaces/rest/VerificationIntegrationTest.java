package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

import com.jayway.jsonpath.JsonPath;
import com.novacorp.inmonode.inmonodebackend.TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ReceiveVoucherEvidenceCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ReleaseExpiredLotBlocksCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FinancingRules;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotBoundary;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotDimensions;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PaymentEvidenceStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ReservationRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ReservationCommandService;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.repositories.LotJpaRepository;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl.FieldReservationConsolidationFacade;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl.LotAvailabilityFacade;
import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenService;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.UserStatus;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The back office verifies payment evidences (2.6.4, US-25) over HTTP against a real PostgreSQL. Reservations are
 * consolidated and their evidences received through the commands the field sync, the web separation and the voucher
 * listener use; each test uses its own lot.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class VerificationIntegrationTest {

    private static final long REVIEWER = 77L;
    private static final long AGENT = 7L;
    private static final long BUYER = 41L;

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
    private FieldReservationConsolidationFacade fieldFacade;

    @Autowired
    private LotAvailabilityFacade lotAvailabilityFacade;

    @Test
    void theQueueListsThePendingEvidencesWithTheirReservationAndLot() throws Exception {
        var fieldLot = lot("F-01");
        var webLot = lot("W-01");
        var field = fieldReservation(fieldLot);
        var web = webReservation(webLot);
        var fieldEvidence = sendVoucher(field, "1500");
        var webEvidence = sendVoucher(web, "9000");

        var queue = mockMvc.perform(get("/api/v1/verifications/pending").header(HttpHeaders.AUTHORIZATION, finance()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertEquals("FIELD", entry(queue, fieldEvidence, "channel"));
        assertEquals("F-01", entry(queue, fieldEvidence, "lotCode"));
        assertEquals(field.toString(), entry(queue, fieldEvidence, "transactionId"));
        assertEquals("PENDING_VERIFICATION", entry(queue, fieldEvidence, "reservationStatus"));
        assertEquals(1500.0, ((Number) entry(queue, fieldEvidence, "initialAmount")).doubleValue());
        assertEquals(false, entry(queue, fieldEvidence, "late"));
        assertEquals("WEB", entry(queue, webEvidence, "channel"));
        assertEquals((int) BUYER, entry(queue, webEvidence, "requesterId"));
    }

    @Test
    void approvingVerifiesTheReservationAndReservesTheLot() throws Exception {
        var lot = lot("A-01");
        var reservation = fieldReservation(lot);
        var evidenceId = sendVoucher(reservation, "1500");

        approve(evidenceId, "{\"note\": \"Conciliado con el banco\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidenceStatus").value("APPROVED"))
                .andExpect(jsonPath("$.reviewerNote").value("Conciliado con el banco"))
                .andExpect(jsonPath("$.reservationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.lotStatus").value("RESERVED"));

        var verified = reservationRepository.findBySourceEventId(reservation).orElseThrow();
        assertEquals(ReservationStatus.VERIFIED, verified.getStatus());
        assertNotNull(verified.getVerifiedAt());
        var evidence = verified.findEvidenceById(evidenceId).orElseThrow();
        assertEquals(PaymentEvidenceStatus.APPROVED, evidence.getStatus());
        assertEquals(REVIEWER, evidence.getReviewerId());
        assertNotNull(evidence.getReviewedAt());
        assertEquals(LotStatus.RESERVED, reload(lot).getStatus());
        assertFalse(inQueue(evidenceId), "a decided evidence leaves the queue");
        mockMvc.perform(get("/api/v1/projects/{id}/lots", lot.getProjectId()))
                .andExpect(jsonPath("$.features[0].properties.status").value("RESERVED"));
        reservationCommandService.handle(new ReleaseExpiredLotBlocksCommand());
        assertEquals(LotStatus.RESERVED, reload(lot).getStatus(), "the release job never frees a reserved lot");
    }

    @Test
    void rejectionKeepsTheLotPendingFor24HoursAndDeliveryGrace() throws Exception {
        var fieldLot = lot("R-01");
        var webLot = lot("R-02");
        var field = fieldReservation(fieldLot);
        var web = webReservation(webLot);
        var fieldEvidence = sendVoucher(field, "1500");
        var webEvidence = sendVoucher(web, "9000");
        var before = Instant.now();

        reject(fieldEvidence, "Voucher ilegible")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidenceStatus").value("REJECTED"))
                .andExpect(jsonPath("$.reviewerNote").value("Voucher ilegible"))
                .andExpect(jsonPath("$.reservationStatus").value("REJECTED"))
                .andExpect(jsonPath("$.lotStatus").value("PENDING_VERIFICATION"));
        reject(webEvidence, "Cuenta de destino incorrecta").andExpect(status().isOk());

        assertHeldFor(fieldLot, before, Duration.ofHours(24).plusMinutes(15));
        assertHeldFor(webLot, before, Duration.ofHours(24).plusMinutes(15));
        reservationCommandService.handle(new ReleaseExpiredLotBlocksCommand());
        assertEquals(LotStatus.PENDING_VERIFICATION, reload(fieldLot).getStatus(), "the new window has not run out");

        var substitute = sendVoucher(field, "1500");
        assertEquals(ReservationStatus.PENDING_VERIFICATION,
                reservationRepository.findBySourceEventId(field).orElseThrow().getStatus());
        assertEquals(LotStatus.PENDING_VERIFICATION, reload(fieldLot).getStatus());
        assertTrue(inQueue(substitute));
    }

    @Test
    void anEvidenceUnderTheDownPaymentCannotBeApprovedButCanBeRejected() throws Exception {
        var lot = lot("U-01");
        var evidenceId = sendVoucher(fieldReservation(lot), "1000");

        approve(evidenceId, null)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.details").value(containsString("1500.00")));
        assertTrue(inQueue(evidenceId), "it is still pending");
        reject(evidenceId, "Monto menor a la separación").andExpect(status().isOk());
    }

    @Test
    void aLateEvidenceCanOnlyBeRejectedAndLeavesTheReservationAsItIs() throws Exception {
        var lot = lot("L-01");
        var reservation = fieldReservation(lot);
        forceBlockExpiry(lot);
        var evidenceId = sendVoucher(reservation, "1500");

        approve(evidenceId, null)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.details").value(containsString("reject")));
        reject(evidenceId, "Llegó después del plazo")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationStatus").value("EXPIRED"))
                .andExpect(jsonPath("$.lotStatus").value("AVAILABLE"));
    }

    @Test
    void aDecisionIsFinal() throws Exception {
        var evidenceId = sendVoucher(fieldReservation(lot("D-01")), "1500");
        approve(evidenceId, null).andExpect(status().isOk());

        approve(evidenceId, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_EVIDENCE_CONFLICT"));
        reject(evidenceId, "Tarde").andExpect(status().isConflict());
        approve(999_999L, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_EVIDENCE_NOT_FOUND"));
    }

    @Test
    void onlyTheFinanceBackOfficeDecidesAndARejectionNeedsItsReason() throws Exception {
        var evidenceId = sendVoucher(fieldReservation(lot("P-01")), "1500");

        mockMvc.perform(get("/api/v1/verifications/pending").header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, 1L)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/verifications/{id}/approve", evidenceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.CATALOG_ADMIN, 1L)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/verifications/{id}/approve", evidenceId))
                .andExpect(status().isUnauthorized());
        reject(evidenceId, " ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").value(containsString("reason")));
        assertTrue(inQueue(evidenceId));
    }

    private ResultActions approve(Long evidenceId, String body) throws Exception {
        var request = post("/api/v1/verifications/{id}/approve", evidenceId)
                .header(HttpHeaders.AUTHORIZATION, finance());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private ResultActions reject(Long evidenceId, String reason) throws Exception {
        return mockMvc.perform(post("/api/v1/verifications/{id}/reject", evidenceId)
                .header(HttpHeaders.AUTHORIZATION, finance())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"%s\"}".formatted(reason)));
    }

    private boolean inQueue(Long evidenceId) throws Exception {
        var queue = mockMvc.perform(get("/api/v1/verifications/pending").header(HttpHeaders.AUTHORIZATION, finance()))
                .andReturn().getResponse().getContentAsString();
        return !JsonPath.<List<Object>>read(queue, "$[?(@.evidenceId == %d)]".formatted(evidenceId)).isEmpty();
    }

    private static Object entry(String queue, Long evidenceId, String field) {
        List<Object> values = JsonPath.read(queue, "$[?(@.evidenceId == %d)].%s".formatted(evidenceId, field));
        assertEquals(1, values.size(), "evidence %d in the queue".formatted(evidenceId));
        return values.getFirst();
    }

    private void assertHeldFor(Lot lot, Instant before, Duration window) {
        var held = reload(lot);
        assertEquals(LotStatus.PENDING_VERIFICATION, held.getStatus());
        var until = held.getBlockedUntil();
        assertNotNull(until);
        assertFalse(until.isBefore(before.plus(window)));
        assertTrue(until.isBefore(Instant.now().plus(window).plusSeconds(1)));
    }

    /** A field reservation of 1 500 down that took the lot, as the field sync leaves it. */
    private UUID fieldReservation(Lot lot) {
        var reservationId = UUID.randomUUID();
        var outcome = fieldFacade.consolidate(reservationId, lot.getId(), AGENT, UUID.randomUUID(),
                new BigDecimal("1500"), Instant.now().minusSeconds(600));
        assertEquals("SYNCED", outcome.result());
        return reservationId;
    }

    /** A web separation of 9 000 down that blocked the lot, as the quoting context leaves it. */
    private UUID webReservation(Lot lot) {
        var transactionId = UUID.randomUUID();
        var block = lotAvailabilityFacade.blockLot(transactionId, lot.getId(), BUYER, new BigDecimal("9000"), "PEN",
                12, new BigDecimal("12"), Instant.now());
        assertEquals("BLOCKED", block.result());
        return transactionId;
    }

    /** A voucher received for the reservation, as the vouchers context hands it over; returns the evidence id. */
    private Long sendVoucher(UUID reservationId, String amount) {
        var voucherId = UUID.randomUUID();
        var evidence = reservationCommandService.handle(new ReceiveVoucherEvidenceCommand(reservationId, voucherId,
                Money.of(new BigDecimal(amount)), LocalDate.now().minusDays(1), "00123456", false,
                "vouchers/%s/%s.jpg".formatted(reservationId, voucherId), Instant.now()));
        return evidence.getId();
    }

    private Lot reload(Lot lot) {
        return lotRepository.findById(lot.getId()).orElseThrow();
    }

    private void forceBlockExpiry(Lot lot) {
        var entity = lotJpaRepository.findById(lot.getId()).orElseThrow();
        entity.setBlockedUntil(Instant.now().minusSeconds(60));
        lotJpaRepository.save(entity);
    }

    /** A lot of its own published project. */
    private Lot lot(String code) {
        var rules = new FinancingRules(new BigDecimal("20"), new BigDecimal("12"), 120, BigDecimal.ONE);
        var project = Project.create("Verificaciones", "Chilca", null, null, rules);
        project.publish(1);
        var projectId = projectRepository.save(project).getId();
        var boundary = LotBoundary.fromPolygonRings(List.of(List.of(
                List.of(0.0, 0.0), List.of(0.001, 0.0), List.of(0.001, 0.001), List.of(0.0, 0.0))));
        return lotRepository.saveAll(List.of(Lot.register(projectId, code,
                new LotDimensions(new BigDecimal("120"), null, null), Money.of(new BigDecimal("45000")), boundary)))
                .getFirst();
    }

    private String finance() {
        return bearer(Role.FINANCE_ADMIN, REVIEWER);
    }

    private String bearer(Role role, long userId) {
        var user = User.restore(userId, role.name().toLowerCase() + userId + "@mail.com", "hash", role,
                UserStatus.ACTIVE, null, null, 0, null);
        return "Bearer " + tokenService.generateToken(user);
    }
}
