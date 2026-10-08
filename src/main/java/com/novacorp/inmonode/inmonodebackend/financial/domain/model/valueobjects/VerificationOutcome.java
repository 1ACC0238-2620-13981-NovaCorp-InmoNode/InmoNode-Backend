package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.entities.PaymentEvidence;

/**
 * What a decision of the back office left: the decided evidence, its reservation and the lot.
 */
public record VerificationOutcome(PaymentEvidence evidence, Reservation reservation, Lot lot) {
}
