package com.novacorp.inmonode.inmonodebackend.financial.application.internal.commandservices;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.acl.ExternalIamService;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ApprovePaymentEvidenceCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.RejectPaymentEvidenceCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.entities.PaymentEvidence;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.VerificationOutcome;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ReservationRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.FinancialVerificationService;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.VerificationCommandService;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Every decision locks the lot first, as the consolidation and the voucher reception do, so a decision never races a
 * new reservation or a substitute voucher of the same lot.
 */
@Service
public class VerificationCommandServiceImpl implements VerificationCommandService {

    private final ReservationRepository reservationRepository;
    private final LotRepository lotRepository;
    private final ExternalIamService externalIamService;
    private final Clock clock;

    public VerificationCommandServiceImpl(ReservationRepository reservationRepository, LotRepository lotRepository,
                                          ExternalIamService externalIamService, Clock clock) {
        this.reservationRepository = reservationRepository;
        this.lotRepository = lotRepository;
        this.externalIamService = externalIamService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result<VerificationOutcome, ApplicationError> handle(ApprovePaymentEvidenceCommand command) {
        return decide(command.evidenceId(), (reservation, evidence) ->
                FinancialVerificationService.approvalObstacle(reservation, evidence)
                        .map(reason -> ApplicationError.businessRuleViolation("payment-verification", reason)),
                (reservation, lot, reviewerId, now, evidence) -> {
                    reservation.verify(evidence.getReference(), reviewerId, command.note(), now);
                    if (!lot.markReserved(Objects.requireNonNull(reservation.getId()))) {
                        throw new IllegalStateException("lot %d is not waiting for the verification of reservation %s"
                                .formatted(lot.getId(), reservation.getSourceEventId()));
                    }
                    return true;
                });
    }

    @Override
    @Transactional
    public Result<VerificationOutcome, ApplicationError> handle(RejectPaymentEvidenceCommand command) {
        return decide(command.evidenceId(), (reservation, evidence) -> Optional.empty(),
                (reservation, lot, reviewerId, now, evidence) -> {
                    var reopened = reservation.rejectEvidence(evidence.getReference(), reviewerId, command.reason(),
                            now);
                    if (reopened && !lot.reopenBlock(Objects.requireNonNull(reservation.getId()), now,
                            reservation.getChannel().blockValidity())) {
                        throw new IllegalStateException("lot %d is not waiting for the verification of reservation %s"
                                .formatted(lot.getId(), reservation.getSourceEventId()));
                    }
                    return reopened;
                });
    }

    /**
     * @param obstacle what forbids the decision, if anything
     * @param decision applies it to the reservation and the lot; answers whether the lot changed
     */
    private Result<VerificationOutcome, ApplicationError> decide(
            Long evidenceId, BiFunction<Reservation, PaymentEvidence, Optional<ApplicationError>> obstacle, Decision decision) {
        var reviewerId = externalIamService.currentUserId().orElse(null);
        if (reviewerId == null) {
            return Result.failure(new ApplicationError("UNAUTHORIZED", "The reviewer is not authenticated"));
        }
        var lotId = reservationRepository.findByEvidenceId(evidenceId).map(Reservation::getLotId).orElse(null);
        if (lotId == null) {
            return Result.failure(ApplicationError.notFound("payment_evidence", String.valueOf(evidenceId)));
        }
        var lot = lotRepository.findByIdForUpdate(lotId).orElseThrow();
        // Read under the lock: a substitute voucher or another decision may have changed it.
        var reservation = reservationRepository.findByEvidenceId(evidenceId).orElseThrow();
        var evidence = reservation.findEvidenceById(evidenceId).orElseThrow();
        if (!evidence.isPending()) {
            return Result.failure(ApplicationError.conflict("payment_evidence",
                    "payment evidence %d is already %s".formatted(evidenceId, evidence.getStatus())));
        }
        var forbidden = obstacle.apply(reservation, evidence);
        if (forbidden.isPresent()) {
            return Result.failure(forbidden.get());
        }
        // PostgreSQL keeps microseconds: the dates answered now must equal the ones read back later.
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var lotChanged = decision.apply(reservation, lot, reviewerId, now, evidence);
        var saved = reservationRepository.save(reservation);
        var savedLot = lotChanged ? lotRepository.save(lot) : lot;
        return Result.success(new VerificationOutcome(saved.findEvidenceById(evidenceId).orElseThrow(), saved,
                savedLot));
    }

    @FunctionalInterface
    private interface Decision {
        boolean apply(Reservation reservation, Lot lot, Long reviewerId, Instant now, PaymentEvidence evidence);
    }
}
