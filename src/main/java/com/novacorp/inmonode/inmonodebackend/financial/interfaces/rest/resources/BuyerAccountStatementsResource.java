package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Every lot of a buyer in one view (US-27). The portal filters {@code statements} to show a single lot (Scenario 2).
 *
 * @param totals     one per currency, since amounts of different currencies are never added together
 * @param statements oldest first
 */
public record BuyerAccountStatementsResource(List<TotalsResource> totals, List<StatementResource> statements) {

    /**
     * Scenario 1: the whole portfolio in one currency.
     *
     * @param invested           down payments and installments paid
     * @param debt               what is still owed, late fees included
     * @param progressPercentage share of everything owed that is already paid
     */
    public record TotalsResource(String currency, int lots, BigDecimal invested, BigDecimal debt,
                                 BigDecimal progressPercentage) {
    }

    /**
     * One lot; its full schedule is at {@code GET /api/v1/reservations/{transactionId}/account-statement}.
     *
     * @param paidAmount          down payment plus installments paid
     * @param balance             what is still owed, late fees included
     * @param overdueInstallments installments overdue, waiting with their late fee
     * @param nextInstallment     null once fully paid
     * @param dueSoon             the next installment falls due within 5 days or is already late
     */
    public record StatementResource(Long id, UUID transactionId, Long projectId, String projectName, Long lotId,
                                    String lotCode, String currency, BigDecimal lotPrice, BigDecimal totalAmount,
                                    BigDecimal paidAmount, BigDecimal balance, BigDecimal progressPercentage,
                                    boolean fullyPaid, int overdueInstallments, NextInstallmentResource nextInstallment,
                                    boolean dueSoon, Instant openedAt) {
    }

    /**
     * @param status    PENDING or OVERDUE
     * @param amountDue the installment plus its late fee
     */
    public record NextInstallmentResource(int number, LocalDate dueDate, BigDecimal amountDue, String status) {
    }
}
