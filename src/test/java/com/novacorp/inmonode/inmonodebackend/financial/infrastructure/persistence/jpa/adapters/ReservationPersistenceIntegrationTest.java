package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.adapters;

import com.novacorp.inmonode.inmonodebackend.TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FinancingRules;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotBoundary;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotDimensions;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ReservationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reservations and lot blocks against a real PostgreSQL: device UUIDs, the lots-reservations references,
 * the locked read and the lookup of expired blocks.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ReservationPersistenceIntegrationTest {

    private static final Duration DAY = Duration.ofHours(24);
    private static final Money AMOUNT = Money.of(new BigDecimal("1500"));

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private LotRepository lotRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void reservationAndLotBlockAreStoredAndRead() {
        var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        var lot = persistedLot();
        var prospect = UUID.randomUUID();
        var source = UUID.randomUUID();

        var reservation = reservationRepository.save(
                Reservation.fromFieldSync(lot.getId(), 7L, prospect, source, AMOUNT, now.minusSeconds(3600)));
        block(lot.getId(), reservation.getId(), now);

        var found = reservationRepository.findBySourceEventId(source).orElseThrow();
        assertEquals(reservation.getId(), found.getId());
        assertEquals(prospect, found.getProspectId());
        assertEquals(ReservationStatus.BLOCKED, found.getStatus());
        assertEquals(now.minusSeconds(3600), found.getReservedAt());
        assertEquals(AMOUNT, found.getInitialAmount());

        var blocked = lotRepository.findByProjectId(lot.getProjectId()).getFirst();
        assertEquals(LotStatus.BLOCKED, blocked.getStatus());
        assertEquals(reservation.getId(), blocked.getCurrentReservationId());
        assertEquals(now.plus(DAY), blocked.getBlockedUntil());
    }

    @Test
    void onlyBlocksThatRanOutAreFoundAsExpired() {
        var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        var expiredLot = persistedLot();
        var activeLot = persistedLot();
        block(expiredLot.getId(), reserve(expiredLot), now.minus(Duration.ofHours(25)));
        block(activeLot.getId(), reserve(activeLot), now);

        var expiredIds = lotRepository.findWithExpiredBlock(now).stream().map(Lot::getId).toList();

        assertTrue(expiredIds.contains(expiredLot.getId()));
        assertFalse(expiredIds.contains(activeLot.getId()));
    }

    @Test
    void aDeviceReservationIdCanBeStoredOnlyOnce() {
        var lot = persistedLot();
        var source = UUID.randomUUID();
        reservationRepository.save(Reservation.fromFieldSync(lot.getId(), 7L, UUID.randomUUID(), source, AMOUNT,
                Instant.now()));

        assertThrows(DataIntegrityViolationException.class, () -> reservationRepository.save(
                Reservation.cancelledByConflict(lot.getId(), 8L, UUID.randomUUID(), source, AMOUNT, Instant.now())));
    }

    /** Blocks the lot inside a transaction, as the locked read requires. */
    private void block(Long lotId, Long reservationId, Instant at) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var lot = lotRepository.findByIdForUpdate(lotId).orElseThrow();
            assertTrue(lot.block(reservationId, at, DAY));
            lotRepository.save(lot);
        });
    }

    private Long reserve(Lot lot) {
        return reservationRepository.save(Reservation.fromFieldSync(lot.getId(), 7L, UUID.randomUUID(),
                UUID.randomUUID(), AMOUNT, Instant.now())).getId();
    }

    private Lot persistedLot() {
        var rules = new FinancingRules(BigDecimal.TEN, BigDecimal.TEN, 60, BigDecimal.ONE);
        var project = projectRepository.save(Project.create("Reservas", "Chilca", null, null, rules));
        var boundary = LotBoundary.fromPolygonRings(List.of(List.of(
                List.of(0.0, 0.0), List.of(0.001, 0.0), List.of(0.001, 0.001), List.of(0.0, 0.0))));
        var lot = Lot.register(project.getId(), "R-01", new LotDimensions(new BigDecimal("120"), null, null),
                Money.of(new BigDecimal("45000")), boundary);
        return lotRepository.saveAll(List.of(lot)).getFirst();
    }
}
