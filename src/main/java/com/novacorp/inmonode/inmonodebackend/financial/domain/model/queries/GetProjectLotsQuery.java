package com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries;

/**
 * Lots of a project with their plan polygon and availability, to draw the interactive map (US-05, US-15).
 */
public record GetProjectLotsQuery(Long projectId) {
}
