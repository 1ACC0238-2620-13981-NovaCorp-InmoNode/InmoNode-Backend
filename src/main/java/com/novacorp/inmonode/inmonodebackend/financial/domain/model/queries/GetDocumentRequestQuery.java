package com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries;

import java.util.Objects;
import java.util.UUID;

/** Where a background PDF stands, for whoever asked for it (US-45). */
public record GetDocumentRequestQuery(UUID requestId) {

    public GetDocumentRequestQuery {
        Objects.requireNonNull(requestId, "requestId");
    }
}
