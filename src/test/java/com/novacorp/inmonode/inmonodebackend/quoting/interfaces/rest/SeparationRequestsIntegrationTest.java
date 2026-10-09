package com.novacorp.inmonode.inmonodebackend.quoting.interfaces.rest;

import com.jayway.jsonpath.JsonPath;
import com.novacorp.inmonode.inmonodebackend.TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FinancingRules;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotBoundary;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotDimensions;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationChannel;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ReservationRepository;
import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenService;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.UserStatus;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.SeparationStatus;
import com.novacorp.inmonode.inmonodebackend.quoting.infrastructure.persistence.jpa.repositories.QuotationJpaRepository;
import com.novacorp.inmonode.inmonodebackend.quoting.infrastructure.persistence.jpa.repositories.SeparationRequestJpaRepository;
import com.novacorp.inmonode.inmonodebackend.quoting.interfaces.events.SeparationRequestRegisteredEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web separation requests (US-19) over HTTP against a real PostgreSQL: a buyer simulates the financing of a lot and
 * asks to separate it; financial blocks the lot for an hour and "Solicitud de separación registrada" is announced.
 * Each test uses its own buyers and lot.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class SeparationRequestsIntegrationTest {

    private static final AtomicLong BUYERS = new AtomicLong(9_000);

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
    private QuotationJpaRepository quotationJpaRepository;

    @Autowired
    private SeparationRequestJpaRepository separationRequestJpaRepository;

    @Autowired
    private ApplicationEvents events;

    @Test
    void aBuyerSeparatesTheLotForAnHourWithTheDownPaymentOfTheQuotation() throws Exception {
        var buyer = BUYERS.incrementAndGet();
        var lot = lot();
        var quotation = quote(buyer, lot);
        var before = Instant.now();

        var answer = separate(buyer, lot.getId(), quotation)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BLOCKED"))
                .andExpect(jsonPath("$.lotId").value(lot.getId()))
                .andExpect(jsonPath("$.quotationId").value(quotation))
                .andExpect(jsonPath("$.initialAmount").value(9000.0))
                .andExpect(jsonPath("$.currency").value("PEN"))
                .andReturn().getResponse().getContentAsString();

        var transactionId = UUID.fromString(JsonPath.read(answer, "$.transactionId"));
        var requestedAt = Instant.parse(JsonPath.read(answer, "$.requestedAt"));
        var lockExpiresAt = Instant.parse(JsonPath.read(answer, "$.lockExpiresAt"));
        assertFalse(requestedAt.isBefore(before.minusSeconds(1)));
        assertFalse(lockExpiresAt.isBefore(requestedAt.plus(Duration.ofHours(1))), "held for one hour");
        assertTrue(lockExpiresAt.isBefore(requestedAt.plus(Duration.ofHours(1)).plusSeconds(5)));

        var reservation = reservationRepository.findBySourceEventId(transactionId).orElseThrow();
        assertEquals(ReservationChannel.WEB, reservation.getChannel());
        assertEquals(ReservationStatus.BLOCKED, reservation.getStatus());
        assertEquals(buyer, reservation.getRequesterId());
        assertEquals(new Money(new BigDecimal("9000"), "PEN"), reservation.getInitialAmount());
        var plan = reservation.getFinancingPlan();
        assertNotNull(plan, "financial keeps the quotation terms");
        assertEquals(12, plan.termMonths());
        assertEquals(new BigDecimal("12.0000"), plan.annualInterestRate());
        assertEquals(new Money(new BigDecimal("45000"), "PEN"), plan.lotPrice());
        var blocked = reload(lot);
        assertEquals(LotStatus.BLOCKED, blocked.getStatus());
        assertEquals(reservation.getId(), blocked.getCurrentReservationId());
        assertEquals(lockExpiresAt, blocked.getBlockedUntil());

        var announced = events.stream(SeparationRequestRegisteredEvent.class).toList();
        assertEquals(1, announced.size());
        var event = announced.getFirst();
        assertEquals(transactionId, event.transactionId());
        assertEquals(lot.getId(), event.lotId());
        assertEquals(buyer, event.buyerId());
        assertEquals(new BigDecimal("9000.00"), event.initialAmount());
        assertEquals("PEN", event.currency());
        assertEquals(requestedAt, event.requestedAt());
        assertEquals(lockExpiresAt, event.lockExpiresAt());
    }

    @Test
    void aBuyerWhoAlreadyHoldsTheLotGetsTheSameRequestBack() throws Exception {
        var buyer = BUYERS.incrementAndGet();
        var lot = lot();
        var quotation = quote(buyer, lot);
        var first = separate(buyer, lot.getId(), quotation).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        var again = separate(buyer, lot.getId(), quotation).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertEquals(first, again);
        assertEquals(1, events.stream(SeparationRequestRegisteredEvent.class).count());
    }

    @Test
    void aLaterBuyerIsRejectedAndTheAttemptIsKept() throws Exception {
        var buyer = BUYERS.incrementAndGet();
        var laterBuyer = BUYERS.incrementAndGet();
        var lot = lot();
        var quotation = quote(buyer, lot);
        var laterQuotation = quote(laterBuyer, lot);
        separate(buyer, lot.getId(), quotation).andExpect(status().isCreated());

        separate(laterBuyer, lot.getId(), laterQuotation)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOT_CONFLICT"));

        var rejected = separationRequestJpaRepository.findAll().stream()
                .filter(request -> request.getBuyerId().equals(laterBuyer))
                .toList();
        assertEquals(1, rejected.size());
        assertEquals(SeparationStatus.REJECTED_UNAVAILABLE, rejected.getFirst().getStatus());
        assertEquals("LOT_UNAVAILABLE", rejected.getFirst().getRejectionReason());
        assertNull(rejected.getFirst().getLockExpiresAt());
        assertTrue(reservationRepository.findBySourceEventId(rejected.getFirst().getTransactionId()).isEmpty());
        assertEquals(1, events.stream(SeparationRequestRegisteredEvent.class).count());
    }

    @Test
    void concurrentRequestsLetOnlyOneBuyerTakeTheLot() throws Exception {
        var lot = lot();
        var buyers = new ArrayList<long[]>();
        for (int i = 0; i < 4; i++) {
            var buyer = BUYERS.incrementAndGet();
            buyers.add(new long[]{buyer, quote(buyer, lot)});
        }
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(buyers.size())) {
            var futures = buyers.stream()
                    .map(pair -> executor.submit(() -> {
                        start.await();
                        return separate(pair[0], lot.getId(), pair[1]).andReturn().getResponse().getStatus();
                    }))
                    .toList();
            start.countDown();

            var statuses = new ArrayList<Integer>();
            for (var future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }

            assertEquals(1, statuses.stream().filter(code -> code == 201).count(), statuses.toString());
            assertEquals(3, statuses.stream().filter(code -> code == 409).count(), statuses.toString());
        }
    }

    @Test
    void anExpiredQuotationMustBeSimulatedAgain() throws Exception {
        var buyer = BUYERS.incrementAndGet();
        var lot = lot();
        var quotation = quote(buyer, lot);
        var entity = quotationJpaRepository.findById(quotation).orElseThrow();
        entity.setValidUntil(Instant.now().minusSeconds(60));
        quotationJpaRepository.save(entity);

        separate(buyer, lot.getId(), quotation)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
        assertEquals(LotStatus.AVAILABLE, reload(lot).getStatus());
    }

    @Test
    void theQuotationMustBeTheBuyersAndOfThatLot() throws Exception {
        var buyer = BUYERS.incrementAndGet();
        var otherBuyer = BUYERS.incrementAndGet();
        var lot = lot();
        var otherLot = lot();
        var quotation = quote(buyer, lot);

        separate(otherBuyer, lot.getId(), quotation)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUOTATION_NOT_FOUND"));
        separate(buyer, otherLot.getId(), quotation)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUOTATION_NOT_FOUND"));
        separate(buyer, lot.getId(), 999_999L)
                .andExpect(status().isNotFound());
        assertEquals(LotStatus.AVAILABLE, reload(lot).getStatus());
        assertEquals(LotStatus.AVAILABLE, reload(otherLot).getStatus());
    }

    @Test
    void onlyBuyersRequestSeparations() throws Exception {
        var lot = lot();

        mockMvc.perform(post("/api/v1/lots/{lotId}/separation-requests", lot.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.FIELD_AGENT, 7L))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quotationId\": 1}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/lots/{lotId}/separation-requests", lot.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quotationId\": 1}"))
                .andExpect(status().isUnauthorized());
        separate(BUYERS.incrementAndGet(), lot.getId(), null)
                .andExpect(status().isBadRequest());
    }

    /** Simulates 9 000 down and 12 months, as the portal does before separating. */
    private long quote(long buyer, Lot lot) throws Exception {
        var response = mockMvc.perform(post("/api/v1/lots/{lotId}/quotations", lot.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"initialPayment\": 9000, \"termMonths\": 12}"))
                .andReturn().getResponse();
        assertEquals(201, response.getStatus(), response.getContentAsString());
        return JsonPath.<Integer>read(response.getContentAsString(), "$.id").longValue();
    }

    private ResultActions separate(long buyer, Long lotId, Long quotation) throws Exception {
        return mockMvc.perform(post("/api/v1/lots/{lotId}/separation-requests", lotId)
                .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, buyer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quotationId\": %s}".formatted(quotation)));
    }

    private Lot reload(Lot lot) {
        return lotRepository.findByProjectId(lot.getProjectId()).getFirst();
    }

    /** A lot of its own published project, with a 20 % minimum down payment, 12 % a year and up to 120 months. */
    private Lot lot() {
        var rules = new FinancingRules(new BigDecimal("20"), new BigDecimal("12"), 120, BigDecimal.ONE);
        var project = Project.create("Separaciones web", "Chilca", null, null, rules);
        project.publish(1);
        var projectId = projectRepository.save(project).getId();
        var boundary = LotBoundary.fromPolygonRings(List.of(List.of(
                List.of(0.0, 0.0), List.of(0.001, 0.0), List.of(0.001, 0.001), List.of(0.0, 0.0))));
        return lotRepository.saveAll(List.of(Lot.register(projectId, "W-01",
                new LotDimensions(new BigDecimal("120"), null, null), Money.of(new BigDecimal("45000")), boundary)))
                .getFirst();
    }

    private String bearer(Role role, long userId) {
        var user = User.restore(userId, role.name().toLowerCase() + userId + "@mail.com", "hash", role,
                UserStatus.ACTIVE, null, null, 0, null);
        return "Bearer " + tokenService.generateToken(user);
    }
}
