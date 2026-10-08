package com.novacorp.inmonode.inmonodebackend.financial.domain.model.entities;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.InstallmentStatus;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One real installment of an account statement (2.6.4.1); it lives inside its {@code AccountStatement}, identified
 * there by its number. Amounts are in the currency of the statement, at two decimals; they can be zero (the last
 * balance, no interest, no fee), which is why they are plain amounts and not {@code Money}.
 */
public class Installment {

    private final @Nullable Long id;
    private final int number;
    private final LocalDate dueDate;
    private final BigDecimal amount;
    private final BigDecimal principal;
    private final BigDecimal interest;
    private InstallmentStatus status;
    private @Nullable Instant paidAt;
    private @Nullable BigDecimal paidAmount;
    private BigDecimal penalty;
    private @Nullable Instant reminderSentAt;
    private @Nullable Instant overdueNotifiedAt;

    private Installment(@Nullable Long id, int number, LocalDate dueDate, BigDecimal amount, BigDecimal principal,
                        BigDecimal interest, InstallmentStatus status, @Nullable Instant paidAt,
                        @Nullable BigDecimal paidAmount, BigDecimal penalty, @Nullable Instant reminderSentAt,
                        @Nullable Instant overdueNotifiedAt) {
        this.id = id;
        this.number = number;
        this.dueDate = dueDate;
        this.amount = amount;
        this.principal = principal;
        this.interest = interest;
        this.status = status;
        this.paidAt = paidAt;
        this.paidAmount = paidAmount;
        this.penalty = penalty;
        this.reminderSentAt = reminderSentAt;
        this.overdueNotifiedAt = overdueNotifiedAt;
    }

    /** A new installment of the schedule, not paid and not due. */
    public static Installment scheduled(int number, LocalDate dueDate, BigDecimal principal, BigDecimal interest) {
        if (number < 1 || dueDate == null || principal == null || interest == null
                || principal.signum() < 0 || interest.signum() < 0) {
            throw new IllegalArgumentException("an installment needs its number, due date and amounts");
        }
        var scaledPrincipal = principal.setScale(2, RoundingMode.HALF_UP);
        var scaledInterest = interest.setScale(2, RoundingMode.HALF_UP);
        return new Installment(null, number, dueDate, scaledPrincipal.add(scaledInterest), scaledPrincipal,
                scaledInterest, InstallmentStatus.PENDING, null, null, BigDecimal.ZERO.setScale(2), null, null);
    }

    /** Rebuilds an already persisted installment. */
    public static Installment restore(Long id, int number, LocalDate dueDate, BigDecimal amount, BigDecimal principal,
                                      BigDecimal interest, InstallmentStatus status, @Nullable Instant paidAt,
                                      @Nullable BigDecimal paidAmount, BigDecimal penalty,
                                      @Nullable Instant reminderSentAt, @Nullable Instant overdueNotifiedAt) {
        return new Installment(id, number, dueDate, amount, principal, interest, status, paidAt, paidAmount, penalty,
                reminderSentAt, overdueNotifiedAt);
    }

    /** What has to be paid to settle it: its amount plus the late fee, if any. */
    public BigDecimal amountDue() {
        return amount.add(penalty);
    }

    public boolean isPaid() {
        return status == InstallmentStatus.PAID;
    }

    /** Whether {@code paid} is exactly what settles it: an installment is paid whole, with its late fee. */
    public boolean isSettledBy(BigDecimal paid) {
        return paid != null && paid.compareTo(amountDue()) == 0;
    }

    /**
     * Records that it was paid, pending or overdue alike; only its {@code AccountStatement} calls it.
     *
     * @throws IllegalStateException    when it is already paid
     * @throws IllegalArgumentException when the amount is not exactly what is due
     */
    public void registerPayment(BigDecimal paid, Instant at) {
        if (isPaid()) {
            throw new IllegalStateException("installment %d is already paid".formatted(number));
        }
        if (!isSettledBy(paid) || at == null) {
            throw new IllegalArgumentException("installment %d is settled with exactly %s"
                    .formatted(number, amountDue()));
        }
        status = InstallmentStatus.PAID;
        paidAt = at;
        paidAmount = paid.setScale(2, RoundingMode.HALF_UP);
    }

    public @Nullable Long getId() { return id; }
    public int getNumber() { return number; }
    public LocalDate getDueDate() { return dueDate; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getPrincipal() { return principal; }
    public BigDecimal getInterest() { return interest; }
    public InstallmentStatus getStatus() { return status; }
    public @Nullable Instant getPaidAt() { return paidAt; }
    public @Nullable BigDecimal getPaidAmount() { return paidAmount; }
    public BigDecimal getPenalty() { return penalty; }
    public @Nullable Instant getReminderSentAt() { return reminderSentAt; }
    public @Nullable Instant getOverdueNotifiedAt() { return overdueNotifiedAt; }
}
