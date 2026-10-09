package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The account statement of a lot, for its buyer (US-23). Amounts are in {@code currency}.
 *
 * @param totalAmount        the down payment, every installment and the late fees incurred
 * @param paidAmount         the down payment plus every installment paid
 * @param balance            what is still owed, late fees included
 * @param progressPercentage share of the total already paid
 * @param fullyPaid          every installment is paid: the lot is the buyer's
 * @param nextInstallment    the first installment not paid yet; null once fully paid
 * @param dueSoon            US-24: the next installment falls due within 5 days or is already late
 */
public record AccountStatementResource(Long id, UUID transactionId, Long lotId, String currency,
                                       BigDecimal lotPrice, BigDecimal initialPayment, BigDecimal financedAmount,
                                       int termMonths, BigDecimal annualInterestRate, BigDecimal totalAmount,
                                       BigDecimal paidAmount, BigDecimal balance, BigDecimal progressPercentage,
                                       boolean fullyPaid, Instant openedAt, InstallmentResource nextInstallment,
                                       boolean dueSoon, List<InstallmentResource> installments) {

    /**
     * @param status    PENDING, PAID or OVERDUE
     * @param penalty   late fee charged when it fell overdue
     * @param amountDue what pays it: the amount plus the penalty
     */
    public record InstallmentResource(int number, LocalDate dueDate, BigDecimal amount, BigDecimal principal,
                                      BigDecimal interest, BigDecimal penalty, BigDecimal amountDue, String status,
                                      Instant paidAt, BigDecimal paidAmount) {
    }
}
