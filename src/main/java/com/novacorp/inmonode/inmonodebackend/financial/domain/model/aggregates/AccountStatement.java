package com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.entities.Installment;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.InstallmentStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.AmortizationSchedule;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Account statement of a buyer for a lot (2.6.4.1, US-23): the real schedule of installments that pays the financed
 * balance, opened when the contract is issued. It refers to its {@link Contract} and its
 * {@link Reservation} by id and keeps the figures it was opened with, so it stays as agreed even if the project
 * changes. Amounts are in {@code currency}, at two decimals.
 */
public class AccountStatement {

    /** Installments fall due on days in Lima, where the projects are sold. */
    public static final ZoneId SALES_ZONE = ZoneId.of("America/Lima");

    /** US-24: an installment due within these days is announced to the buyer. */
    public static final int DUE_SOON_DAYS = 5;

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final @Nullable Long id;
    private final Long contractId;
    private final Long reservationId;
    private final UUID transactionId;
    private final Long buyerId;
    private final Long lotId;
    private final String currency;
    private final BigDecimal lotPrice;
    private final BigDecimal initialPayment;
    private final int termMonths;
    private final BigDecimal annualInterestRate;
    private final Instant openedAt;
    private final List<Installment> installments;

    private AccountStatement(@Nullable Long id, Long contractId, Long reservationId, UUID transactionId, Long buyerId,
                             Long lotId, String currency, BigDecimal lotPrice, BigDecimal initialPayment,
                             int termMonths, BigDecimal annualInterestRate, Instant openedAt,
                             List<Installment> installments) {
        this.id = id;
        this.contractId = contractId;
        this.reservationId = reservationId;
        this.transactionId = transactionId;
        this.buyerId = buyerId;
        this.lotId = lotId;
        this.currency = currency;
        this.lotPrice = lotPrice;
        this.initialPayment = initialPayment;
        this.termMonths = termMonths;
        this.annualInterestRate = annualInterestRate;
        this.openedAt = openedAt;
        this.installments = installments.stream().sorted(Comparator.comparingInt(Installment::getNumber)).toList();
    }

    /**
     * Opens the statement of an issued contract, with the financing plan of its reservation; the first
     * installment falls due one month after the day (in Lima) it is opened.
     *
     * @throws IllegalArgumentException when the contract is not of this reservation or the reservation has no plan
     */
    public static AccountStatement open(Contract contract, Reservation reservation, Instant now) {
        var plan = reservation.getFinancingPlan();
        if (plan == null || contract.getId() == null || reservation.getId() == null
                || !contract.getReservationId().equals(reservation.getId())) {
            throw new IllegalArgumentException("an account statement needs a saved contract of a reservation with a plan");
        }
        var initial = reservation.getInitialAmount();
        var financed = plan.lotPrice().amount().subtract(initial.amount());
        var schedule = AmortizationSchedule.french(financed, plan.annualInterestRate(), plan.termMonths(),
                LocalDate.ofInstant(now, SALES_ZONE));
        return open(contract, reservation, now, schedule);
    }

    /** Uses the accepted quotation amounts and rebased dates instead of recalculating persisted commercial terms. */
    public static AccountStatement open(Contract contract, Reservation reservation, Instant now, List<Installment> schedule) {
        var plan = reservation.getFinancingPlan();
        if (plan == null || contract.getId() == null || !contract.getReservationId().equals(reservation.getId())
                || schedule.size() != plan.termMonths()) throw new IllegalArgumentException("invalid agreed schedule");
        var principal = schedule.stream().map(Installment::getPrincipal).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (principal.compareTo(plan.lotPrice().amount().subtract(reservation.getInitialAmount().amount())) != 0) {
            throw new IllegalArgumentException("schedule principal does not match agreed financing");
        }
        return new AccountStatement(null, contract.getId(), reservation.getId(), contract.getTransactionId(),
                contract.getBuyerId(), contract.getLotId(), plan.lotPrice().currency(), plan.lotPrice().amount(),
                reservation.getInitialAmount().amount(), plan.termMonths(), plan.annualInterestRate(), now, schedule);
    }

    /** Rebuilds an already persisted statement. */
    public static AccountStatement restore(Long id, Long contractId, Long reservationId, UUID transactionId,
                                           Long buyerId, Long lotId, String currency, BigDecimal lotPrice,
                                           BigDecimal initialPayment, int termMonths, BigDecimal annualInterestRate,
                                           Instant openedAt, List<Installment> installments) {
        return new AccountStatement(id, contractId, reservationId, transactionId, buyerId, lotId, currency, lotPrice,
                initialPayment, termMonths, annualInterestRate, openedAt, installments);
    }

    public BigDecimal financedAmount() {
        return lotPrice.subtract(initialPayment);
    }

