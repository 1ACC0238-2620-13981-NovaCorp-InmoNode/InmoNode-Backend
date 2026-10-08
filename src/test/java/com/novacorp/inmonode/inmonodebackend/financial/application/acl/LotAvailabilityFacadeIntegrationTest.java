package com.novacorp.inmonode.inmonodebackend.financial.application.acl;

import com.novacorp.inmonode.inmonodebackend.TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ReleaseExpiredLotBlocksCommand;
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
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ReservationCommandService;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.repositories.LotJpaRepository;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl.FieldReservationConsolidationFacade;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl.LotAvailabilityFacade;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl.LotBlock;
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
 * The Open Host Service Cotización y Separación Digital uses to read lots and block them for web separation requests
 * (US-17, US-19), against a real PostgreSQL. Each test uses its own lot.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class LotAvailabilityFacadeIntegrationTest {

    private static final long BUYER = 41L;
    private static final long OTHER_BUYER = 42L;
    private static final BigDecimal DOWN_PAYMENT = new BigDecimal("9000");
    private static final BigDecimal TWELVE_PERCENT = new BigDecimal("12");

    @Autowired
    private LotAvailabilityFacade facade;

    @Autowired
    private FieldReservationConsolidationFacade fieldFacade;

    @Autowired
    private ReservationCommandService reservationCommandService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private LotRepository lotRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private LotJpaRepository lotJpaRepository;

    @Test
    void aLotOfAPublishedProjectIsOfferedWithTheProjectRules() {
        var lot = lot(true);

        var offer = facade.findLotOffer(lot.getId()).orElseThrow();

        assertEquals(lot.getId(), offer.lotId());
        assertEquals(lot.getProjectId(), offer.projectId());
        assertEquals("Cotizaciones", offer.projectName());
        assertEquals("Q-01", offer.lotCode());
        assertEquals(0, new BigDecimal("120").compareTo(offer.area()));
        assertEquals(new BigDecimal("45000.00"), offer.price());
        assertEquals("PEN", offer.currency());
        assertTrue(offer.available());
        assertEquals(new BigDecimal("20.00"), offer.minDownPaymentPercentage());
        assertEquals(new BigDecimal("12.500"), offer.annualInterestRate());
        assertEquals(120, offer.maxTermMonths());
    }

    @Test
    void lotsOfDraftProjectsAndUnknownLotsAreNotOffered() {
        assertTrue(facade.findLotOffer(lot(false).getId()).isEmpty());
        assertTrue(facade.findLotOffer(999_999L).isEmpty());
    }

    @Test
    void theFirstRequestBlocksTheLotForAnHourAsAWebReservation() {
        var lot = lot(true);
        var transactionId = UUID.randomUUID();
        var requestedAt = Instant.now();

        var block = block(transactionId, lot, BUYER);

        assertEquals("BLOCKED", block.result());
        assertNotNull(block.reservationId());
        var blockedUntil = block.blockedUntil();
        assertNotNull(blockedUntil);
        assertFalse(blockedUntil.isBefore(requestedAt.plus(Duration.ofHours(1)).minusSeconds(1)));
        assertTrue(blockedUntil.isBefore(Instant.now().plus(Duration.ofHours(1)).plusSeconds(1)));
        var reservation = reservationRepository.findBySourceEventId(transactionId).orElseThrow();
        assertEquals(block.reservationId(), reservation.getId());
        assertEquals(ReservationChannel.WEB, reservation.getChannel());
        assertEquals(ReservationStatus.BLOCKED, reservation.getStatus());
        assertEquals(BUYER, reservation.getRequesterId());
        assertNull(reservation.getProspectId());
        assertEquals(new Money(DOWN_PAYMENT, "PEN"), reservation.getInitialAmount());
        var plan = reservation.getFinancingPlan();
        assertNotNull(plan, "the quotation terms become the reservation financing plan");
        assertEquals(new Money(new BigDecimal("45000"), "PEN"), plan.lotPrice());
        assertEquals(12, plan.termMonths());
        assertEquals(new BigDecimal("12.000"), plan.annualInterestRate());
        var blocked = reload(lot);
        assertEquals(LotStatus.BLOCKED, blocked.getStatus());
        assertEquals(reservation.getId(), blocked.getCurrentReservationId());
        assertEquals(blockedUntil, blocked.getBlockedUntil());
        assertFalse(facade.findLotOffer(lot.getId()).orElseThrow().available());
    }

    @Test
    void aLaterRequestFindsTheLotUnavailableAndNothingIsStored() {
        var lot = lot(true);
        block(UUID.randomUUID(), lot, BUYER);
        var later = UUID.randomUUID();

        var block = block(later, lot, OTHER_BUYER);

        assertEquals("LOT_UNAVAILABLE", block.result());
        assertNull(block.reservationId());
        assertNull(block.blockedUntil());
        assertTrue(reservationRepository.findBySourceEventId(later).isEmpty());
    }

    @Test
    void retryingTheSameRequestAnswersTheSameBlock() {
        var lot = lot(true);
        var transactionId = UUID.randomUUID();
        var first = block(transactionId, lot, BUYER);

        var retry = block(transactionId, lot, BUYER);

        assertEquals(first, retry);
    }

    @Test
    void lotsOfDraftProjectsAndUnknownLotsCannotBeBlocked() {
        var draftRequest = UUID.randomUUID();

        assertEquals("LOT_NOT_FOUND", block(draftRequest, lot(false), BUYER).result());
        assertEquals("LOT_NOT_FOUND", facade.blockLot(UUID.randomUUID(), 999_999L, BUYER, DOWN_PAYMENT, "PEN",
                12, TWELVE_PERCENT, Instant.now()).result());
        assertTrue(reservationRepository.findBySourceEventId(draftRequest).isEmpty());
    }

    @Test
    void anExpiredWebBlockIsReleasedAndTheLotCanBeTakenAgain() {
        var lot = lot(true);
        var expired = UUID.randomUUID();
        block(expired, lot, BUYER);
        forceBlockExpiry(lot);

        reservationCommandService.handle(new ReleaseExpiredLotBlocksCommand());

        assertEquals(ReservationStatus.EXPIRED, reservationRepository.findBySourceEventId(expired).orElseThrow()
                .getStatus());
        assertEquals(LotStatus.AVAILABLE, reload(lot).getStatus());
        assertEquals("BLOCKED", block(UUID.randomUUID(), lot, OTHER_BUYER).result());
        assertEquals("LOT_UNAVAILABLE", block(expired, lot, BUYER).result(),
                "a retry of the expired request does not get the lot back");
    }

    @Test
    void webAndFieldRequestsShareTheSameAuthority() {
        var lot = lot(true);
        block(UUID.randomUUID(), lot, BUYER);

        var field = fieldFacade.consolidate(UUID.randomUUID(), lot.getId(), 7L, UUID.randomUUID(),
                new BigDecimal("1500"), Instant.now());

        assertEquals("CONFLICT", field.result());
        assertEquals("LOT_UNAVAILABLE", field.conflictReason());
    }

    @Test
    void concurrentRequestsOfTheSameLotLetOnlyOneBlockIt() throws Exception {
        var lot = lot(true);
        var buyers = 5;
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(buyers)) {
            var futures = IntStream.range(0, buyers)
                    .mapToObj(buyer -> executor.submit(() -> {
                        start.await();
                        return block(UUID.randomUUID(), lot, 100L + buyer).result();
                    }))
                    .toList();
            start.countDown();

            var results = new ArrayList<String>();
            for (var future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }

            assertEquals(1, results.stream().filter("BLOCKED"::equals).count(), results.toString());
            assertEquals(buyers - 1, results.stream().filter("LOT_UNAVAILABLE"::equals).count(), results.toString());
        }
    }

    @Test
    void theAmountMustBePositiveAndInAnIsoCurrency() {
        var lot = lot(true);

        assertThrows(IllegalArgumentException.class,
                () -> facade.blockLot(UUID.randomUUID(), lot.getId(), BUYER, BigDecimal.ZERO, "PEN", 12,
                        TWELVE_PERCENT, Instant.now()));
        assertThrows(IllegalArgumentException.class,
                () -> facade.blockLot(UUID.randomUUID(), lot.getId(), BUYER, DOWN_PAYMENT, "soles", 12,
                        TWELVE_PERCENT, Instant.now()));
        assertThrows(IllegalArgumentException.class,
                () -> facade.blockLot(UUID.randomUUID(), lot.getId(), BUYER, DOWN_PAYMENT, "PEN", 0,
                        TWELVE_PERCENT, Instant.now()), "a term out of range");
        assertEquals(LotStatus.AVAILABLE, reload(lot).getStatus());
    }

    private LotBlock block(UUID transactionId, Lot lot, long buyer) {
        return facade.blockLot(transactionId, lot.getId(), buyer, DOWN_PAYMENT, "PEN", 12, TWELVE_PERCENT,
                Instant.now());
    }

    private Lot reload(Lot lot) {
        return lotRepository.findByProjectId(lot.getProjectId()).getFirst();
    }

    private void forceBlockExpiry(Lot lot) {
        var entity = lotJpaRepository.findById(lot.getId()).orElseThrow();
        entity.setBlockedUntil(Instant.now().minusSeconds(60));
        lotJpaRepository.save(entity);
    }

    /** A lot of its own project, published or still a draft. */
    private Lot lot(boolean published) {
        var rules = new FinancingRules(new BigDecimal("20"), new BigDecimal("12.5"), 120, BigDecimal.ONE);
        var project = Project.create("Cotizaciones", "Chilca", null, null, rules);
        if (published) {
            project.publish(1);
        }
        var projectId = projectRepository.save(project).getId();
        var boundary = LotBoundary.fromPolygonRings(List.of(List.of(
                List.of(0.0, 0.0), List.of(0.001, 0.0), List.of(0.001, 0.001), List.of(0.0, 0.0))));
        return lotRepository.saveAll(List.of(Lot.register(projectId, "Q-01",
                new LotDimensions(new BigDecimal("120"), null, null), Money.of(new BigDecimal("45000")), boundary)))
                .getFirst();
    }
}
