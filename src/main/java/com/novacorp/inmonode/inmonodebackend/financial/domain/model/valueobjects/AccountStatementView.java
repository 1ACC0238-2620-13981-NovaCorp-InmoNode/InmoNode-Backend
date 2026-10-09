package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.AccountStatement;

/**
 * An account statement as its buyer sees it on a given day.
 *
 * @param dueSoon US-24, Scenario 1: the next installment falls due within {@value AccountStatement#DUE_SOON_DAYS}
 *                days or is already late
 */
public record AccountStatementView(AccountStatement statement, boolean dueSoon) {
}
