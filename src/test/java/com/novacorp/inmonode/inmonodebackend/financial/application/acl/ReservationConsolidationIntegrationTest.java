package com.novacorp.inmonode.inmonodebackend.financial.application.acl;

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
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl.FieldReservationConsolidation;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl.FieldReservationConsolidationFacade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Consolidation of field reservations (US-11, US-12, US-32) through the facade the field context uses, against
 * a real PostgreSQL.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ReservationConsolidationIntegrationTest {

    private static final long AGENT = 7L;
    private static final BigDecimal AMOUNT = new BigDecimal("1500");

    @Autowired
    private FieldReservationConsolidationFacade facade;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private LotRepository lotRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private LotJpaRepository lotJpaRepository;

    @Test
    void firstReservationOfAnAvailableLotHoldsItForADay() {
        var lot = availableLot();
        var before = Instant.now();

        var outcome = consolidate(UUID.randomUUID(), lot);

        assertEquals("SYNCED", outcome.result());
        assertNotNull(outcome.reservationId());
        assertEquals("BLOCKED", outcome.reservationStatus());
        assertNull(outcome.conflictReason());
        var blockedUntil = outcome.blockedUntil();
        assertNotNull(blockedUntil);
        assertFalse(blockedUntil.isBefore(before.plus(Duration.ofHours(24)).minusSeconds(1)));
        assertTrue(blockedUntil.isBefore(Instant.now().plus(Duration.ofHours(24)).plusSeconds(1)));
        var stored = reload(lot);
        assertEquals(LotStatus.BLOCKED, stored.getStatus());
        assertEquals(outcome.reservationId(), stored.getCurrentReservationId());
    }

    @Test
    void laterReservationOfATakenLotIsAConflictAndIsKept() {
        var lot = availableLot();
        var first = consolidate(UUID.randomUUID(), lot);
        var secondId = UUID.randomUUID();

        var outcome = consolidate(secondId, lot);

        assertEquals("CONFLICT", outcome.result());
        assertEquals("LOT_UNAVAILABLE", outcome.conflictReason());
        assertEquals("CANCELLED_BY_CONFLICT", outcome.reservationStatus());
        assertNull(outcome.blockedUntil());
        assertEquals(ReservationStatus.CANCELLED_BY_CONFLICT,
                reservationRepository.findBySourceEventId(secondId).orElseThrow().getStatus());
        assertEquals(first.reservationId(), reload(lot).getCurrentReservationId(), "the first one keeps the lot");
    }

    @Test
    void resentReservationsAreDuplicatesWithTheirOriginalResult() {
        var lot = availableLot();
        var syncedId = UUID.randomUUID();
        var conflictId = UUID.randomUUID();
        var synced = consolidate(syncedId, lot);
        var conflict = consolidate(conflictId, lot);

        var resentSynced = consolidate(syncedId, lot);
        var resentConflict = consolidate(conflictId, lot);

        assertEquals("DUPLICATE", resentSynced.result());
        assertEquals("SYNCED", resentSynced.originalResult());
        assertEquals(synced.reservationId(), resentSynced.reservationId());
        assertEquals(synced.blockedUntil(), resentSynced.blockedUntil());
        assertEquals("DUPLICATE", resentConflict.result());
        assertEquals("CONFLICT", resentConflict.originalResult());
        assertEquals("LOT_UNAVAILABLE", resentConflict.conflictReason());
        assertEquals(conflict.reservationId(), resentConflict.reservationId());
        assertNull(resentConflict.blockedUntil());
    }

    @Test
    void anExpiredBlockIsTakenByTheNextReservationAndThePreviousOneExpires() {
        var lot = availableLot();
        var previousId = UUID.randomUUID();
        consolidate(previousId, lot);
        forceBlockExpiry(lot);

        var outcome = consolidate(UUID.randomUUID(), lot);

        assertEquals("SYNCED", outcome.result());
        assertEquals(outcome.reservationId(), reload(lot).getCurrentReservationId());
        assertEquals(ReservationStatus.EXPIRED,
                reservationRepository.findBySourceEventId(previousId).orElseThrow().getStatus());
    }

    @Test
    void concurrentReservationsOfTheSameLotLetOnlyTheFirstTakeIt() throws Exception {
        var lot = availableLot();
        var agents = 5;
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(agents)) {
            var futures = IntStream.range(0, agents)
                    .mapToObj(i -> executor.submit(() -> {
                        start.await();
                        return consolidate(UUID.randomUUID(), lot).result();
                    }))
                    .toList();
            start.countDown();

            var results = new ArrayList<String>();
            for (var future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }

            assertEquals(1, results.stream().filter("SYNCED"::equals).count(), results.toString());
            assertEquals(agents - 1, results.stream().filter("CONFLICT"::equals).count(), results.toString());
        }
    }

    @Test
    void reservationOfAnUnknownLotIsAConflictThatIsNotStored() {
        var reservationId = UUID.randomUUID();

        var outcome = facade.consolidate(reservationId, 999_999L, AGENT, UUID.randomUUID(), AMOUNT, Instant.now());

        assertEquals("CONFLICT", outcome.result());
        assertEquals("LOT_NOT_FOUND", outcome.conflictReason());
        assertNull(outcome.reservationId());
        assertTrue(reservationRepository.findBySourceEventId(reservationId).isEmpty());
    }

    private FieldReservationConsolidation consolidate(UUID reservationId, Lot lot) {
        return facade.consolidate(reservationId, lot.getId(), AGENT, UUID.randomUUID(), AMOUNT,
                Instant.now().minus(Duration.ofHours(2)));
    }

    private Lot reload(Lot lot) {
        return lotRepository.findByProjectId(lot.getProjectId()).getFirst();
    }

    private void forceBlockExpiry(Lot lot) {
        var entity = lotJpaRepository.findById(lot.getId()).orElseThrow();
        entity.setBlockedUntil(Instant.now().minusSeconds(60));
        lotJpaRepository.save(entity);
    }

    private Lot availableLot() {
        var rules = new FinancingRules(BigDecimal.TEN, BigDecimal.TEN, 60, BigDecimal.ONE);
        var project = projectRepository.save(Project.create("Separaciones", "Chilca", null, null, rules));
        var boundary = LotBoundary.fromPolygonRings(List.of(List.of(
                List.of(0.0, 0.0), List.of(0.001, 0.0), List.of(0.001, 0.001), List.of(0.0, 0.0))));
        var lot = Lot.register(project.getId(), "S-01", new LotDimensions(new BigDecimal("120"), null, null),
                Money.of(new BigDecimal("45000")), boundary);
        return lotRepository.saveAll(List.of(lot)).getFirst();
    }
}
