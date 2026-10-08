package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.entities.PaymentEvidence;

/**
 * One entry of the verification queue: a pending evidence with the reservation and the lot it pays for.
 */
public record PendingVerification(PaymentEvidence evidence, Reservation reservation, Lot lot) {
}
