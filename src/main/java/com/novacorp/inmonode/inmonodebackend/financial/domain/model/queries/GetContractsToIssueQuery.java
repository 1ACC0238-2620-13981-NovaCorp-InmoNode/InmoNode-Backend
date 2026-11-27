package com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries;

import com.novacorp.inmonode.inmonodebackend.shared.domain.model.valueobjects.PageRequest;

import java.util.Objects;

/**
 * The back office's contract queue: one page of the reservations that can get their contract now (US-21), so finance
 * knows which ones to issue without having their transaction ids at hand.
 */
public record GetContractsToIssueQuery(PageRequest pagination) {
    public GetContractsToIssueQuery {
        Objects.requireNonNull(pagination, "pagination");
    }
}
