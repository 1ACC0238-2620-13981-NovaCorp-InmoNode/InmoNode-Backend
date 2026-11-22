package com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries;

import com.novacorp.inmonode.inmonodebackend.shared.domain.model.valueobjects.PageRequest;

import java.util.Objects;

/**
 * The back office's verification queue: one page of the payment evidences still waiting for a decision (US-46).
 */
public record GetPendingVerificationsQuery(PageRequest pagination) {
    public GetPendingVerificationsQuery {
        Objects.requireNonNull(pagination, "pagination");
    }
}
