package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

import java.math.BigDecimal;
public record CatalogLotResource(Long id, Long projectId, String stageName, String code, String status,
        BigDecimal area, BigDecimal front, BigDecimal depth, BigDecimal price, String currency,
        GeoJsonGeometryResource geometry) {}
