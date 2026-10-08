package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.financial.domain.services.LotCommandService;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.GeoJsonFeatureCollectionResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform.ImportLotsCommandFromResourceAssembler;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform.LotImportResultResourceAssembler;
import com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/lots")
@Tag(name = "Projects", description = "Catalog of real estate projects and their lots")
public class ProjectLotsController {

    static final String GEO_JSON = "application/geo+json";

    private final LotCommandService lotCommandService;

    public ProjectLotsController(LotCommandService lotCommandService) {
        this.lotCommandService = lotCommandService;
    }

    @PostMapping(consumes = {MediaType.APPLICATION_JSON_VALUE, GEO_JSON})
    @PreAuthorize("hasRole('CATALOG_ADMIN')")
    @Operation(summary = "Load the lots of the project plan (US-53)",
            description = "Back-office only (CATALOG_ADMIN). The body is a GeoJSON FeatureCollection with one Polygon "
                    + "feature per lot ([longitude, latitude], WGS84) and properties code, area and price (front and "
                    + "depth optional). A lot with a repeated code or invalid data or geometry is rejected on its own; "
                    + "the valid ones are saved and the response lists each rejection with its reason.")
    public ResponseEntity<?> importLots(@PathVariable Long projectId,
                                        @Valid @RequestBody GeoJsonFeatureCollectionResource plan) {
        var result = lotCommandService.handle(
                ImportLotsCommandFromResourceAssembler.toCommandFromResource(projectId, plan));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, LotImportResultResourceAssembler::toResourceFromResult, HttpStatus.OK);
    }
}
