package com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.entities.PaymentEvidence;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ContractDocument;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ContractStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PaymentEvidenceSource;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PaymentEvidenceStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationChannel;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ContractTest {

    private static final Instant NOW = Instant.parse("2026-10-08T15:00:00Z");
    private static final UUID TRANSACTION = UUID.fromString("e422adfe-e39a-4cb7-a8a5-cadef492cea9");
    private static final UUID DOCUMENT = UUID.fromString("0b6a2f3c-9e10-4c55-9a43-7d0e4a521c1b");

    @Test
    void aVerifiedWebReservationGetsItsContract() {
        var reservation = web(ReservationStatus.VERIFIED);

        var contract = Contract.issue(reservation, new ContractDocument(TRANSACTION, DOCUMENT, 2048), 77L, NOW);

        assertNull(contract.getId());
        assertEquals(5L, contract.getReservationId());
        assertEquals(TRANSACTION, contract.getTransactionId());
        assertEquals(41L, contract.getBuyerId());
        assertEquals(3L, contract.getLotId());
        assertEquals(DOCUMENT, contract.getDocumentId());
        assertEquals("contracts/%s/%s.pdf".formatted(TRANSACTION, DOCUMENT), contract.getObjectKey());
        assertEquals(2048, contract.getSizeBytes());
        assertEquals(ContractStatus.ISSUED, contract.getStatus());
        assertEquals(NOW, contract.getIssuedAt());
        assertEquals(77L, contract.getIssuedBy());
        assertNull(contract.getBuyerAcknowledgedAt());
    }

    @Test
    void onlyAVerifiedWebReservationCanGetOne() {
        assertTrue(Contract.issuingObstacle(web(ReservationStatus.VERIFIED)).isEmpty());
        assertTrue(Contract.issuingObstacle(web(ReservationStatus.PENDING_VERIFICATION)).orElseThrow()
                .contains("PENDING_VERIFICATION"));
        var field = Reservation.restore(6L, 3L, ReservationChannel.FIELD, 7L, UUID.randomUUID(), TRANSACTION,
                Money.of(new BigDecimal("1500")), NOW, ReservationStatus.VERIFIED, List.of(), NOW);
        assertTrue(Contract.issuingObstacle(field).orElseThrow().contains("field"));
        assertThrows(IllegalStateException.class,
                () -> Contract.issue(field, new ContractDocument(TRANSACTION, DOCUMENT, 2048), 77L, NOW));
    }

    @Test
    void theDocumentMustBelongToTheReservation() {
        assertThrows(IllegalArgumentException.class, () -> Contract.issue(web(ReservationStatus.VERIFIED),
                new ContractDocument(UUID.randomUUID(), DOCUMENT, 2048), 77L, NOW));
    }

    @Test
    void theDocumentIsAPdfOfUpToTenMegabytes() {
        assertDoesNotThrow(() -> new ContractDocument(TRANSACTION, DOCUMENT, 1));
        assertDoesNotThrow(() -> new ContractDocument(TRANSACTION, DOCUMENT, ContractDocument.MAX_SIZE_BYTES));
        for (var size : new long[]{0, ContractDocument.MAX_SIZE_BYTES + 1}) {
            var error = assertThrows(IllegalArgumentException.class,
                    () -> new ContractDocument(TRANSACTION, DOCUMENT, size));
            assertTrue(error.getMessage().startsWith("sizeBytes"), error.getMessage());
        }
        assertEquals("application/pdf", ContractDocument.CONTENT_TYPE);
    }

    private static Reservation web(ReservationStatus status) {
        var evidence = PaymentEvidence.restore(9L, UUID.randomUUID(),
                PaymentEvidenceSource.VOUCHER,
                Money.of(new BigDecimal("9000")), LocalDate.parse("2026-10-07"), "TRX-1", false, "vouchers/a.jpg",
                status == ReservationStatus.VERIFIED ? PaymentEvidenceStatus.APPROVED : PaymentEvidenceStatus.PENDING,
                false, NOW, status == ReservationStatus.VERIFIED ? 77L : null, null,
                status == ReservationStatus.VERIFIED ? NOW : null);
        return Reservation.restore(5L, 3L, ReservationChannel.WEB, 41L, null, TRANSACTION,
                Money.of(new BigDecimal("9000")), NOW, status, List.of(evidence),
                status == ReservationStatus.VERIFIED ? NOW : null);
    }
}
