package com.novacorp.inmonode.inmonodebackend.iam.domain.model.events;

import java.time.Instant;

/**
 * Raised when consecutive failed sign-in attempts lock the account temporarily (US-01, Scenario 2).
 *
 * @param email       the locked account email
 * @param lockedUntil instant at which the lock expires
 */
public record AccountLockedEvent(String email, Instant lockedUntil) {
}
