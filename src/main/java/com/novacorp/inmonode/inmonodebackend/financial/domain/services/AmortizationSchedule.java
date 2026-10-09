package com.novacorp.inmonode.inmonodebackend.financial.domain.services;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.entities.Installment;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The real schedule of an account statement with the French amortization system (a fixed installment). It is the
 * same algorithm the quoting context uses to simulate, kept here on purpose: contexts share no domain code (Context
 * Map), and the agreed figures must come out the same. Stateless, so it is used without an instance.
 */
public final class AmortizationSchedule {

    private static final MathContext PRECISION = MathContext.DECIMAL128;


    private AmortizationSchedule() {}

    /**
     * Fixed installment {@code F·i / (1 − (1+i)^−n)}, with {@code i} the monthly rate; with no interest, {@code F / n}.
     * Every installment is rounded to the cent and the last one absorbs the rounding, so the principal adds up to the
     * financed amount exactly.
     *
     * @param financed             amount to finance, positive
     * @param annualRatePercentage annual rate as a percentage (12.5 means 12.5 %)
     * @param startDate            the first installment is due one month after it
     */
    public static List<Installment> french(BigDecimal financed, BigDecimal annualRatePercentage, int termMonths,
                                           LocalDate startDate) {
        if (financed == null || financed.signum() <= 0 || annualRatePercentage == null || termMonths < 1
                || termMonths > 360 || startDate == null) {
            throw new IllegalArgumentException("a schedule needs a positive financed amount, a rate and a term");
        }
        var monthlyRate = effectiveMonthlyRate(annualRatePercentage);
        var fixed = fixedInstallment(financed, monthlyRate, termMonths);
        var installments = new ArrayList<Installment>(termMonths);
        var balance = financed.setScale(2, RoundingMode.HALF_EVEN);
        for (int number = 1; number <= termMonths; number++) {
            var interest = balance.multiply(monthlyRate, PRECISION).setScale(2, RoundingMode.HALF_EVEN);
            var principal = number == termMonths ? balance : fixed.subtract(interest);
            balance = balance.subtract(principal);
            installments.add(Installment.scheduled(number, startDate.plusMonths(number), principal, interest));
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
