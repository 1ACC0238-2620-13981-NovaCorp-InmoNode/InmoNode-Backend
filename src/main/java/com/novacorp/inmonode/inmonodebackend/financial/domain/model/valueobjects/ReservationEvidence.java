package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.entities.PaymentEvidence;

import java.util.Objects;

/**
 * A payment evidence together with the reservation it belongs to, as a page of evidences lists them (US-46).
 */
public record ReservationEvidence(Reservation reservation, PaymentEvidence evidence) {
    public ReservationEvidence {
        Objects.requireNonNull(reservation, "reservation");
        Objects.requireNonNull(evidence, "evidence");
    }
}
