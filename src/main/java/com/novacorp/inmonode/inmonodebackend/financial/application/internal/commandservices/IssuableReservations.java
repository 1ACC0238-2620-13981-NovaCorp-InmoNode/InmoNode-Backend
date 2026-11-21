package com.novacorp.inmonode.inmonodebackend.financial.application.internal.commandservices;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Contract;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ContractRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ReservationRepository;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;

import java.util.Objects;
import java.util.UUID;

/**
 * The reservations that can get their contract now (US-21): verified web reservations without one yet. Uploading a
 * contract by hand and generating it (US-45) apply the same rule.
 */
final class IssuableReservations {

    private IssuableReservations() {
    }

    /**
     * The reservation, or {@code RESERVATION_NOT_FOUND}, {@code BUSINESS_RULE_VIOLATION} when it is not a verified
     * web reservation, or {@code CONTRACT_CONFLICT} when its contract was already issued.
     */
    static Result<Reservation, ApplicationError> find(ReservationRepository reservations, ContractRepository contracts,
                                                      UUID transactionId) {
        var reservation = reservations.findBySourceEventId(transactionId).orElse(null);
        if (reservation == null) {
            return Result.failure(ApplicationError.notFound("reservation", transactionId.toString()));
        }
        var obstacle = Contract.issuingObstacle(reservation);
        if (obstacle.isPresent()) {
            return Result.failure(ApplicationError.businessRuleViolation("contract-issuing", obstacle.get()));
        }
        if (contracts.findByReservationId(Objects.requireNonNull(reservation.getId())).isPresent()) {
            return Result.failure(ApplicationError.conflict("contract",
                    "the contract of reservation %s was already issued".formatted(transactionId)));
        }
        return Result.success(reservation);
    }
}
