package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;

/**
 * Catalog entry of a project: the project and the figures of its lots.
 */
public record ProjectSummary(Project project, LotStatistics lotStatistics) {
}
