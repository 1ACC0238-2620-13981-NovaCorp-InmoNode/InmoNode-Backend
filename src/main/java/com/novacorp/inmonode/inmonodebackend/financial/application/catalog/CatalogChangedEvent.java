package com.novacorp.inmonode.inmonodebackend.financial.application.catalog;

import java.time.Instant;

/** Immutable snapshot emitted inside the write transaction and delivered only after commit. */
public record CatalogChangedEvent(Long projectId, Long lotId, String status, Instant changedAt) {}
