package com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ExtractedDataTest {

    private static final LocalDate DAY = LocalDate.parse("2026-10-08");

    @Test
    void theAmountKeepsTwoDecimalsAndTheConfidenceThree() {
        var data = new ExtractedData(new BigDecimal("1500.5"), "PEN", DAY, "OP-123", new BigDecimal("0.98765"));

        assertEquals(new BigDecimal("1500.50"), data.amount());
        assertEquals(new BigDecimal("0.988"), data.confidence());
    }

    @Test
    void theCurrencyAndTheCodeAreTrimmedAndTheCurrencyUpperCased() {
        var data = new ExtractedData(new BigDecimal("1500"), " usd ", DAY, "  00123456 ", null);

        assertEquals("USD", data.currency());
        assertEquals("00123456", data.operationCode());
        assertNull(data.confidence());
    }

    @Test
    void theAmountMustBePositive() {
        for (var amount : new BigDecimal[]{BigDecimal.ZERO, new BigDecimal("-1"), null}) {
            var error = assertThrows(IllegalArgumentException.class,
                    () -> new ExtractedData(amount, "PEN", DAY, "OP-123", null), String.valueOf(amount));
            assertTrue(error.getMessage().startsWith("amount"), error.getMessage());
        }
    }

    @Test
    void theCurrencyIsAnIsoCode() {
        for (var currency : new String[]{"S/", "SOLES", "PE", "", null}) {
            var error = assertThrows(IllegalArgumentException.class,
                    () -> new ExtractedData(BigDecimal.TEN, currency, DAY, "OP-123", null), String.valueOf(currency));
            assertTrue(error.getMessage().startsWith("currency"), error.getMessage());
        }
    }

    @Test
    void theOperationDateAndCodeAreRequired() {
        var noDate = assertThrows(IllegalArgumentException.class,
                () -> new ExtractedData(BigDecimal.TEN, "PEN", null, "OP-123", null));
        assertTrue(noDate.getMessage().startsWith("operationDate"), noDate.getMessage());
        for (var code : new String[]{"", "   ", null, "9".repeat(51)}) {
            var error = assertThrows(IllegalArgumentException.class,
                    () -> new ExtractedData(BigDecimal.TEN, "PEN", DAY, code, null), String.valueOf(code));
            assertTrue(error.getMessage().startsWith("operationCode"), error.getMessage());
        }
        assertDoesNotThrow(() -> new ExtractedData(BigDecimal.TEN, "PEN", DAY, "9".repeat(50), null));
    }

    @Test
    void theConfidenceGoesFromZeroToOne() {
        assertDoesNotThrow(() -> new ExtractedData(BigDecimal.TEN, "PEN", DAY, "OP-123", BigDecimal.ZERO));
        assertDoesNotThrow(() -> new ExtractedData(BigDecimal.TEN, "PEN", DAY, "OP-123", BigDecimal.ONE));
        for (var confidence : new BigDecimal[]{new BigDecimal("-0.01"), new BigDecimal("1.01")}) {
            var error = assertThrows(IllegalArgumentException.class,
                    () -> new ExtractedData(BigDecimal.TEN, "PEN", DAY, "OP-123", confidence), confidence.toString());
            assertTrue(error.getMessage().startsWith("confidence"), error.getMessage());
        }
    }
}
