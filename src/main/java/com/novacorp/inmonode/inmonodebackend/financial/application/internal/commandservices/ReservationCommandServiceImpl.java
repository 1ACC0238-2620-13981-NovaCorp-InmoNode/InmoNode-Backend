package com.novacorp.inmonode.inmonodebackend.financial.application.internal.commandservices;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ConsolidateFieldReservationCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FieldReservationOutcome;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FieldReservationOutcome.ConflictReason;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationChannel;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ReservationRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ReservationCommandService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Service
public class ReservationCommandServiceImpl implements ReservationCommandService {

    private final LotRepository lotRepository;
    private final ReservationRepository reservationRepository;
    private final Clock clock;

    public ReservationCommandServiceImpl(LotRepository lotRepository, ReservationRepository reservationRepository,
                                         Clock clock) {
        this.lotRepository = lotRepository;
        this.reservationRepository = reservationRepository;
        this.clock = clock;
    }

    /**
     * The lot is locked first, so every decision about it (duplicate, conflict, block) is taken by one consolidation
     * at a time, in the order they reach the server.
     */
    @Override
    @Transactional
    public FieldReservationOutcome handle(ConsolidateFieldReservationCommand command) {
        var lot = lotRepository.findByIdForUpdate(command.lotId()).orElse(null);
        if (lot == null) {
            return FieldReservationOutcome.conflict(null, ConflictReason.LOT_NOT_FOUND);
        }
        var existing = reservationRepository.findBySourceEventId(command.sourceEventId());
        if (existing.isPresent()) {
            var reservation = existing.get();
            var heldUntil = Objects.equals(lot.getCurrentReservationId(), reservation.getId()) ? lot.getBlockedUntil() : null;
            return FieldReservationOutcome.duplicateOf(reservation, heldUntil);
        }
        // PostgreSQL keeps microseconds: the block answered now must equal the one a re-send reads back.
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        expirePreviousBlock(lot, now);
        if (!lot.isAvailable(now)) {
            var conflicted = reservationRepository.save(Reservation.cancelledByConflict(command.lotId(),
                    command.agentId(), command.prospectId(), command.sourceEventId(), command.initialAmount(),
                    command.reservedAt()));
            return FieldReservationOutcome.conflict(conflicted, ConflictReason.LOT_UNAVAILABLE);
        }
        var reservation = reservationRepository.save(Reservation.fromFieldSync(command.lotId(), command.agentId(),
                command.prospectId(), command.sourceEventId(), command.initialAmount(), command.reservedAt()));
        lot.block(Objects.requireNonNull(reservation.getId()), now, ReservationChannel.FIELD.blockValidity());
        var blocked = lotRepository.save(lot);
        return FieldReservationOutcome.synced(reservation, Objects.requireNonNull(blocked.getBlockedUntil()));
    }

    /** A block that ran out without payment evidence frees the lot and expires the reservation that held it. */
    private void expirePreviousBlock(Lot lot, Instant now) {
        lot.releaseExpiredBlock(now)
                .flatMap(reservationRepository::findById)
                .ifPresent(previous -> {
                    if (previous.expire()) {
                        reservationRepository.save(previous);
                    }
                });
    }
}
