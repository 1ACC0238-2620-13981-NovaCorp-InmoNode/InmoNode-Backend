package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.PublishProjectCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ProjectCommandService;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.CreateProjectResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform.CreateProjectCommandFromResourceAssembler;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform.ProjectResourceAssembler;
import com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = "Projects", description = "Catalog of real estate projects and their lots")
public class ProjectsController {

    private final ProjectCommandService projectCommandService;

    public ProjectsController(ProjectCommandService projectCommandService) {
        this.projectCommandService = projectCommandService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CATALOG_ADMIN')")
    @Operation(summary = "Register a real estate project as a draft (US-53)",
            description = "Back-office only (CATALOG_ADMIN). The project is published once it has lots.")
    public ResponseEntity<?> create(@Valid @RequestBody CreateProjectResource resource) {
        var result = projectCommandService.handle(
                CreateProjectCommandFromResourceAssembler.toCommandFromResource(resource));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, ProjectResourceAssembler::toResourceFromEntity, HttpStatus.CREATED);
    }

    @PostMapping("/{projectId}/publish")
    @PreAuthorize("hasRole('CATALOG_ADMIN')")
    @Operation(summary = "Publish a project in the catalog (US-53)",
            description = "Back-office only (CATALOG_ADMIN). Requires at least one lot (422 otherwise). "
                    + "Publishing an already published project returns it unchanged.")
    public ResponseEntity<?> publish(@PathVariable Long projectId) {
        var result = projectCommandService.handle(new PublishProjectCommand(projectId));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, ProjectResourceAssembler::toResourceFromEntity, HttpStatus.OK);
    }
}
