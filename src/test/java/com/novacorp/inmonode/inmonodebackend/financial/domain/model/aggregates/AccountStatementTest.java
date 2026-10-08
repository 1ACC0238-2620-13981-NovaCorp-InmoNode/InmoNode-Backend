package com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.entities.Installment;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ContractStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FinancingPlan;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.InstallmentStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationChannel;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationStatus;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AccountStatementTest {

    private static final Instant NOW = Instant.parse("2026-10-08T15:00:00Z");
    private static final UUID TRANSACTION = UUID.fromString("e422adfe-e39a-4cb7-a8a5-cadef492cea9");
    private static final UUID DOCUMENT = UUID.fromString("0b6a2f3c-9e10-4c55-9a43-7d0e4a521c1b");
    private static final FinancingPlan PLAN =
            new FinancingPlan(Money.of(new BigDecimal("45000")), 12, new BigDecimal("12"));

    @Test
    void itFinancesThePriceLessTheDownPaymentWithThePlanOfTheReservation() {
        var statement = AccountStatement.open(contract(5L), reservation(PLAN), NOW);

        assertNull(statement.getId());
        assertEquals(10L, statement.getContractId());
        assertEquals(5L, statement.getReservationId());
        assertEquals(TRANSACTION, statement.getTransactionId());
        assertEquals(41L, statement.getBuyerId());
        assertEquals(3L, statement.getLotId());
        assertEquals("PEN", statement.getCurrency());
        assertEquals(new BigDecimal("45000.00"), statement.getLotPrice());
        assertEquals(new BigDecimal("9000.00"), statement.getInitialPayment());
        assertEquals(new BigDecimal("36000.00"), statement.financedAmount());
        assertEquals(12, statement.getTermMonths());
        assertEquals(new BigDecimal("12.000"), statement.getAnnualInterestRate());
        assertEquals(NOW, statement.getOpenedAt());
        assertEquals(12, statement.getInstallments().size());
        assertEquals(new BigDecimal("3198.56"), statement.getInstallments().getFirst().getAmount());
        assertEquals(new BigDecimal("3198.50"), statement.getInstallments().getLast().getAmount());
    }

    @Test
    void installmentsFallDueFromTheDayInLimaItWasOpened() {
        // 22:00 of October 8 in Lima, already October 9 in UTC.
        var statement = AccountStatement.open(contract(5L), reservation(PLAN), Instant.parse("2026-10-09T03:00:00Z"));

        assertEquals(LocalDate.parse("2026-11-08"), statement.getInstallments().getFirst().getDueDate());
        assertEquals(LocalDate.parse("2027-10-08"), statement.getInstallments().getLast().getDueDate());
    }

    @Test
    void aNewStatementOnlyHasTheDownPaymentPaid() {
        var statement = AccountStatement.open(contract(5L), reservation(PLAN), NOW);

        assertEquals(new BigDecimal("47382.66"), statement.totalAmount());
        assertEquals(new BigDecimal("9000.00"), statement.paidAmount());
        assertEquals(new BigDecimal("38382.66"), statement.balance());
        assertEquals(new BigDecimal("18.99"), statement.progressPercentage());
        assertFalse(statement.isFullyPaid());
        assertEquals(1, statement.nextInstallment().orElseThrow().getNumber());
    }

    @Test
    void paidInstallmentsAndLateFeesChangeTheFigures() {
        var opened = AccountStatement.open(contract(5L), reservation(PLAN), NOW);
        var installments = new ArrayList<>(opened.getInstallments());
        installments.set(0, paid(installments.get(0)));
        installments.set(1, paid(installments.get(1)));
        installments.set(2, overdue(installments.get(2), new BigDecimal("63.97")));
        var statement = restore(opened, installments);

        assertEquals(new BigDecimal("15397.12"), statement.paidAmount());
        assertEquals(new BigDecimal("32049.51"), statement.balance(), "the late fee is owed too");
        assertEquals(new BigDecimal("47446.63"), statement.totalAmount());
        assertEquals(new BigDecimal("32.45"), statement.progressPercentage());
        assertEquals(3, statement.nextInstallment().orElseThrow().getNumber(), "an overdue one is still to pay");
    }

    @Test
    void withEveryInstallmentPaidNothingIsOwed() {
        var opened = AccountStatement.open(contract(5L), reservation(PLAN), NOW);
        var statement = restore(opened, opened.getInstallments().stream().map(AccountStatementTest::paid).toList());

        assertTrue(statement.isFullyPaid());
        assertEquals(new BigDecimal("0.00"), statement.balance());
        assertEquals(new BigDecimal("47382.66"), statement.paidAmount());
        assertEquals(new BigDecimal("100.00"), statement.progressPercentage());
        assertTrue(statement.nextInstallment().isEmpty());
        assertFalse(statement.isDueSoon(LocalDate.parse("2027-12-01")));
    }

    @Test
    void anInstallmentIsPaidWithExactlyWhatIsDueInAnyOrder() {
        var statement = AccountStatement.open(contract(5L), reservation(PLAN), NOW);
        var paidAt = NOW.plusSeconds(86_400 * 30);

        statement.registerInstallmentPayment(3, new BigDecimal("3198.56"), paidAt);

        var third = statement.findInstallment(3).orElseThrow();
        assertEquals(InstallmentStatus.PAID, third.getStatus());
        assertEquals(paidAt, third.getPaidAt());
        assertEquals(new BigDecimal("3198.56"), third.getPaidAmount());
        assertEquals(new BigDecimal("12198.56"), statement.paidAmount());
        assertEquals(new BigDecimal("35184.10"), statement.balance());
        assertEquals(1, statement.nextInstallment().orElseThrow().getNumber());
        assertFalse(statement.isFullyPaid());
    }

    @Test
    void anOverdueInstallmentIsPaidWithItsLateFee() {
        var opened = AccountStatement.open(contract(5L), reservation(PLAN), NOW);
        var installments = new ArrayList<>(opened.getInstallments());
        installments.set(0, overdue(installments.get(0), new BigDecimal("31.99")));
        var statement = restore(opened, installments);
        var first = statement.findInstallment(1).orElseThrow();

        assertFalse(first.isSettledBy(new BigDecimal("3198.56")));
        assertThrows(IllegalArgumentException.class,
                () -> statement.registerInstallmentPayment(1, new BigDecimal("3198.56"), NOW));
        assertTrue(first.isSettledBy(new BigDecimal("3230.55")));
        statement.registerInstallmentPayment(1, new BigDecimal("3230.55"), NOW);

        assertEquals(InstallmentStatus.PAID, first.getStatus());
        assertEquals(new BigDecimal("12230.55"), statement.paidAmount());
    }

    @Test
    void aPaidOrUnknownInstallmentCannotBePaid() {
        var statement = AccountStatement.open(contract(5L), reservation(PLAN), NOW);
        statement.registerInstallmentPayment(1, new BigDecimal("3198.56"), NOW);

        assertThrows(IllegalStateException.class,
                () -> statement.registerInstallmentPayment(1, new BigDecimal("3198.56"), NOW));
        assertThrows(IllegalArgumentException.class,
                () -> statement.registerInstallmentPayment(13, new BigDecimal("3198.56"), NOW));
        assertThrows(IllegalArgumentException.class,
                () -> statement.registerInstallmentPayment(2, new BigDecimal("3198.57"), NOW));
        assertTrue(statement.findInstallment(13).isEmpty());
    }

    @Test
    void theLastPaymentPaysTheStatementOff() {
        var statement = AccountStatement.open(contract(5L), reservation(PLAN), NOW);
        statement.getInstallments().forEach(installment ->
                statement.registerInstallmentPayment(installment.getNumber(), installment.amountDue(), NOW));

        assertTrue(statement.isFullyPaid());
        assertEquals(new BigDecimal("0.00"), statement.balance());
        assertEquals(new BigDecimal("47382.66"), statement.paidAmount());
        assertEquals(new BigDecimal("100.00"), statement.progressPercentage());
    }

    @Test
    void anInstallmentFallsOverdueTheDayAfterItsDueDateWithASingleLateFee() {
        var statement = AccountStatement.open(contract(5L), reservation(PLAN), NOW);
        var lateFeeRate = new BigDecimal("1.5");

        assertTrue(statement.markOverdueInstallments(LocalDate.parse("2026-11-08"), lateFeeRate).isEmpty(),
                "it is due all of its due date");
        var overdue = statement.markOverdueInstallments(LocalDate.parse("2026-11-09"), lateFeeRate);

        assertEquals(List.of(1), overdue.stream().map(Installment::getNumber).toList());
        var first = statement.findInstallment(1).orElseThrow();
        assertEquals(InstallmentStatus.OVERDUE, first.getStatus());
        assertEquals(new BigDecimal("47.98"), first.getPenalty());
        assertEquals(new BigDecimal("3246.54"), first.amountDue());
        assertEquals(new BigDecimal("38430.64"), statement.balance());
        assertTrue(statement.markOverdueInstallments(LocalDate.parse("2026-11-20"), lateFeeRate).isEmpty(),
                "the fee is charged once");
        assertEquals(new BigDecimal("47.98"), first.getPenalty());
    }

    @Test
    void severalInstallmentsCanFallOverdueAtOnceButNotThePaidOnes() {
        var statement = AccountStatement.open(contract(5L), reservation(PLAN), NOW);
        statement.registerInstallmentPayment(1, new BigDecimal("3198.56"), NOW);

        var overdue = statement.markOverdueInstallments(LocalDate.parse("2027-01-10"), BigDecimal.ZERO);

        assertEquals(List.of(2, 3), overdue.stream().map(Installment::getNumber).toList());
        assertEquals(2, statement.overdueInstallmentCount());
        assertEquals(InstallmentStatus.PAID, statement.findInstallment(1).orElseThrow().getStatus());
        assertEquals(new BigDecimal("0.00"), statement.findInstallment(2).orElseThrow().getPenalty(),
                "a project without late fee");
    }

    @Test
    void buyersAreRemindedOnceOfAnInstallmentDueWithinFiveDays() {
        var statement = AccountStatement.open(contract(5L), reservation(PLAN), NOW);

        assertTrue(statement.installmentsToRemind(LocalDate.parse("2026-11-02")).isEmpty());
        assertEquals(List.of(1), statement.installmentsToRemind(LocalDate.parse("2026-11-03")).stream()
                .map(Installment::getNumber).toList());
        assertEquals(List.of(1), statement.installmentsToRemind(LocalDate.parse("2026-11-08")).stream()
                .map(Installment::getNumber).toList(), "even on its due date");

        statement.recordReminder(1, NOW);

        assertEquals(NOW, statement.findInstallment(1).orElseThrow().getReminderSentAt());
        assertTrue(statement.installmentsToRemind(LocalDate.parse("2026-11-05")).isEmpty());
        statement.registerInstallmentPayment(2, new BigDecimal("3198.56"), NOW);
        assertTrue(statement.installmentsToRemind(LocalDate.parse("2026-12-05")).isEmpty(), "paid already");
    }

    @Test
    void buyersAreToldOnceOfAnOverdueInstallment() {
        var statement = AccountStatement.open(contract(5L), reservation(PLAN), NOW);
        assertTrue(statement.overdueInstallmentsToNotify().isEmpty());
        statement.markOverdueInstallments(LocalDate.parse("2026-11-09"), new BigDecimal("1.5"));

        assertEquals(List.of(1), statement.overdueInstallmentsToNotify().stream()
                .map(Installment::getNumber).toList());
        assertTrue(statement.installmentsToRemind(LocalDate.parse("2026-11-09")).isEmpty(),
                "an overdue one gets a notice, not a reminder");
        statement.recordOverdueNotice(1, NOW);

        assertEquals(NOW, statement.findInstallment(1).orElseThrow().getOverdueNotifiedAt());
        assertTrue(statement.overdueInstallmentsToNotify().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> statement.recordOverdueNotice(13, NOW));
    }

    @Test
    void theNextInstallmentIsDueSoonWithinFiveDaysOrWhenLate() {
        var statement = AccountStatement.open(contract(5L), reservation(PLAN), NOW);

        assertFalse(statement.isDueSoon(LocalDate.parse("2026-11-02")));
        assertTrue(statement.isDueSoon(LocalDate.parse("2026-11-03")));
        assertTrue(statement.isDueSoon(LocalDate.parse("2026-11-08")));
        assertTrue(statement.isDueSoon(LocalDate.parse("2026-12-01")));
    }

    @Test
    void itBelongsToTheBuyerOfTheContract() {
        var statement = AccountStatement.open(contract(5L), reservation(PLAN), NOW);

        assertTrue(statement.belongsTo(41L));
        assertFalse(statement.belongsTo(42L));
    }

    @Test
    void itNeedsTheContractOfTheReservationAndAPlan() {
        assertThrows(IllegalArgumentException.class,
                () -> AccountStatement.open(contract(6L), reservation(PLAN), NOW));
        assertThrows(IllegalArgumentException.class,
                () -> AccountStatement.open(contract(5L), reservation(null), NOW));
    }

    private static Contract contract(Long reservationId) {
        return Contract.restore(10L, reservationId, TRANSACTION, 41L, 3L, DOCUMENT,
                "contracts/%s/%s.pdf".formatted(TRANSACTION, DOCUMENT), 2048, ContractStatus.ISSUED, NOW, 77L, NOW);
    }

    private static Reservation reservation(@Nullable FinancingPlan plan) {
        return Reservation.restore(5L, 3L, ReservationChannel.WEB, 41L, null, TRANSACTION,
                Money.of(new BigDecimal("9000")), NOW, ReservationStatus.VERIFIED, List.of(), NOW, plan);
    }

    private static AccountStatement restore(AccountStatement statement, List<Installment> installments) {
        return AccountStatement.restore(1L, statement.getContractId(), statement.getReservationId(),
                statement.getTransactionId(), statement.getBuyerId(), statement.getLotId(), statement.getCurrency(),
                statement.getLotPrice(), statement.getInitialPayment(), statement.getTermMonths(),
                statement.getAnnualInterestRate(), statement.getOpenedAt(), installments);
    }

    private static Installment paid(Installment installment) {
        return Installment.restore(installment.getNumber() * 100L, installment.getNumber(), installment.getDueDate(),
                installment.getAmount(), installment.getPrincipal(), installment.getInterest(), InstallmentStatus.PAID,
                NOW, installment.amountDue(), installment.getPenalty(), null, null);
    }

    private static Installment overdue(Installment installment, BigDecimal penalty) {
        return Installment.restore(installment.getNumber() * 100L, installment.getNumber(), installment.getDueDate(),
                installment.getAmount(), installment.getPrincipal(), installment.getInterest(),
                InstallmentStatus.OVERDUE, null, null, penalty, null, NOW);
    }
}
