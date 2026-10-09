package com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.*;
public record RegisterLotCommand(Long projectId, String stageName, String code, LotDimensions dimensions,
                                  Money price, LotBoundary boundary) {}
