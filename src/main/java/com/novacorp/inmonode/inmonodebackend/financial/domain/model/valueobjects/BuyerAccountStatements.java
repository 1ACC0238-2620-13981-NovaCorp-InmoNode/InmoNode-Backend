package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.AccountStatement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Every account statement of a buyer in one view (US-27): the totals of all their lots and the detail of each one.
 * Amounts of different currencies are never added together, so there is one total per currency.
 *
 * @param statements oldest first
 */
public record BuyerAccountStatements(List<Entry> statements) {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    public BuyerAccountStatements {
        statements = List.copyOf(statements);
    }

    /** Scenario 1: what was invested, what is owed and the progress of all the lots, per currency. */
    public List<Totals> totals() {
        var byCurrency = new LinkedHashMap<String, Totals>();
        for (var entry : statements) {
            var statement = entry.statement();
            byCurrency.merge(statement.getCurrency(),
                    new Totals(statement.getCurrency(), 1, statement.paidAmount(), statement.balance()),
                    Totals::plus);
        }
        return List.copyOf(byCurrency.values());
    }

    /**
     * One lot of the buyer.
     *
     * @param dueSoon its next installment falls due within 5 days or is already late
     */
    public record Entry(AccountStatement statement, Long projectId, String projectName, String lotCode,
                        boolean dueSoon) {
    }

    /**
     * @param lots     how many lots are paid in this currency
     * @param invested what was paid: down payments and installments
     * @param debt     what is still owed, late fees included
     */
    public record Totals(String currency, int lots, BigDecimal invested, BigDecimal debt) {

        /** The share of everything owed that is already paid, as a percentage with two decimals. */
        public BigDecimal progressPercentage() {
            return invested.multiply(ONE_HUNDRED).divide(invested.add(debt), 2, RoundingMode.HALF_UP);
        }

        Totals plus(Totals other) {
            return new Totals(currency, lots + other.lots, invested.add(other.invested), debt.add(other.debt));
        }
    }
}
