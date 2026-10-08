package com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries;

/**
 * Catalog a field agent downloads at the start of the day to work offline (US-02): every published project
 * with its financing rules and lots. Assigning projects to agents (US-54) will narrow it later.
 */
public record GetFieldPortfolioQuery() {
}
