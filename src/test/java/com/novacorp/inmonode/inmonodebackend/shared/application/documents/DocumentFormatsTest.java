package com.novacorp.inmonode.inmonodebackend.shared.application.documents;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** Amounts and dates in the generated documents read as in the apps. */
class DocumentFormatsTest {

    @Test
    void amountsReadWithTheCurrencySymbolThousandsSeparatorsAndTwoDecimals() {
        assertEquals("S/ 45,000.00", DocumentFormats.money(new BigDecimal("45000"), "PEN"));
        assertEquals("S/ 1,234,567.50", DocumentFormats.money(new BigDecimal("1234567.5"), "PEN"));
        assertEquals("US$ 980.00", DocumentFormats.money(new BigDecimal("980"), "USD"));
        assertEquals("EUR 10.00", DocumentFormats.money(new BigDecimal("10"), "EUR"));
        assertEquals("897.55", DocumentFormats.amount(new BigDecimal("897.559")), "cut, never rounded up");
    }

    @Test
    void datesAreWrittenDayFirstInLima() {
        assertEquals("12/12/2026", DocumentFormats.date(LocalDate.of(2026, 12, 12)));
        assertEquals("21/11/2026", DocumentFormats.date(Instant.parse("2026-11-22T00:21:46Z")));
        assertEquals("21/11/2026 19:21", DocumentFormats.dateTime(Instant.parse("2026-11-22T00:21:46Z")));
        assertEquals("12.5", DocumentFormats.number(new BigDecimal("12.5000")));
    }
}
