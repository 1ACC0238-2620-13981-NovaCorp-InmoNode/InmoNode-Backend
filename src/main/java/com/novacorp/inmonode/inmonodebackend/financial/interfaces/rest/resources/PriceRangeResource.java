package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

import java.math.BigDecimal;

public record PriceRangeResource(BigDecimal min, BigDecimal max, String currency) {
}
