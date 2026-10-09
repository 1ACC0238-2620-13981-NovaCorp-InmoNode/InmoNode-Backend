package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.*;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetAdminProjectLotsQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.*;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.*;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform.*;
import com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/catalog")
@PreAuthorize("hasRole('CATALOG_ADMIN')")
@Tag(name = "Real estate catalog", description = "Projects, stages and individual lot publication (US-51/52/53)")
public class RealEstateCatalogController {
    private final ProjectCommandService projects;
    private final ProjectQueryService projectQueries;
    private final LotCommandService lots;
    public RealEstateCatalogController(ProjectCommandService projects, ProjectQueryService projectQueries, LotCommandService lots) {
        this.projects = projects; this.projectQueries = projectQueries; this.lots = lots;
    }
    @PostMapping("/projects")
    @ApiResponse(responseCode = "201", description = "Draft project created with its stages")
    @Operation(summary = "Create a draft project with stages (US-51)")
    public ResponseEntity<?> createProject(@Valid @RequestBody CreateCatalogProjectResource resource) {
        return ResponseEntityAssembler.toResponseEntityFromResult(projects.handle(CatalogResourceAssembler.toCommand(resource)),
                ProjectResourceAssembler::toResourceFromEntity, HttpStatus.CREATED);
    }
    @PostMapping("/projects/{projectId}/lots")
    @ApiResponse(responseCode = "201", description = "Draft lot created; publication is a separate operation")
    @Operation(summary = "Register a complete individual lot as DRAFT (US-52)",
            description = "Stage must belong to the project; code is unique. Invalid polygons return 400. Does not offer the lot for reservation.")
    public ResponseEntity<?> register(@PathVariable Long projectId, @Valid @RequestBody RegisterLotResource resource) {
        return ResponseEntityAssembler.toResponseEntityFromResult(lots.handle(CatalogResourceAssembler.toCommand(projectId, resource)),
                CatalogResourceAssembler::toResource, HttpStatus.CREATED);
    }
    @GetMapping("/projects/{projectId}/lots")
    @Operation(summary = "List all project lots, including drafts, for catalog administration")
    public ResponseEntity<?> list(@PathVariable Long projectId) {
        return ResponseEntityAssembler.toResponseEntityFromResult(projectQueries.handle(new GetAdminProjectLotsQuery(projectId)),
                result -> result.stream().map(CatalogResourceAssembler::toResource).toList(), HttpStatus.OK);
    }
    @PutMapping("/projects/{projectId}/publish")
    @Operation(summary = "Publish the project after loading its lots")
    public ResponseEntity<?> publishProject(@PathVariable Long projectId) {
        return ResponseEntityAssembler.toResponseEntityFromResult(projects.handle(new PublishProjectCommand(projectId)),
                ProjectResourceAssembler::toResourceFromEntity, HttpStatus.OK);
    }
    @PutMapping("/lots/{lotId}/publish")
    @Operation(summary = "Publish a draft lot in a published project (US-53)",
            description = "Validates the project, stage, price, dimensions and polygon. Becomes AVAILABLE; repeats do not reset blocked, reserved or sold lots.")
    public ResponseEntity<?> publishLot(@PathVariable Long lotId) {
        return ResponseEntityAssembler.toResponseEntityFromResult(lots.handle(new PublishLotCommand(lotId)),
                CatalogResourceAssembler::toResource, HttpStatus.OK);
    }
}
