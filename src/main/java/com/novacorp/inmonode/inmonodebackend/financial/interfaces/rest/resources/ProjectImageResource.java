package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

import java.time.Instant;
import java.util.UUID;

/**
 * A photo or render in the gallery of a project (US-57).
 *
 * @param url       the file; it works until {@code expiresAt}, then read the project again for a new link
 * @param expiresAt when the link stops working
 */
public record ProjectImageResource(UUID imageId, String contentType, String url, Instant expiresAt) {
}
