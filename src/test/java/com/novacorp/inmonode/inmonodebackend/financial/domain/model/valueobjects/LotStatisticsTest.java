package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class LotStatisticsTest {

    private static final Money MIN = Money.of(new BigDecimal("44000"));
    private static final Money MAX = Money.of(new BigDecimal("47500"));

    @Test
    void availabilityIsTheShareOfAvailableLotsWithTwoDecimals() {
        assertEquals(new BigDecimal("100.00"), new LotStatistics(3, 3, 0, MIN, MAX).availabilityPercentage());
        assertEquals(new BigDecimal("66.67"), new LotStatistics(3, 2, 1, MIN, MAX).availabilityPercentage());
        assertEquals(new BigDecimal("0.00"), LotStatistics.EMPTY.availabilityPercentage());
    }

    @Test
    void projectIsSoldOutOnlyWhenEveryLotIsSold() {
        assertTrue(new LotStatistics(3, 0, 3, MIN, MAX).isSoldOut());
        assertFalse(new LotStatistics(3, 0, 2, MIN, MAX).isSoldOut());
        assertFalse(LotStatistics.EMPTY.isSoldOut());
    }
}
