package com.novacorp.inmonode.inmonodebackend.financial.application.internal.commandservices;

import com.novacorp.inmonode.inmonodebackend.TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ReleaseExpiredLotBlocksCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FinancingRules;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotBoundary;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotDimensions;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ReservationRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ReservationCommandService;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.repositories.LotJpaRepository;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.scheduling.ExpiredLotBlockReleaseJob;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl.FieldReservationConsolidationFacade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Release of expired lot blocks (Lot Block) against a real PostgreSQL. The scheduled job is off in the test
 * profile, so the command is called directly.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ReleaseExpiredLotBlocksIntegrationTest {

    @Autowired
    private ReservationCommandService reservationCommandService;

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

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void expiredBlocksAreReleasedAndTheirReservationsExpireWhileActiveOnesStay() {
        var projectId = publishedProject();
        var expiredLot = lot(projectId, "E-01");
        var activeLot = lot(projectId, "E-02");
        var expiredReservation = reserve(expiredLot);
        var activeReservation = reserve(activeLot);
        forceBlockExpiry(expiredLot);
        assertEquals(0, availableLots(projectId), "an unreleased expired block still shows as blocked");

        var released = reservationCommandService.handle(new ReleaseExpiredLotBlocksCommand());

        assertTrue(released >= 1);
        var lots = lotsByCode(projectId);
        assertEquals(LotStatus.AVAILABLE, lots.get("E-01").getStatus());
        assertNull(lots.get("E-01").getCurrentReservationId());
        assertNull(lots.get("E-01").getBlockedUntil());
        assertEquals(LotStatus.BLOCKED, lots.get("E-02").getStatus());
        assertEquals(ReservationStatus.EXPIRED, statusOf(expiredReservation));
        assertEquals(ReservationStatus.BLOCKED, statusOf(activeReservation));
        assertEquals(1, availableLots(projectId), "the catalog shows the released lot as available");

        assertEquals(0, reservationCommandService.handle(new ReleaseExpiredLotBlocksCommand()),
                "nothing is left to release");
    }

    @Test
    void theScheduledJobIsOffInTheTestProfile() {
        assertTrue(applicationContext.getBeansOfType(ExpiredLotBlockReleaseJob.class).isEmpty());
    }

    private UUID reserve(Lot lot) {
        var reservationId = UUID.randomUUID();
        var outcome = facade.consolidate(reservationId, lot.getId(), 7L, UUID.randomUUID(), new BigDecimal("1500"),
                Instant.now());
        assertEquals("SYNCED", outcome.result());
        return reservationId;
    }

    private ReservationStatus statusOf(UUID reservationId) {
        return reservationRepository.findBySourceEventId(reservationId).orElseThrow().getStatus();
    }

    private void forceBlockExpiry(Lot lot) {
        var entity = lotJpaRepository.findById(lot.getId()).orElseThrow();
        entity.setBlockedUntil(Instant.now().minusSeconds(60));
        lotJpaRepository.save(entity);
    }

    private long availableLots(Long projectId) {
        return lotRepository.summarizeByProjectIds(List.of(projectId)).get(projectId).availableLots();
    }

    private Map<String, Lot> lotsByCode(Long projectId) {
        return lotRepository.findByProjectId(projectId).stream()
                .collect(Collectors.toMap(Lot::getCode, lot -> lot));
    }

    private Long publishedProject() {
        var rules = new FinancingRules(BigDecimal.TEN, BigDecimal.TEN, 60, BigDecimal.ONE);
        var project = Project.create("Vencimientos", "Chilca", null, null, rules);
        project.publish(1);
        return projectRepository.save(project).getId();
    }

    private Lot lot(Long projectId, String code) {
        var boundary = LotBoundary.fromPolygonRings(List.of(List.of(
                List.of(0.0, 0.0), List.of(0.001, 0.0), List.of(0.001, 0.001), List.of(0.0, 0.0))));
        return lotRepository.saveAll(List.of(Lot.register(projectId, code,
                new LotDimensions(new BigDecimal("120"), null, null), Money.of(new BigDecimal("45000")), boundary)))
                .getFirst();
    }
}
