package com.novacorp.inmonode.inmonodebackend.quoting.domain.services;

import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.entities.ScheduledInstallment;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.FinancingRules;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.InitialPayment;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.Money;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Domain service of 2.6.3.1: validates a simulation against the project's rules and projects its schedule with the
 * French amortization system (a fixed installment). Stateless, so it is used without an instance.
 */
public final class FinancingSimulationService {

    private static final MathContext PRECISION = MathContext.DECIMAL128;


    private FinancingSimulationService() {}

    /**
     * US-17, Scenario 2, and the limits of the rules.
     *
     * @throws IllegalArgumentException when the down payment is under the minimum (naming that minimum), covers the
     *                                  whole price, or the term is outside 1 to the maximum
     */
    public static void validate(Money price, InitialPayment initialPayment, int termMonths, FinancingRules rules) {
        if (!initialPayment.amount().currency().equals(price.currency())) {
            throw new IllegalArgumentException("initialPayment must be in " + price.currency());
        }
        if (!initialPayment.meetsMinimum(price, rules)) {
            var minimum = InitialPayment.minimumFor(price, rules);
            throw new IllegalArgumentException("initialPayment must be at least %s %s (%s %% of the price %s)"
                    .formatted(minimum.amount().toPlainString(), minimum.currency(),
                            rules.minimumInitialPercentage().stripTrailingZeros().toPlainString(),
                            price.amount().toPlainString()));
        }
        if (!initialPayment.amount().isLessThan(price)) {
            throw new IllegalArgumentException("initialPayment must be less than the price %s; nothing would be financed"
                    .formatted(price.amount().toPlainString()));
        }
        if (termMonths < 1 || termMonths > rules.maxTermMonths()) {
            throw new IllegalArgumentException(
                    "termMonths must be between 1 and %d".formatted(rules.maxTermMonths()));
        }
    }

    /**
     * Fixed installment {@code F·i / (1 − (1+i)^−n)}, with {@code i} the monthly rate; with no interest, {@code F / n}.
     * Every installment is rounded to the cent and the last one absorbs the rounding, so the principal adds up to the
     * financed amount exactly.
     *
     * @param annualRatePercentage annual rate as a percentage (12.5 means 12.5 %)
     * @param startDate            the first installment is due one month after it
     */
    public static List<ScheduledInstallment> frenchSchedule(Money financed, BigDecimal annualRatePercentage,
                                                            int termMonths, LocalDate startDate) {
        if (termMonths < 1 || termMonths > 360 || startDate == null || financed.amount().signum() <= 0) {
            throw new IllegalArgumentException("a schedule needs a positive amount, a date and a term from 1 to 360");
        }
        var currency = financed.currency();
        var monthlyRate = effectiveMonthlyRate(annualRatePercentage);
        var fixedInstallment = fixedInstallment(financed.amount(), monthlyRate, termMonths);
        var installments = new ArrayList<ScheduledInstallment>(termMonths);
        var balance = financed.amount();
        for (int number = 1; number <= termMonths; number++) {
            var interest = balance.multiply(monthlyRate, PRECISION).setScale(2, RoundingMode.HALF_EVEN);
            var principal = number == termMonths ? balance : fixedInstallment.subtract(interest);
            balance = balance.subtract(principal);
            installments.add(new ScheduledInstallment(number, startDate.plusMonths(number),
                    new Money(principal.add(interest), currency), new Money(principal, currency),
                    new Money(interest, currency), new Money(balance, currency)));
        }
        return List.copyOf(installments);
    }

    /** Converts TEA to an effective monthly rate with decimal Newton iteration: (1 + TEA/100)^(1/12) - 1. */
    static BigDecimal effectiveMonthlyRate(BigDecimal annualRatePercentage) {
        if (annualRatePercentage == null || annualRatePercentage.signum() < 0
                || annualRatePercentage.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("effective annual rate must be between 0 and 100");
        }
        var annualGrowth = BigDecimal.ONE.add(annualRatePercentage.movePointLeft(2), PRECISION);
        var root = BigDecimal.ONE;
        for (int iteration = 0; iteration < 80; iteration++) {
            var next = root.multiply(BigDecimal.valueOf(11), PRECISION)
                    .add(annualGrowth.divide(root.pow(11, PRECISION), PRECISION), PRECISION)
                    .divide(BigDecimal.valueOf(12), PRECISION);
            if (next.subtract(root).abs().compareTo(new BigDecimal("1E-30")) < 0) {
                return next.subtract(BigDecimal.ONE, PRECISION);
            }
            root = next;
        }
        throw new IllegalStateException("effective monthly rate did not converge");
    }

    private static BigDecimal fixedInstallment(BigDecimal financed, BigDecimal monthlyRate, int termMonths) {
        if (monthlyRate.signum() == 0) {
            return financed.divide(BigDecimal.valueOf(termMonths), 2, RoundingMode.HALF_EVEN);
        }
        var growth = BigDecimal.ONE.add(monthlyRate).pow(termMonths, PRECISION);
        return financed.multiply(monthlyRate, PRECISION).multiply(growth, PRECISION)
                .divide(growth.subtract(BigDecimal.ONE), PRECISION)
                .setScale(2, RoundingMode.HALF_EVEN);
    }
}
