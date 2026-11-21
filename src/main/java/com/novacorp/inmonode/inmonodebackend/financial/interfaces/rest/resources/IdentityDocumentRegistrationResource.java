package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

import java.time.Instant;
import java.util.UUID;

/**
 * @param result RECEIVED when it was attached now; DUPLICATE when the reservation already had this photo
 */
public record IdentityDocumentRegistrationResource(UUID transactionId, UUID documentId, String contentType,
                                                   long sizeBytes, Instant attachedAt, String result) {
}
