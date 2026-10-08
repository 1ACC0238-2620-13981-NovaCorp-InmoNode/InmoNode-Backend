package com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.aggregates;

import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects.ExtractedData;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects.VoucherContentType;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects.VoucherFile;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects.VoucherStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VoucherTest {

    private static final UUID RESERVATION = UUID.fromString("7d0e4a52-1c1b-4c55-9a43-0b6a2f3c9e10");
    private static final UUID VOUCHER = UUID.fromString("2b8f6c1e-5d4a-4f7b-8e2c-9a1d3e5f7b60");
    private static final VoucherFile FILE = VoucherFile.of(RESERVATION, VOUCHER, "image/jpeg", 2048);
    /** 2026-10-08 at 21:00 in Lima, already the 9th in UTC. */
    private static final Instant RECEIVED_AT = Instant.parse("2026-10-09T02:00:00Z");

    @Test
    void aReceivedVoucherIsSynchronizedWithItsFileAndItsData() {
        var data = dataDated(LocalDate.parse("2026-10-08"));

        var voucher = Voucher.receive(FILE, data, true, RECEIVED_AT);

        assertNull(voucher.getId());
        assertEquals(VOUCHER, voucher.getVoucherId());
        assertEquals(RESERVATION, voucher.getReservationId());
        assertEquals(FILE.objectKey(), voucher.getObjectKey());
        assertEquals(VoucherContentType.JPEG, voucher.getContentType());
        assertEquals(2048, voucher.getSizeBytes());
        assertEquals(data, voucher.getExtractedData());
        assertTrue(voucher.isManuallyCorrected());
        assertEquals(VoucherStatus.SYNCED, voucher.getStatus());
        assertEquals(RECEIVED_AT, voucher.getReceivedAt());
        assertTrue(voucher.belongsTo(RESERVATION));
        assertFalse(voucher.belongsTo(UUID.randomUUID()));
    }

    @Test
    void theOperationCannotBeDatedAfterTheDayInLimaTheVoucherIsReceived() {
        assertDoesNotThrow(() -> Voucher.receive(FILE, dataDated(LocalDate.parse("2026-09-30")), false, RECEIVED_AT));

        var error = assertThrows(IllegalArgumentException.class,
                () -> Voucher.receive(FILE, dataDated(LocalDate.parse("2026-10-09")), false, RECEIVED_AT));
        assertTrue(error.getMessage().startsWith("operationDate"), error.getMessage());
    }

    private static ExtractedData dataDated(LocalDate operationDate) {
        return new ExtractedData(new BigDecimal("1500.00"), "PEN", operationDate, "00123456", new BigDecimal("0.97"));
    }
}
