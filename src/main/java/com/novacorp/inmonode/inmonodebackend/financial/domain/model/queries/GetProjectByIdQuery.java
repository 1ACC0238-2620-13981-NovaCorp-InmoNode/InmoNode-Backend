package com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries;

/**
 * A published project, or a draft when the caller is the catalog back-office.
 */
public record GetProjectByIdQuery(Long projectId) {
}
