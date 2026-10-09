package com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VoucherFileTest {

    private static final UUID RESERVATION = UUID.fromString("7d0e4a52-1c1b-4c55-9a43-0b6a2f3c9e10");
    private static final UUID VOUCHER = UUID.fromString("2b8f6c1e-5d4a-4f7b-8e2c-9a1d3e5f7b60");

    @Test
    void theFileIsStoredUnderItsReservationWithTheExtensionOfItsFormat() {
        assertEquals("vouchers/%s/%s.jpg".formatted(RESERVATION, VOUCHER),
                VoucherFile.of(RESERVATION, VOUCHER, "image/jpeg", 2048).objectKey());
        assertEquals("vouchers/%s/%s.png".formatted(RESERVATION, VOUCHER),
                VoucherFile.of(RESERVATION, VOUCHER, "image/png", 2048).objectKey());
        assertEquals("vouchers/%s/%s.pdf".formatted(RESERVATION, VOUCHER),
                VoucherFile.of(RESERVATION, VOUCHER, "application/pdf", 2048).objectKey());
    }

    @Test
    void theMediaTypeIgnoresCaseAndSurroundingSpaces() {
        var file = VoucherFile.of(RESERVATION, VOUCHER, "  Application/PDF ", 2048);

        assertEquals(VoucherContentType.PDF, file.contentType());
        assertEquals("application/pdf", file.contentType().mediaType());
    }

    @Test
    void onlyJpegPngAndPdfAreAccepted() {
        for (var mediaType : new String[]{"text/plain", "image/gif", "image/jpeg; charset=utf-8", "", null}) {
            var error = assertThrows(IllegalArgumentException.class,
                    () -> VoucherFile.of(RESERVATION, VOUCHER, mediaType, 2048), String.valueOf(mediaType));
            assertTrue(error.getMessage().startsWith("contentType"), error.getMessage());
        }
    }

    @Test
    void theFileWeighsFromOneByteUpToFiveMegabytes() {
        assertDoesNotThrow(() -> VoucherFile.of(RESERVATION, VOUCHER, "image/jpeg", 1));
        assertDoesNotThrow(() -> VoucherFile.of(RESERVATION, VOUCHER, "image/jpeg", 5 * 1024 * 1024));
        for (var size : new long[]{0, -1, 5 * 1024 * 1024 + 1}) {
            var error = assertThrows(IllegalArgumentException.class,
                    () -> VoucherFile.of(RESERVATION, VOUCHER, "image/jpeg", size), String.valueOf(size));
            assertTrue(error.getMessage().startsWith("sizeBytes"), error.getMessage());
        }
    }

    @Test
    void theReservationAndTheVoucherAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> VoucherFile.of(null, VOUCHER, "image/jpeg", 2048));
        assertThrows(IllegalArgumentException.class, () -> VoucherFile.of(RESERVATION, null, "image/jpeg", 2048));
    }
}
