package com.novacorp.inmonode.inmonodebackend.financial.domain.services;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetProjectByIdQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetProjectLotsQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetPublishedLotQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetPublishedProjectsQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ProjectSummary;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PublishedLot;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;

import java.util.List;
import java.util.Optional;

/**
 * Query side of the project catalog. Drafts are visible only to the catalog back-office; for anyone else
 * they do not exist.
 */
public interface ProjectQueryService {
    Result<List<Lot>, ApplicationError> handle(com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetAdminProjectLotsQuery query);

    /** US-15: published projects with their price range and availability, ordered by name. */
    List<ProjectSummary> handle(GetPublishedProjectsQuery query);

    Result<Project, ApplicationError> handle(GetProjectByIdQuery query);

    /** US-05, US-15: lots of a visible project, ordered by code. */
    Result<List<Lot>, ApplicationError> handle(GetProjectLotsQuery query);

    /** US-17: the lot with its project, only when the project is published; drafts are never offered to buyers. */
    Optional<PublishedLot> handle(GetPublishedLotQuery query);
}
