package com.novacorp.inmonode.inmonodebackend.quoting.domain.services;

import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.entities.ScheduledInstallment;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.FinancingRules;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.InitialPayment;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class FinancingSimulationServiceTest {

    private static final LocalDate START = LocalDate.parse("2026-10-08");
    private static final Money PRICE = pen("45000");
    private static final FinancingRules RULES = new FinancingRules(new BigDecimal("20"), new BigDecimal("12"), 120);

    @Test
    void theInstallmentIsFixedAndThePrincipalAddsUpToTheFinancedAmount() {
        var schedule = FinancingSimulationService.frenchSchedule(pen("36000"), new BigDecimal("12"), 12, START);

        assertEquals(12, schedule.size());
        var first = schedule.getFirst();
        assertEquals(pen("3188.23"), first.amount());
        assertEquals(pen("341.60"), first.interest());
        assertEquals(pen("2846.63"), first.principal());
        assertEquals(pen("33153.37"), first.balance());
        schedule.subList(0, 11).forEach(installment -> assertEquals(pen("3188.23"), installment.amount()));
        var last = schedule.getLast();
        assertEquals(pen("3188.28"), last.amount(), "the last installment absorbs the rounding");
        assertEquals(pen("0.00"), last.balance());
        assertEquals(pen("36000.00"), sum(schedule, ScheduledInstallment::principal));
        assertEquals(pen("2258.81"), sum(schedule, ScheduledInstallment::interest));
    }

    @Test
    void withNoInterestTheBalanceIsSplitEvenly() {
        var schedule = FinancingSimulationService.frenchSchedule(pen("10000"), BigDecimal.ZERO, 3, START);

        assertEquals(pen("3333.33"), schedule.get(0).amount());
        assertEquals(pen("3333.33"), schedule.get(1).amount());
        assertEquals(pen("3333.34"), schedule.get(2).amount());
        assertEquals(pen("0.00"), sum(schedule, ScheduledInstallment::interest));
        assertEquals(pen("10000.00"), sum(schedule, ScheduledInstallment::principal));
    }

    @Test
    void installmentsFallDueMonthlyFromTheMonthAfterTheSimulation() {
        var schedule = FinancingSimulationService.frenchSchedule(pen("36000"), new BigDecimal("12"), 3,
                LocalDate.parse("2026-01-31"));

        assertEquals(LocalDate.parse("2026-02-28"), schedule.get(0).dueDate());
        assertEquals(LocalDate.parse("2026-03-31"), schedule.get(1).dueDate());
        assertEquals(LocalDate.parse("2026-04-30"), schedule.get(2).dueDate());
        assertEquals(1, schedule.get(0).number());
        assertEquals(3, schedule.get(2).number());
    }

    @Test
    void aSingleInstallmentPaysTheWholeBalanceWithItsInterest() {
        var schedule = FinancingSimulationService.frenchSchedule(pen("36000"), new BigDecimal("12"), 1, START);

        assertEquals(1, schedule.size());
        assertEquals(pen("36341.60"), schedule.getFirst().amount());
        assertEquals(pen("36000.00"), schedule.getFirst().principal());
    }

    @Test
    void theDownPaymentMustReachTheMinimumAndNamesIt() {
        assertDoesNotThrow(() -> FinancingSimulationService.validate(PRICE, initial("9000"), 12, RULES));

        var error = assertThrows(IllegalArgumentException.class,
                () -> FinancingSimulationService.validate(PRICE, initial("8999.99"), 12, RULES));

        assertTrue(error.getMessage().startsWith("initialPayment"), error.getMessage());
        assertTrue(error.getMessage().contains("9000.00 PEN"), error.getMessage());
        assertTrue(error.getMessage().contains("20 %"), error.getMessage());
    }

    @Test
    void theDownPaymentCannotCoverTheWholePrice() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> FinancingSimulationService.validate(PRICE, initial("45000"), 12, RULES));

        assertTrue(error.getMessage().startsWith("initialPayment"), error.getMessage());
    }

    @Test
    void theTermGoesFromOneMonthToTheProjectMaximum() {
        assertDoesNotThrow(() -> FinancingSimulationService.validate(PRICE, initial("9000"), 1, RULES));
        assertDoesNotThrow(() -> FinancingSimulationService.validate(PRICE, initial("9000"), 120, RULES));
        for (var term : new int[]{0, 121}) {
            var error = assertThrows(IllegalArgumentException.class,
                    () -> FinancingSimulationService.validate(PRICE, initial("9000"), term, RULES));
            assertTrue(error.getMessage().startsWith("termMonths"), error.getMessage());
            assertTrue(error.getMessage().contains("120"), error.getMessage());
        }
    }

    @Test
    void theDownPaymentIsInTheCurrencyOfThePrice() {
        var error = assertThrows(IllegalArgumentException.class, () -> FinancingSimulationService.validate(PRICE,
                new InitialPayment(new Money(new BigDecimal("9000"), "USD")), 12, RULES));

        assertTrue(error.getMessage().contains("PEN"), error.getMessage());
    }

    private static Money sum(java.util.List<ScheduledInstallment> schedule,
                             java.util.function.Function<ScheduledInstallment, Money> part) {
        return schedule.stream().map(part).reduce(pen("0"), Money::plus);
    }

    private static InitialPayment initial(String amount) {
        return new InitialPayment(pen(amount));
    }

    private static Money pen(String amount) {
        return new Money(new BigDecimal(amount), "PEN");
    }
}
