package com.novacorp.inmonode.inmonodebackend.financial.domain.services;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.entities.Installment;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.InstallmentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Same reference figures as the quoting simulation: the agreed schedule must match what the buyer was quoted.
 */
class AmortizationScheduleTest {

    private static final LocalDate START = LocalDate.parse("2026-10-08");

    @Test
    void theInstallmentIsFixedAndThePrincipalAddsUpToTheFinancedAmount() {
        var schedule = AmortizationSchedule.french(new BigDecimal("36000"), new BigDecimal("12"), 12, START);

        assertEquals(12, schedule.size());
        var first = schedule.getFirst();
        assertEquals(new BigDecimal("3198.56"), first.getAmount());
        assertEquals(new BigDecimal("360.00"), first.getInterest());
        assertEquals(new BigDecimal("2838.56"), first.getPrincipal());
        schedule.subList(0, 11).forEach(installment ->
                assertEquals(new BigDecimal("3198.56"), installment.getAmount()));
        assertEquals(new BigDecimal("3198.50"), schedule.getLast().getAmount(),
                "the last installment absorbs the rounding");
        assertEquals(new BigDecimal("36000.00"), sum(schedule, Installment::getPrincipal));
        assertEquals(new BigDecimal("2382.66"), sum(schedule, Installment::getInterest));
    }

    @Test
    void everyInstallmentStartsPendingWithoutLateFee() {
        var schedule = AmortizationSchedule.french(new BigDecimal("36000"), new BigDecimal("12"), 3, START);

        schedule.forEach(installment -> {
            assertNull(installment.getId());
            assertEquals(InstallmentStatus.PENDING, installment.getStatus());
            assertEquals(new BigDecimal("0.00"), installment.getPenalty());
            assertEquals(installment.getAmount(), installment.amountDue());
            assertFalse(installment.isPaid());
            assertNull(installment.getPaidAt());
            assertNull(installment.getPaidAmount());
            assertNull(installment.getReminderSentAt());
            assertNull(installment.getOverdueNotifiedAt());
        });
    }

    @Test
    void withNoInterestTheBalanceIsSplitEvenly() {
        var schedule = AmortizationSchedule.french(new BigDecimal("10000"), BigDecimal.ZERO, 3, START);

        assertEquals(new BigDecimal("3333.33"), schedule.get(0).getAmount());
        assertEquals(new BigDecimal("3333.33"), schedule.get(1).getAmount());
        assertEquals(new BigDecimal("3333.34"), schedule.get(2).getAmount());
        assertEquals(new BigDecimal("0.00"), sum(schedule, Installment::getInterest));
    }

    @Test
    void installmentsFallDueMonthlyFromTheMonthAfterTheStart() {
        var schedule = AmortizationSchedule.french(new BigDecimal("36000"), new BigDecimal("12"), 3,
                LocalDate.parse("2026-01-31"));

        assertEquals(LocalDate.parse("2026-02-28"), schedule.get(0).getDueDate());
        assertEquals(LocalDate.parse("2026-03-31"), schedule.get(1).getDueDate());
        assertEquals(LocalDate.parse("2026-04-30"), schedule.get(2).getDueDate());
        assertEquals(List.of(1, 2, 3), schedule.stream().map(Installment::getNumber).toList());
    }

    @Test
    void aSingleInstallmentPaysTheWholeBalanceWithItsInterest() {
        var schedule = AmortizationSchedule.french(new BigDecimal("36000"), new BigDecimal("12"), 1, START);

        assertEquals(1, schedule.size());
        assertEquals(new BigDecimal("36360.00"), schedule.getFirst().getAmount());
        assertEquals(new BigDecimal("36000.00"), schedule.getFirst().getPrincipal());
    }

    @Test
    void itNeedsAPositiveAmountAndATerm() {
        assertThrows(IllegalArgumentException.class,
                () -> AmortizationSchedule.french(BigDecimal.ZERO, new BigDecimal("12"), 12, START));
        assertThrows(IllegalArgumentException.class,
                () -> AmortizationSchedule.french(new BigDecimal("36000"), new BigDecimal("12"), 0, START));
        assertThrows(IllegalArgumentException.class,
                () -> AmortizationSchedule.french(new BigDecimal("36000"), null, 12, START));
    }

    private static BigDecimal sum(List<Installment> schedule, Function<Installment, BigDecimal> amount) {
        return schedule.stream().map(amount).reduce(new BigDecimal("0.00"), BigDecimal::add);
    }
}
