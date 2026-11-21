package com.novacorp.inmonode.inmonodebackend.financial.application.internal.documents;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** Amounts in the generated contract read as in the apps (US-45). */
class PreliminaryContractComposerTest {

    @Test
    void amountsReadWithTheCurrencySymbolThousandsSeparatorsAndTwoDecimals() {
        assertEquals("S/ 45,000.00", PreliminaryContractComposer.money(new Money(new BigDecimal("45000"), "PEN")));
        assertEquals("S/ 1,234,567.50", PreliminaryContractComposer.money(new Money(new BigDecimal("1234567.5"), "PEN")));
        assertEquals("US$ 980.00", PreliminaryContractComposer.money(new Money(new BigDecimal("980"), "USD")));
        assertEquals("EUR 10.00", PreliminaryContractComposer.money(new Money(new BigDecimal("10"), "EUR")));
    }
}
