package com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.entities.Installment;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ContractStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FinancingPlan;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.InstallmentPaymentIntentStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.InstallmentStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationChannel;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class InstallmentPaymentIntentTest {

    private static final Instant NOW = Instant.parse("2026-11-22T15:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final UUID ID = UUID.fromString("5f0c7a52-3a8e-4d2b-9a61-0c3f2e7b9d14");
    private static final UUID TRANSACTION = UUID.fromString("e422adfe-e39a-4cb7-a8a5-cadef492cea9");
    private static final Long BUYER = 41L;

    @Test
    void itIsOpenedForExactlyWhatTheInstallmentOwesAndExpiresAfterItsTimeToLive() {
        var statement = statement(null);
        var installment = statement.findInstallment(2).orElseThrow();

        var intent = InstallmentPaymentIntent.open(ID, statement, installment, BUYER, "key-1", NOW, TTL);

        assertEquals(ID, intent.getId());
        assertEquals(1L, intent.getAccountStatementId());
        assertEquals(2, intent.getInstallmentNumber());
        assertEquals(BUYER, intent.getBuyerId());
        assertEquals(new BigDecimal("3188.23"), intent.getAmount());
        assertEquals("PEN", intent.getCurrency());
        assertEquals(318_823L, intent.amountInMinorUnits());
        assertEquals("key-1", intent.getIdempotencyKey());
        assertEquals(InstallmentPaymentIntentStatus.OPEN, intent.getStatus());
        assertEquals(NOW, intent.getCreatedAt());
        assertEquals(NOW.plus(TTL), intent.getExpiresAt());
        assertFalse(intent.isExpired(NOW.plus(TTL).minusNanos(1000)));
        assertTrue(intent.isExpired(NOW.plus(TTL)));
        assertTrue(intent.isFor(1L, 2));
        assertFalse(intent.isFor(1L, 3));
        assertTrue(intent.belongsTo(BUYER));
        assertFalse(intent.belongsTo(42L));
    }

    @Test
    void anOverdueInstallmentIsChargedWithItsLateFee() {
        var statement = statement(new BigDecimal("31.99"));

        var intent = InstallmentPaymentIntent.open(ID, statement, statement.findInstallment(1).orElseThrow(), BUYER,
                " ", NOW, TTL);

        assertEquals(new BigDecimal("3220.22"), intent.getAmount());
        assertEquals(322_022L, intent.amountInMinorUnits());
        assertNull(intent.getIdempotencyKey());
    }

    @Test
    void itIsOpenedOnlyForAnUnpaidInstallmentOfAStatementOfTheBuyer() {
        var statement = statement(null);
        var first = statement.findInstallment(1).orElseThrow();
        var other = statement(null).findInstallment(1).orElseThrow();

        assertThrows(IllegalArgumentException.class,
                () -> InstallmentPaymentIntent.open(ID, statement, first, 42L, null, NOW, TTL));
        assertThrows(IllegalArgumentException.class,
                () -> InstallmentPaymentIntent.open(ID, statement, other, BUYER, null, NOW, TTL));
        assertThrows(IllegalArgumentException.class,
                () -> InstallmentPaymentIntent.open(ID, statement, first, BUYER, null, NOW, Duration.ZERO));
        statement.registerInstallmentPayment(1, first.amountDue(), NOW);
        assertThrows(IllegalStateException.class,
                () -> InstallmentPaymentIntent.open(ID, statement, first, BUYER, null, NOW, TTL));
    }

    @Test
    void theGatewayIdIsKeptOnce() {
        var intent = opened();

        intent.attach("pi_1");
        intent.attach("pi_1");

        assertEquals("pi_1", intent.getProviderPaymentIntentId());
        assertThrows(IllegalStateException.class, () -> intent.attach("pi_2"));
        assertThrows(IllegalArgumentException.class, () -> intent.attach(" "));
    }

    @Test
    void itSucceedsOnlyOnce() {
        var intent = opened();
        intent.attach("pi_1");

        intent.succeed("ch_1", NOW.plusSeconds(60));

        assertEquals(InstallmentPaymentIntentStatus.SUCCEEDED, intent.getStatus());
        assertEquals("ch_1", intent.getProviderChargeId());
        assertEquals(NOW.plusSeconds(60), intent.getConfirmedAt());
        assertTrue(intent.isConfirmed());
        assertFalse(intent.isOpen());
        assertFalse(intent.isExpired(NOW.plus(TTL)));
        assertThrows(IllegalStateException.class, () -> intent.succeed("ch_2", NOW));
        assertThrows(IllegalStateException.class, () -> intent.markUnapplied("ch_2", NOW, "paid"));
    }

    @Test
    void aConfirmedChargeIsNeitherCanceledNorExpiredNorDeclined() {
        var intent = opened();
        intent.markUnapplied("ch_1", NOW, "installment 2 was already paid");

        assertEquals(InstallmentPaymentIntentStatus.UNAPPLIED, intent.getStatus());
        assertEquals("installment 2 was already paid", intent.getLastFailure());
        assertThrows(IllegalStateException.class, intent::expire);
        assertThrows(IllegalStateException.class, () -> intent.cancel("late"));
        assertThrows(IllegalStateException.class, () -> intent.recordFailure("declined"));
    }

    @Test
    void aChargeIsConfirmedEvenAfterItWasClosedHere() {
        var expired = opened();
        expired.expire();
        expired.succeed("ch_1", NOW);
        assertEquals(InstallmentPaymentIntentStatus.SUCCEEDED, expired.getStatus());

        var canceled = opened();
        canceled.cancel("what it owes changed");
        canceled.markUnapplied("ch_2", NOW, "installment 2 now owes 3220.22 PEN");
        assertEquals(InstallmentPaymentIntentStatus.UNAPPLIED, canceled.getStatus());
    }

    @Test
    void aDeclineKeepsItOpenWithTheReason() {
        var intent = opened();

        intent.recordFailure("Your card was declined.");

        assertTrue(intent.isOpen());
        assertEquals("Your card was declined.", intent.getLastFailure());
        intent.recordFailure("x".repeat(600));
        assertEquals(500, intent.getLastFailure().length());
    }

    @Test
    void onlyAnOpenChargeIsCanceledOrExpired() {
        var canceled = opened();
        canceled.cancel("canceled in the gateway");
        assertEquals(InstallmentPaymentIntentStatus.CANCELED, canceled.getStatus());
        assertThrows(IllegalStateException.class, canceled::expire);
        assertThrows(IllegalStateException.class, () -> canceled.attach("pi_1"));

        var expired = opened();
        expired.expire();
        assertEquals(InstallmentPaymentIntentStatus.EXPIRED, expired.getStatus());
        assertFalse(expired.isExpired(NOW.plus(TTL)));
        assertThrows(IllegalStateException.class, () -> expired.cancel("late"));
    }

    private static InstallmentPaymentIntent opened() {
        var statement = statement(null);
        return InstallmentPaymentIntent.open(UUID.randomUUID(), statement, statement.findInstallment(2).orElseThrow(),
                BUYER, null, NOW, TTL);
    }

    /**
     * The statement of AccountStatementTest, saved with id 1 for buyer 41 and lot 3; its first installment overdue
     * with {@code penalty} when there is one.
     */
    public static AccountStatement statement(BigDecimal penalty) {
        var plan = new FinancingPlan(Money.of(new BigDecimal("45000")), 12, new BigDecimal("12"));
        var reservation = Reservation.restore(5L, 3L, ReservationChannel.WEB, BUYER, null, TRANSACTION,
                Money.of(new BigDecimal("9000")), NOW, ReservationStatus.VERIFIED, List.of(), NOW, plan);
        var contract = Contract.restore(10L, 5L, TRANSACTION, BUYER, 3L, UUID.randomUUID(), "contracts/c.pdf", 2048,
                ContractStatus.ISSUED, NOW, 77L, NOW);
        var opened = AccountStatement.open(contract, reservation, NOW);
        var installments = new ArrayList<Installment>();
        for (var installment : opened.getInstallments()) {
            var overdue = penalty != null && installment.getNumber() == 1;
            installments.add(Installment.restore(installment.getNumber() * 100L, installment.getNumber(),
                    installment.getDueDate(), installment.getAmount(), installment.getPrincipal(),
                    installment.getInterest(), overdue ? InstallmentStatus.OVERDUE : InstallmentStatus.PENDING, null,
                    null, overdue ? penalty : installment.getPenalty(), null, null, null, null));
        }
        return AccountStatement.restore(1L, opened.getContractId(), opened.getReservationId(),
                opened.getTransactionId(), opened.getBuyerId(), opened.getLotId(), opened.getCurrency(),
                opened.getLotPrice(), opened.getInitialPayment(), opened.getTermMonths(),
                opened.getAnnualInterestRate(), opened.getOpenedAt(), installments);
    }
}
