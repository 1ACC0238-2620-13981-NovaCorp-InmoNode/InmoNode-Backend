package com.novacorp.inmonode.inmonodebackend.quoting.domain.model.aggregates;

import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.entities.ScheduledInstallment;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.InitialPayment;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.LotSnapshot;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.services.FinancingSimulationService;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * A buyer's simulated financing of a lot (US-17): the projected schedule under the project's rules at that moment.
 * It keeps a copy of the price and the rate, so it stays as it was shown even if the project changes; it is valid
 * for a limited time, after which the buyer simulates again before requesting a separation.
 */
public class Quotation {

    /** Installments fall due on days in Lima, where the projects are sold. */
    static final ZoneId SALES_ZONE = ZoneId.of("America/Lima");

    private final @Nullable Long id;
    private final Long buyerId;
    private final Long lotId;
    private final Long projectId;
    private final String lotCode;
    private final Money lotPrice;
    private final Money initialPayment;
    private final int termMonths;
    private final BigDecimal annualInterestRate;
    private final List<ScheduledInstallment> installments;
    private final Instant generatedAt;
    private final Instant validUntil;

    private Quotation(@Nullable Long id, Long buyerId, Long lotId, Long projectId, String lotCode, Money lotPrice,
                      Money initialPayment, int termMonths, BigDecimal annualInterestRate,
                      List<ScheduledInstallment> installments, Instant generatedAt, Instant validUntil) {
        this.id = id;
        this.buyerId = buyerId;
        this.lotId = lotId;
        this.projectId = projectId;
        this.lotCode = lotCode;
        this.lotPrice = lotPrice;
        this.initialPayment = initialPayment;
        this.termMonths = termMonths;
        this.annualInterestRate = annualInterestRate;
        this.installments = List.copyOf(installments);
        this.generatedAt = generatedAt;
        this.validUntil = validUntil;
    }

    /**
     * Simulates the financing of the lot with its project's rules.
     *
     * @param validity how long the quotation can back a separation request
     * @throws IllegalArgumentException when the down payment or the term break the rules (US-17, Scenario 2)
     */
    public static Quotation simulate(Long buyerId, LotSnapshot lot, InitialPayment initialPayment, int termMonths,
                                     Instant now, Duration validity) {
        if (buyerId == null || lot == null || initialPayment == null || now == null || validity == null) {
            throw new IllegalArgumentException("a quotation needs its buyer, lot, down payment and date");
        }
        var rules = lot.rules();
        FinancingSimulationService.validate(lot.price(), initialPayment, termMonths, rules);
        var financed = lot.price().minus(initialPayment.amount());
        var schedule = FinancingSimulationService.frenchSchedule(financed, rules.annualInterestRate(), termMonths,
                LocalDate.ofInstant(now, SALES_ZONE));
        return new Quotation(null, buyerId, lot.lotId(), lot.projectId(), lot.code(), lot.price(),
                initialPayment.amount(), termMonths, rules.annualInterestRate(), schedule, now, now.plus(validity));
    }

    /** Rebuilds an already persisted quotation. */
    public static Quotation restore(Long id, Long buyerId, Long lotId, Long projectId, String lotCode, Money lotPrice,
                                    Money initialPayment, int termMonths, BigDecimal annualInterestRate,
                                    List<ScheduledInstallment> installments, Instant generatedAt,
                                    Instant validUntil) {
        return new Quotation(id, buyerId, lotId, projectId, lotCode, lotPrice, initialPayment, termMonths,
                annualInterestRate, installments, generatedAt, validUntil);
    }

    /** Whether it can still back a separation request. */
    public boolean isValid(Instant now) {
        return now.isBefore(validUntil);
    }

    public boolean belongsTo(Long buyerId) {
        return this.buyerId.equals(buyerId);
    }

    public Money financedAmount() {
        return lotPrice.minus(initialPayment);
    }

    /** The fixed installment; only the last one may differ by a few cents of rounding. */
    public Money monthlyInstallment() {
        return installments.getFirst().amount();
    }

    public Money totalInterest() {
        return installments.stream().map(ScheduledInstallment::interest)
                .reduce(new Money(BigDecimal.ZERO, lotPrice.currency()), Money::plus);
    }

    /** Down payment plus every installment. */
    public Money totalToPay() {
        return installments.stream().map(ScheduledInstallment::amount).reduce(initialPayment, Money::plus);
    }

    public BigDecimal initialPercentage() {
        return new InitialPayment(initialPayment).percentageOf(lotPrice);
    }

    public @Nullable Long getId() { return id; }
    public Long getBuyerId() { return buyerId; }
    public Long getLotId() { return lotId; }
    public Long getProjectId() { return projectId; }
    public String getLotCode() { return lotCode; }
    public Money getLotPrice() { return lotPrice; }
    public Money getInitialPayment() { return initialPayment; }
    public int getTermMonths() { return termMonths; }
    public BigDecimal getAnnualInterestRate() { return annualInterestRate; }
    public List<ScheduledInstallment> getInstallments() { return installments; }
    public Instant getGeneratedAt() { return generatedAt; }
    public Instant getValidUntil() { return validUntil; }
}