    /** Scheduled installments (principal plus interest), excluding the already verified initial payment and penalties. */
    public BigDecimal totalAmount() {
        return installments.stream().map(Installment::getAmount).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    /** US-23: amounts paid toward scheduled installments, excluding late fees. */
    public BigDecimal paidAmount() {
        return installments.stream().filter(Installment::isPaid).map(Installment::getAmount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    /** US-23: what is still owed, late fees included. */
    public BigDecimal balance() {
        return installments.stream()
                .filter(installment -> !installment.isPaid())
                .map(Installment::amountDue)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    /** US-23: the share of the total already paid, as a percentage with two decimals. */
    public BigDecimal progressPercentage() {
        var paid = paidAmount();
        var total = totalAmount();
        if (total.signum() == 0) return BigDecimal.valueOf(100).setScale(2);
        return paid.multiply(ONE_HUNDRED).divide(total, 2, RoundingMode.HALF_UP);
    }

    /** US-27: how many installments are overdue, waiting with their late fee. */
    public int overdueInstallmentCount() {
        return (int) installments.stream().filter(installment -> installment.getStatus() == InstallmentStatus.OVERDUE)
                .count();
    }

    public boolean isFullyPaid() {
        return balance().signum() == 0;
    }

    public Optional<Installment> findInstallment(int number) {
        return installments.stream().filter(installment -> installment.getNumber() == number).findFirst();
    }

    /**
     * US-23: the back office records the payment of an installment, in any order, with exactly what is due (its
     * amount plus the late fee). Once the last one is paid the statement {@linkplain #isFullyPaid() is paid off}.
     *
     * @throws IllegalArgumentException when the installment does not exist or the amount is not exactly what is due
     * @throws IllegalStateException    when the installment is already paid
     */
    public void registerInstallmentPayment(int number, BigDecimal paid, Instant paidAt) {
        existingInstallment(number).registerPayment(paid, paidAt);
    }

    /**
     * US-24, Scenario 2: every pending installment whose due date has passed by {@code asOfDate} falls overdue with
     * its late fee of {@code lateFeeRate} % of its amount.
     *
     * @return the installments that have just fallen overdue
     */
    public List<Installment> markOverdueInstallments(LocalDate asOfDate, BigDecimal lateFeeRate) {
        var overdue = new ArrayList<Installment>();
        for (var installment : installments) {
            if (installment.markOverdue(asOfDate, lateFeeRate)) {
                overdue.add(installment);
            }
        }
        return List.copyOf(overdue);
    }

    /** US-24, Scenario 1: the pending installments due within {@value #DUE_SOON_DAYS} days, not reminded yet. */
    public List<Installment> installmentsToRemind(LocalDate asOfDate) {
        return installments.stream()
                .filter(installment -> installment.needsReminder(asOfDate, DUE_SOON_DAYS))
                .toList();
    }

    /** US-24, Scenario 2: the overdue installments whose buyer was not told yet. */
    public List<Installment> overdueInstallmentsToNotify() {
        return installments.stream().filter(Installment::needsOverdueNotice).toList();
    }

    /** US-24: the buyer was reminded of the installment, so it is not reminded again. */
    public void recordReminder(int number, Instant sentAt) {
        existingInstallment(number).recordReminder(sentAt);
    }

    /** US-24: the buyer was told the installment is overdue, so they are not told again. */
    public void recordOverdueNotice(int number, Instant sentAt) {
        existingInstallment(number).recordOverdueNotice(sentAt);
    }

    private Installment existingInstallment(int number) {
        return findInstallment(number)
                .orElseThrow(() -> new IllegalArgumentException("installment %d does not exist".formatted(number)));
    }

    /** The first installment not paid yet, by number. */
    public Optional<Installment> nextInstallment() {
        return installments.stream().filter(installment -> !installment.isPaid()).findFirst();
    }

    /** US-24, Scenario 1: whether the next installment falls due within {@value #DUE_SOON_DAYS} days (or is late). */
    public boolean isDueSoon(LocalDate today) {
        return nextInstallment()
                .filter(next -> !next.getDueDate().isAfter(today.plusDays(DUE_SOON_DAYS)))
                .isPresent();
    }

    public boolean belongsTo(Long userId) {
        return buyerId.equals(userId);
    }

    public @Nullable Long getId() { return id; }
    public Long getContractId() { return contractId; }
    public Long getReservationId() { return reservationId; }
    public UUID getTransactionId() { return transactionId; }
    public Long getBuyerId() { return buyerId; }
    public Long getLotId() { return lotId; }
    public String getCurrency() { return currency; }
    public BigDecimal getLotPrice() { return lotPrice; }
    public BigDecimal getInitialPayment() { return initialPayment; }
    public int getTermMonths() { return termMonths; }
    public BigDecimal getAnnualInterestRate() { return annualInterestRate; }
    public Instant getOpenedAt() { return openedAt; }
    public List<Installment> getInstallments() { return installments; }
}
