package com.novacorp.inmonode.inmonodebackend.financial.application.internal.queryservices;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.acl.ExternalIamService;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetProjectByIdQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetProjectLotsQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetPublishedLotQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetPublishedProjectsQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotStatistics;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ProjectSummary;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PublishedLot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ProjectQueryService;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class ProjectQueryServiceImpl implements ProjectQueryService {

    private final ProjectRepository projectRepository;
    private final LotRepository lotRepository;
    private final ExternalIamService externalIamService;

    public ProjectQueryServiceImpl(ProjectRepository projectRepository, LotRepository lotRepository,
                                   ExternalIamService externalIamService) {
        this.projectRepository = projectRepository;
        this.lotRepository = lotRepository;
        this.externalIamService = externalIamService;
    }

    @Override
    public List<ProjectSummary> handle(GetPublishedProjectsQuery query) {
        var projects = projectRepository.findAllPublished();
        var statistics = lotRepository.summarizeByProjectIds(
                projects.stream().map(Project::getId).map(Objects::requireNonNull).toList());
        return projects.stream()
                .map(project -> new ProjectSummary(project,
                        statistics.getOrDefault(project.getId(), LotStatistics.EMPTY)))
                .toList();
    }

    @Override
    public Result<Project, ApplicationError> handle(GetProjectByIdQuery query) {
        return visibleProject(query.projectId());
    }

    @Override
    public Result<List<Lot>, ApplicationError> handle(GetProjectLotsQuery query) {
        return visibleProject(query.projectId())
                .map(project -> lotRepository.findByProjectId(query.projectId()));
    }

    @Override
    public Optional<PublishedLot> handle(GetPublishedLotQuery query) {
        return lotRepository.findById(query.lotId())
                .flatMap(lot -> projectRepository.findById(lot.getProjectId())
                        .filter(Project::isPublished)
                        .map(project -> new PublishedLot(project, lot)));
    }

    /** The project when it is published, or a draft seen by the catalog back-office; not found otherwise. */
    private Result<Project, ApplicationError> visibleProject(Long projectId) {
        return projectRepository.findById(projectId)
                .filter(project -> project.isPublished() || externalIamService.isCatalogAdmin())
                .<Result<Project, ApplicationError>>map(Result::success)
                .orElseGet(() -> Result.failure(ApplicationError.notFound("project", String.valueOf(projectId))));
    }
}
