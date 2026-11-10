package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.AccountStatement;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Contract;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NoDebtCertificateTest {

    private static final Instant NOW = Instant.parse("2026-10-08T15:00:00Z");
    private static final Instant PAID_OFF_AT = Instant.parse("2027-10-08T16:30:00Z");
    private static final UUID TRANSACTION = UUID.fromString("e422adfe-e39a-4cb7-a8a5-cadef492cea9");
    private static final UUID DOCUMENT = UUID.fromString("0b6a2f3c-9e10-4c55-9a43-7d0e4a521c1b");
    private static final CoOwner CO_OWNER = new CoOwner("Ana Pérez Quispe", "DNI", "45678912");

    @Test
    void itAttestsTheDownPaymentTheInstallmentsAndTheLateFeesPaid() {
        var certificate = NoDebtCertificate.issue(paidOff(123L), "Estados de cuenta", "E-01", "buyer@mail.com",
                CO_OWNER, NOW);

        assertEquals("NA-000123", certificate.number());
        assertEquals(123L, certificate.accountStatementId());
        assertEquals(10L, certificate.contractId());
        assertEquals(41L, certificate.buyerId());
        assertEquals("buyer@mail.com", certificate.buyerEmail());
        assertEquals(CO_OWNER, certificate.coOwner());
        assertEquals("Estados de cuenta", certificate.projectName());
        assertEquals("E-01", certificate.lotCode());
        assertEquals("PEN", certificate.currency());
        assertEquals(new BigDecimal("45000.00"), certificate.lotPrice());
        assertEquals(new BigDecimal("9000.00"), certificate.initialPayment());
        assertEquals(12, certificate.installmentCount());
        assertEquals(new BigDecimal("38258.81"), certificate.installmentsPaid());
        assertEquals(new BigDecimal("47.82"), certificate.lateFeesPaid());
        assertEquals(new BigDecimal("47306.63"), certificate.totalPaid());
        assertEquals(PAID_OFF_AT, certificate.paidOffAt());
        assertEquals(NOW, certificate.issuedAt());
    }

    @Test
    void everyDownloadOfAStatementHasTheSameVerificationCode() {
        var first = NoDebtCertificate.issue(paidOff(123L), "Estados de cuenta", "E-01", "buyer@mail.com", null, NOW);
        var later = NoDebtCertificate.issue(paidOff(123L), "Estados de cuenta", "E-01", null, CO_OWNER,
                NOW.plusSeconds(86_400 * 90));
        var another = NoDebtCertificate.issue(paidOff(124L), "Estados de cuenta", "E-01", "buyer@mail.com", null, NOW);

        assertTrue(first.verificationCode().matches("[0-9A-F]{4}(-[0-9A-F]{4}){3}"), first.verificationCode());
        assertEquals(first.verificationCode(), later.verificationCode());
        assertEquals(first.number(), later.number());
        assertNotEquals(first.verificationCode(), another.verificationCode());
    }

    @Test
    void aStatementThatStillOwesHasNoCertificate() {
        var statement = restore(123L, AccountStatement.open(contract(), reservation(), NOW));

        assertThrows(IllegalStateException.class,
                () -> NoDebtCertificate.issue(statement, "Estados de cuenta", "E-01", null, null, NOW));
    }

    /** 9 000 down and 12 installments, the first one paid late with its 1.5 % fee and the last one paid off. */
    private static AccountStatement paidOff(Long id) {
        var statement = AccountStatement.open(contract(), reservation(), NOW);
        statement.markOverdueInstallments(LocalDate.parse("2026-11-09"), new BigDecimal("1.5"));
        for (var installment : statement.getInstallments()) {
            var paidAt = installment.getNumber() == 12 ? PAID_OFF_AT : NOW.plusSeconds(86_400L * 30 * installment.getNumber());
            statement.registerInstallmentPayment(installment.getNumber(), installment.amountDue(), paidAt);
        }
        return restore(id, statement);
    }

    private static AccountStatement restore(Long id, AccountStatement statement) {
        return AccountStatement.restore(id, statement.getContractId(), statement.getReservationId(),
                statement.getTransactionId(), statement.getBuyerId(), statement.getLotId(), statement.getCurrency(),
                statement.getLotPrice(), statement.getInitialPayment(), statement.getTermMonths(),
                statement.getAnnualInterestRate(), statement.getOpenedAt(), statement.getInstallments());
    }

    private static Contract contract() {
        return Contract.restore(10L, 5L, TRANSACTION, 41L, 3L, DOCUMENT,
                "contracts/%s/%s.pdf".formatted(TRANSACTION, DOCUMENT), 2048, ContractStatus.ISSUED, NOW, 77L, NOW);
    }

    private static Reservation reservation() {
        return Reservation.restore(5L, 3L, ReservationChannel.WEB, 41L, null, TRANSACTION,
                Money.of(new BigDecimal("9000")), NOW, ReservationStatus.VERIFIED, List.of(), NOW,
                new FinancingPlan(Money.of(new BigDecimal("45000")), 12, new BigDecimal("12")));
    }
}
