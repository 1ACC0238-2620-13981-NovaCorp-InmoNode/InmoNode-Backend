package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

import java.time.Instant;
import java.util.UUID;

/**
 * @param result RECEIVED when it was added now; DUPLICATE when the project already had this image
 */
public record ProjectImageRegistrationResource(Long projectId, UUID imageId, String contentType, long sizeBytes,
                                               Instant registeredAt, String result) {
}
