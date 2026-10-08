package com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects;

import java.math.BigDecimal;

/**
 * Read-only copy of a lot and its project, taken from Control Financiero y Documental to run a simulation. It is not
 * the source of truth for availability: that is decided again, under lock, when the lot is blocked.
 *
 * @param area                   total area, in square meters
 * @param availableAtQueryTime   whether the lot could be separated when it was read
 * @param rules                  the financing rules of its project
 */
public record LotSnapshot(Long lotId, Long projectId, String code, BigDecimal area, Money price,
                          boolean availableAtQueryTime, FinancingRules rules) {
}
