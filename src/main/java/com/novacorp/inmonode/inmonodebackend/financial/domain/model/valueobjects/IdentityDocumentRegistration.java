package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import java.time.Instant;

/**
 * The identity document photo a field reservation has after a registration (US-59).
 *
 * @param result {@code RECEIVED} when it was attached now; {@code DUPLICATE} when the reservation already had it
 */
public record IdentityDocumentRegistration(IdentityDocument document, Instant attachedAt, Result result) {

    public enum Result { RECEIVED, DUPLICATE }
}
