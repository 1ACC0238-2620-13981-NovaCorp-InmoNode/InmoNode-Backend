package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

import java.time.Instant;
import java.util.UUID;

/**
 * What a decision left.
 *
 * @param evidenceStatus    APPROVED or REJECTED
 * @param reviewerNote      the note of the reviewer; the reason of a rejection
 * @param reservationStatus VERIFIED after an approval; BLOCKED when a rejection reopened the wait for a substitute
 * @param lotStatus         RESERVED after an approval; BLOCKED while a substitute is awaited
 * @param blockedUntil      until when the lot waits for the substitute, when it does
 */
public record VerificationDecisionResource(Long evidenceId, String evidenceStatus, String reviewerNote,
                                           Instant reviewedAt, Long reservationId, UUID transactionId,
                                           String reservationStatus, Long lotId, String lotStatus,
                                           Instant blockedUntil) {
}
