package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

import com.jayway.jsonpath.JsonPath;
import com.novacorp.inmonode.inmonodebackend.TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ReceiveVoucherEvidenceCommand;
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
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl.FieldReservationConsolidationFacade;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl.LotAvailabilityFacade;
import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenService;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.UserStatus;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * T91: two finance analysts deciding the same payment evidence at the same time, against a real PostgreSQL. Every
 * decision locks the lot and then the reservation, so one of them decides and the other finds it decided: the
 * evidence, the reservation and the lot end as the winner said, whoever it was.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class VerificationConcurrencyIntegrationTest {

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
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationCommandService reservationCommandService;

    @Autowired
    private FieldReservationConsolidationFacade fieldFacade;

    @Autowired
    private LotAvailabilityFacade lotAvailabilityFacade;

    @RepeatedTest(3)
    void approvingAndRejectingTheSameEvidenceAtOnceDecidesItOnce() throws Exception {
        var lot = lot("AR-01");
        var reservationId = fieldReservation(lot);
        var evidenceId = sendVoucher(reservationId, "1500");
        var before = Instant.now();

        var answers = atOnce(List.of(() -> approve(evidenceId, 77L), () -> reject(evidenceId, 78L)));

        var approval = answers.get(0);
        var rejection = answers.get(1);
        assertEquals(List.of(200, 409), List.of(approval.getStatus(), rejection.getStatus()).stream().sorted().toList(),
                "one decided it and the other found it decided");
        var loser = approval.getStatus() == 409 ? approval : rejection;
        assertEquals("PAYMENT_EVIDENCE_CONFLICT", JsonPath.read(loser.getContentAsString(), "$.code"));
        var reservation = reservationRepository.findBySourceEventId(reservationId).orElseThrow();
        var evidence = reservation.findEvidenceById(evidenceId).orElseThrow();
        var decided = lotRepository.findById(lot.getId()).orElseThrow();
        if (approval.getStatus() == 200) {
            assertEquals(PaymentEvidenceStatus.APPROVED, evidence.getStatus());
            assertEquals(77L, evidence.getReviewerId());
            assertEquals(ReservationStatus.VERIFIED, reservation.getStatus());
            assertEquals(LotStatus.RESERVED, decided.getStatus());
        } else {
            assertEquals(PaymentEvidenceStatus.REJECTED, evidence.getStatus());
            assertEquals(78L, evidence.getReviewerId());
            assertEquals(ReservationStatus.REJECTED, reservation.getStatus());
            assertEquals(LotStatus.PENDING_VERIFICATION, decided.getStatus(), "the lot waits for another voucher");
            assertFalse(decided.getBlockedUntil().isBefore(before.plus(Duration.ofHours(24))));
        }
        assertEquals(JsonPath.read((approval.getStatus() == 200 ? approval : rejection).getContentAsString(),
                "$.evidenceStatus"), evidence.getStatus().name(), "the winner's answer is what was kept");
    }

    @RepeatedTest(3)
    void twoApprovalsOfTheSameWebEvidenceAtOnceVerifyTheReservationOnce() throws Exception {
        var lot = lot("AA-01");
        var transactionId = webReservation(lot);
        var evidenceId = sendVoucher(transactionId, "9000");

        var answers = atOnce(List.of(() -> approve(evidenceId, 77L), () -> approve(evidenceId, 78L)));

        assertEquals(List.of(200, 409), answers.stream().map(MockHttpServletResponse::getStatus).sorted().toList());
        var winner = answers.get(0).getStatus() == 200 ? 77L : 78L;
        var reservation = reservationRepository.findBySourceEventId(transactionId).orElseThrow();
        var evidence = reservation.findEvidenceById(evidenceId).orElseThrow();
        assertEquals(PaymentEvidenceStatus.APPROVED, evidence.getStatus());
        assertEquals(winner, evidence.getReviewerId(), "the approval kept is the one that answered 200");
        assertEquals(ReservationStatus.VERIFIED, reservation.getStatus());
        assertEquals(LotStatus.RESERVED, lotRepository.findById(lot.getId()).orElseThrow().getStatus());
        var won = answers.get(0).getStatus() == 200 ? answers.get(0) : answers.get(1);
        assertEquals(evidence.getReviewedAt(), Instant.parse(JsonPath.read(won.getContentAsString(), "$.reviewedAt")));
    }

    // ---------------------------------------------------------------- helpers

    private static List<MockHttpServletResponse> atOnce(List<Callable<MockHttpServletResponse>> calls) throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(calls.size())) {
            var futures = calls.stream().map(call -> executor.submit(() -> {
                start.await();
                return call.call();
            })).toList();
            start.countDown();
            var answers = new ArrayList<MockHttpServletResponse>();
            for (var future : futures) {
                answers.add(future.get(60, TimeUnit.SECONDS));
            }
            return answers;
        }
    }

    private MockHttpServletResponse approve(Long evidenceId, long reviewer) throws Exception {
        return mockMvc.perform(post("/api/v1/verifications/{id}/approve", evidenceId)
                .header(HttpHeaders.AUTHORIZATION, bearer(Role.FINANCE_ADMIN, reviewer))).andReturn().getResponse();
    }

    private MockHttpServletResponse reject(Long evidenceId, long reviewer) throws Exception {
        return mockMvc.perform(post("/api/v1/verifications/{id}/reject", evidenceId)
                .header(HttpHeaders.AUTHORIZATION, bearer(Role.FINANCE_ADMIN, reviewer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Voucher ilegible\"}")).andReturn().getResponse();
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

    /** A lot of its own published project. */
    private Lot lot(String code) {
        var rules = new FinancingRules(new BigDecimal("20"), new BigDecimal("12"), 120, BigDecimal.ONE);
        var project = Project.create("Decisiones simultáneas", "Chilca", null, null, rules);
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
