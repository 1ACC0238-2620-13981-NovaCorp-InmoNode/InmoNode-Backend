package com.novacorp.inmonode.inmonodebackend.financial.application.internal.commandservices;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.CreateProjectCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.PublishProjectCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ProjectCommandService;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectCommandServiceImpl implements ProjectCommandService {

    private final ProjectRepository projectRepository;
    private final LotRepository lotRepository;

    public ProjectCommandServiceImpl(ProjectRepository projectRepository, LotRepository lotRepository) {
        this.projectRepository = projectRepository;
        this.lotRepository = lotRepository;
    }

    @Override
    @Transactional
    public Result<Project, ApplicationError> handle(CreateProjectCommand command) {
        var project = Project.create(command.name(), command.location(), command.coordinates(),
                command.coverImageUrl(), command.financingRules());
        return Result.success(projectRepository.save(project));
    }

    @Override
    @Transactional
    public Result<Project, ApplicationError> handle(PublishProjectCommand command) {
        var projectId = command.projectId();
        var project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            return Result.failure(ApplicationError.notFound("project", String.valueOf(projectId)));
        }
        if (!project.publish(lotRepository.countByProjectId(projectId))) {
            return Result.failure(ApplicationError.businessRuleViolation("project-publication",
                    "A project can be published only once it has at least one lot"));
        }
        return Result.success(projectRepository.save(project));
    }
}
