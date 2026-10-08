package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.AccountStatement;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Contract;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BuyerAccountStatementsTest {

    private static final Instant NOW = Instant.parse("2026-10-08T15:00:00Z");

    @Test
    void theTotalsAddUpEveryLotOfTheSameCurrency() {
        var first = statement(5L, "PEN");
        first.registerInstallmentPayment(1, new BigDecimal("3198.56"), NOW);
        var view = new BuyerAccountStatements(List.of(entry(first), entry(statement(6L, "PEN"))));

        var totals = view.totals();

        assertEquals(1, totals.size());
        var pen = totals.getFirst();
        assertEquals("PEN", pen.currency());
        assertEquals(2, pen.lots());
        assertEquals(new BigDecimal("21198.56"), pen.invested());
        assertEquals(new BigDecimal("73566.76"), pen.debt());
        assertEquals(new BigDecimal("22.37"), pen.progressPercentage());
    }

    @Test
    void amountsOfDifferentCurrenciesAreNeverAddedTogether() {
        var view = new BuyerAccountStatements(List.of(entry(statement(5L, "PEN")), entry(statement(6L, "USD"))));

        var totals = view.totals();

        assertEquals(List.of("PEN", "USD"), totals.stream().map(BuyerAccountStatements.Totals::currency).toList());
        totals.forEach(currencyTotals -> {
            assertEquals(1, currencyTotals.lots());
            assertEquals(new BigDecimal("9000.00"), currencyTotals.invested());
            assertEquals(new BigDecimal("18.99"), currencyTotals.progressPercentage());
        });
    }

    @Test
    void aBuyerWithoutStatementsHasNoTotals() {
        var view = new BuyerAccountStatements(List.of());

        assertTrue(view.totals().isEmpty());
        assertTrue(view.statements().isEmpty());
    }

    private static BuyerAccountStatements.Entry entry(AccountStatement statement) {
        return new BuyerAccountStatements.Entry(statement, 2L, "Los Olivos", "A-0" + statement.getReservationId(),
                false);
    }

    /** 9 000 down on 45 000 in 12 months at 12 %. */
    private static AccountStatement statement(Long reservationId, String currency) {
        var transactionId = UUID.randomUUID();
        var reservation = Reservation.restore(reservationId, 3L, ReservationChannel.WEB, 41L, null, transactionId,
                new Money(new BigDecimal("9000"), currency), NOW, ReservationStatus.VERIFIED, List.of(), NOW,
                new FinancingPlan(new Money(new BigDecimal("45000"), currency), 12, new BigDecimal("12")));
        var contract = Contract.restore(reservationId * 10, reservationId, transactionId, 41L, 3L, UUID.randomUUID(),
                "contracts/%s/c.pdf".formatted(transactionId), 2048, ContractStatus.ISSUED, NOW, 77L, NOW);
        return AccountStatement.open(contract, reservation, NOW);
    }
}
