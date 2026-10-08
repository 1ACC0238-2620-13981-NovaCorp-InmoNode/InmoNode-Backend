package com.novacorp.inmonode.inmonodebackend.financial.domain.model.entities;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PaymentEvidenceStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PaymentEvidenceTest {

    private static final Instant SUBMITTED_AT = Instant.parse("2026-10-08T10:00:00Z");
    private static final Instant REVIEWED_AT = Instant.parse("2026-10-08T15:00:00Z");

    @Test
    void anApprovedCopyKeepsTheReviewerTheNoteAndTheDate() {
        var evidence = voucher();

        var approved = evidence.approved(77L, "  conciliado con el banco ", REVIEWED_AT);

        assertEquals(PaymentEvidenceStatus.APPROVED, approved.getStatus());
        assertEquals(77L, approved.getReviewerId());
        assertEquals("conciliado con el banco", approved.getReviewerNote());
        assertEquals(REVIEWED_AT, approved.getReviewedAt());
        assertEquals(evidence.getReference(), approved.getReference());
        assertEquals(evidence.getAmount(), approved.getAmount());
        assertFalse(approved.isPending());
        assertTrue(evidence.isPending(), "the original is untouched");
    }

    @Test
    void anApprovalNeedsNoNote() {
        assertNull(voucher().approved(77L, "  ", REVIEWED_AT).getReviewerNote());
        assertNull(voucher().approved(77L, null, REVIEWED_AT).getReviewerNote());
    }

    @Test
    void aRejectionNeedsItsReason() {
        var rejected = voucher().rejected(77L, "Voucher ilegible", REVIEWED_AT);

        assertEquals(PaymentEvidenceStatus.REJECTED, rejected.getStatus());
        assertEquals("Voucher ilegible", rejected.getReviewerNote());
        for (var reason : new String[]{null, "", "   "}) {
            var error = assertThrows(IllegalArgumentException.class,
                    () -> voucher().rejected(77L, reason, REVIEWED_AT), String.valueOf(reason));
            assertTrue(error.getMessage().startsWith("reason"), error.getMessage());
        }
        assertThrows(IllegalArgumentException.class,
                () -> voucher().rejected(77L, "x".repeat(PaymentEvidence.MAX_NOTE_LENGTH + 1), REVIEWED_AT));
    }

    @Test
    void aDecisionIsFinal() {
        var approved = voucher().approved(77L, null, REVIEWED_AT);
        var rejected = voucher().rejected(77L, "Cuenta incorrecta", REVIEWED_AT);

        assertThrows(IllegalStateException.class, () -> approved.rejected(77L, "otra", REVIEWED_AT));
        assertThrows(IllegalStateException.class, () -> rejected.approved(77L, null, REVIEWED_AT));
    }

    @Test
    void aLateEvidenceStaysLateWhenDecided() {
        var rejected = voucher().markedLate().rejected(77L, "Llegó tarde", REVIEWED_AT);

        assertTrue(rejected.isLate());
    }

    private static PaymentEvidence voucher() {
        var voucherId = UUID.randomUUID();
        return PaymentEvidence.fromVoucher(voucherId, Money.of(new BigDecimal("1500")),
                LocalDate.parse("2026-10-07"), "00123456", false, "vouchers/r/%s.jpg".formatted(voucherId),
                SUBMITTED_AT);
    }
}
