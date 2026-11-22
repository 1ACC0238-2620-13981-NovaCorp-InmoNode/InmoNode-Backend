package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import java.time.Instant;

/**
 * An image in the gallery of a project after a registration (US-57).
 *
 * @param result {@code RECEIVED} when it was added now; {@code DUPLICATE} when the project already had it
 */
public record ProjectImageRegistration(ProjectImage image, Instant registeredAt, Result result) {
    public enum Result { RECEIVED, DUPLICATE }
}
