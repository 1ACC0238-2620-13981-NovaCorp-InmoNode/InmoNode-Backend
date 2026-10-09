package com.novacorp.inmonode.inmonodebackend.financial.application.internal.queryservices;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetFieldPortfolioQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PortfolioProject;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.FieldPortfolioQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class FieldPortfolioQueryServiceImpl implements FieldPortfolioQueryService {

    private final ProjectRepository projectRepository;
    private final LotRepository lotRepository;

    public FieldPortfolioQueryServiceImpl(ProjectRepository projectRepository, LotRepository lotRepository) {
        this.projectRepository = projectRepository;
        this.lotRepository = lotRepository;
    }

    @Override
    public List<PortfolioProject> handle(GetFieldPortfolioQuery query) {
        var projects = projectRepository.findAllPublished();
        var lotsByProject = lotRepository.findByProjectIds(
                        projects.stream().map(Project::getId).map(Objects::requireNonNull).toList())
                .stream()
                .collect(Collectors.groupingBy(Lot::getProjectId));
        return projects.stream()
                .map(project -> new PortfolioProject(project, lotsByProject.getOrDefault(project.getId(), List.of())))
                .toList();
    }
}
