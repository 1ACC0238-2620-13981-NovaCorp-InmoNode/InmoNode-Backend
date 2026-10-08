package com.novacorp.inmonode.inmonodebackend.financial.application.internal.commandservices;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ConsolidateFieldReservationCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ReceiveVoucherEvidenceCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ReleaseExpiredLotBlocksCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.RequestWebReservationCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.entities.PaymentEvidence;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FieldReservationOutcome;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FinancingPlan;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FieldReservationOutcome.ConflictReason;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationChannel;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.WebReservationOutcome;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
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
    private final ProjectRepository projectRepository;
    private final Clock clock;

    public ReservationCommandServiceImpl(LotRepository lotRepository, ReservationRepository reservationRepository,
                                         ProjectRepository projectRepository, Clock clock) {
        this.lotRepository = lotRepository;
        this.reservationRepository = reservationRepository;
        this.projectRepository = projectRepository;
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
        releaseExpiredBlock(lot, now);
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

    /**
     * Same lock as the field consolidation, so web and field requests for one lot are decided one after the other.
     * Joins the caller's transaction: the requesting context stores its request together with the block.
     */
    @Override
    @Transactional
    public WebReservationOutcome handle(RequestWebReservationCommand command) {
        var lot = lotRepository.findByIdForUpdate(command.lotId()).orElse(null);
        var published = lot != null && projectRepository.findById(lot.getProjectId())
                .filter(Project::isPublished)
                .isPresent();
        if (!published) {
            return WebReservationOutcome.lotNotFound();
        }
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var existing = reservationRepository.findBySourceEventId(command.sourceEventId());
        if (existing.isPresent()) {
            var stillHeld = Objects.equals(lot.getCurrentReservationId(), existing.get().getId())
                    && lot.getStatus() == LotStatus.BLOCKED && !lot.hasExpiredBlock(now);
            return stillHeld
                    ? WebReservationOutcome.blocked(existing.get(), Objects.requireNonNull(lot.getBlockedUntil()))
                    : WebReservationOutcome.unavailable();
        }
        releaseExpiredBlock(lot, now);
        if (!lot.isAvailable(now)) {
            return WebReservationOutcome.unavailable();
        }
        // The plan applies the quotation terms to the price the lot has now, under the lock.
        var plan = new FinancingPlan(lot.getPrice(), command.termMonths(), command.annualInterestRate());
        var reservation = reservationRepository.save(Reservation.fromWebRequest(command.lotId(), command.buyerId(),
                command.sourceEventId(), command.initialAmount(), plan, command.requestedAt()));
        lot.block(Objects.requireNonNull(reservation.getId()), now, ReservationChannel.WEB.blockValidity());
        var blocked = lotRepository.save(lot);
        return WebReservationOutcome.blocked(reservation, Objects.requireNonNull(blocked.getBlockedUntil()));
    }

    /**
     * Each lot is read again under lock: a consolidation may have taken it since it was listed, and then it is no
     * longer expired.
     */
    @Override
    @Transactional
    public int handle(ReleaseExpiredLotBlocksCommand command) {
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var released = 0;
        for (var lotId : lotRepository.findIdsWithExpiredBlock(now)) {
            var lot = lotRepository.findByIdForUpdate(lotId).orElse(null);
            if (lot != null && releaseExpiredBlock(lot, now)) {
                lotRepository.save(lot);
                released++;
            }
        }
        return released;
    }

    /**
     * The lot is locked first, as in the consolidation, and a block that ran out is released before deciding: a
     * voucher that arrives after the block ended is late even if the release job has not run yet.
     */
    @Override
    @Transactional
    public PaymentEvidence handle(ReceiveVoucherEvidenceCommand command) {
        var lotId = reservationRepository.findBySourceEventId(command.reservationId())
                .map(Reservation::getLotId)
                .orElseThrow(() -> new IllegalStateException("no reservation %s for the payment evidence %s"
                        .formatted(command.reservationId(), command.voucherId())));
        var lot = lotRepository.findByIdForUpdate(lotId)
                .orElseThrow(() -> new IllegalStateException("lot %d of reservation %s does not exist"
                        .formatted(lotId, command.reservationId())));
        var submittedAt = command.submittedAt();
        if (releaseExpiredBlock(lot, submittedAt)) {
            lotRepository.save(lot);
        }
        // Read under the lock: releasing the block may have expired it.
        var reservation = reservationRepository.findBySourceEventId(command.reservationId()).orElseThrow();
        var received = reservation.findEvidence(command.voucherId());
        if (received.isPresent()) {
            return received.get();
        }
        var evidence = PaymentEvidence.fromVoucher(command.voucherId(), command.amount(), command.operationDate(),
                command.operationCode(), command.manuallyCorrected(), command.objectKey(), submittedAt);
        if (reservation.attachEvidence(evidence)) {
            if (!lot.moveToPendingVerification(Objects.requireNonNull(reservation.getId()), submittedAt)) {
                throw new IllegalStateException("reservation %s is blocked but does not hold lot %d"
                        .formatted(command.reservationId(), lotId));
            }
            lotRepository.save(lot);
        }
        return reservationRepository.save(reservation).findEvidence(command.voucherId()).orElseThrow();
    }

    /**
     * A block that ran out without payment evidence frees the lot and expires the reservation that held it.
     * The caller saves the lot.
     *
     * @return whether the lot had an expired block
     */
    private boolean releaseExpiredBlock(Lot lot, Instant now) {
        var previousReservationId = lot.releaseExpiredBlock(now);
        previousReservationId
                .flatMap(reservationRepository::findById)
                .ifPresent(previous -> {
                    if (previous.expire()) {
                        reservationRepository.save(previous);
                    }
                });
        return previousReservationId.isPresent();
    }
}
