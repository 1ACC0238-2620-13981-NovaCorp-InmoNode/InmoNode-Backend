package com.novacorp.inmonode.inmonodebackend.financial.application.internal.queryservices;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.acl.ExternalIamService;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetProjectByIdQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetProjectLotsQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetPublishedLotQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetPublishedProjectsQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotStatistics;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ProjectDetail;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ProjectDetail.GalleryImage;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ProjectSummary;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PublishedLot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectImageRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectImageRepository.RegisteredImage;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ProjectQueryService;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import com.novacorp.inmonode.inmonodebackend.shared.application.storage.ObjectStorage;
import org.springframework.stereotype.Service;
import com.novacorp.inmonode.inmonodebackend.financial.application.catalog.CatalogReadCache;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class ProjectQueryServiceImpl implements ProjectQueryService {

    /** The buyer browses the gallery while the page stays open, so its links last longer than a download's. */
    static final Duration GALLERY_URL_VALIDITY = Duration.ofHours(1);

    private final ProjectRepository projectRepository;
    private final LotRepository lotRepository;
    private final ProjectImageRepository projectImageRepository;
    private final ObjectStorage objectStorage;
    private final ExternalIamService externalIamService;
    private final CatalogReadCache cache;

    public ProjectQueryServiceImpl(ProjectRepository projectRepository, LotRepository lotRepository,
                                   ProjectImageRepository projectImageRepository, ObjectStorage objectStorage,
                                   ExternalIamService externalIamService, CatalogReadCache cache) {
        this.projectRepository = projectRepository;
        this.lotRepository = lotRepository;
        this.projectImageRepository = projectImageRepository;
        this.objectStorage = objectStorage;
        this.externalIamService = externalIamService;
        this.cache = cache;
    }

    @Override
    public List<ProjectSummary> handle(GetPublishedProjectsQuery query) {
        return cache.get("published-projects", this::loadPublishedProjects);
    }

    private List<ProjectSummary> loadPublishedProjects() {
        var projects = projectRepository.findAllPublished();
        var statistics = lotRepository.summarizeByProjectIds(
                projects.stream().map(Project::getId).map(Objects::requireNonNull).toList());
        return projects.stream()
                .map(project -> new ProjectSummary(project,
                        statistics.getOrDefault(project.getId(), LotStatistics.EMPTY)))
                .toList();
    }

    @Override
    public Result<ProjectDetail, ApplicationError> handle(GetProjectByIdQuery query) {
        return visibleProject(query.projectId()).map(project -> new ProjectDetail(project, gallery(project)));
    }

    /**
     * The links are signed on every read and never cached, so they are always valid for the whole
     * {@link #GALLERY_URL_VALIDITY}; signing needs no call to the storage.
     */
    private List<GalleryImage> gallery(Project project) {
        return projectImageRepository.findByProjectId(project.getId()).stream()
                .map(RegisteredImage::image)
                .map(image -> {
                    var link = objectStorage.presignDownload(image.objectKey(), GALLERY_URL_VALIDITY);
                    return new GalleryImage(image, link.url(), link.expiresAt());
                })
                .toList();
    }

    @Override
    public Result<List<Lot>, ApplicationError> handle(GetProjectLotsQuery query) {
        return visibleProject(query.projectId())
                .map(project -> project.isPublished()
                        ? cache.get("project-lots:" + query, () -> lotRepository.findByProjectId(query.projectId(), query.filters()))
                        : lotRepository.findByProjectId(query.projectId(), query.filters()));
    }

    @Override
    public Optional<PublishedLot> handle(GetPublishedLotQuery query) {
        return cache.get("published-lot:" + query.lotId(), () -> lotRepository.findById(query.lotId())
                .filter(lot -> lot.getStatus() != com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotStatus.DRAFT)
                .flatMap(lot -> projectRepository.findById(lot.getProjectId())
                        .filter(Project::isPublished)
                        .map(project -> new PublishedLot(project, lot))));
    }

    @Override
    public Result<List<Lot>, ApplicationError> handle(com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetAdminProjectLotsQuery query) {
        if (!externalIamService.isCatalogAdmin()) return Result.failure(new ApplicationError("FORBIDDEN", "Catalog administrator required"));
        return projectRepository.existsById(query.projectId()) ? Result.success(lotRepository.findByProjectId(query.projectId()))
                : Result.failure(ApplicationError.notFound("project", String.valueOf(query.projectId())));
    }

    /** The project when it is published, or a draft seen by the catalog back-office; not found otherwise. */
    private Result<Project, ApplicationError> visibleProject(Long projectId) {
        var published = cache.get("published-project:" + projectId,
                () -> projectRepository.findById(projectId).filter(Project::isPublished));
        return (published.isPresent() || !externalIamService.isCatalogAdmin() ? published : projectRepository.findById(projectId))
                .filter(project -> project.isPublished() || externalIamService.isCatalogAdmin())
                .<Result<Project, ApplicationError>>map(Result::success)
                .orElseGet(() -> Result.failure(ApplicationError.notFound("project", String.valueOf(projectId))));
    }
}
