package com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotFilters;

/**
 * Lots of a project with their plan polygon and availability, to draw the interactive map (US-05, US-15).
 */
public record GetProjectLotsQuery(Long projectId, LotFilters filters) {
    public GetProjectLotsQuery(Long projectId) {
        this(projectId, LotFilters.NONE);
    }
}
