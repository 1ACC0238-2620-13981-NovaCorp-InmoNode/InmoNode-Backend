package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetProjectLotsQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotFilters;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.MapBounds;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.LotCommandService;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ProjectQueryService;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.GeoJsonFeatureCollectionResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform.ImportLotsCommandFromResourceAssembler;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform.LotFeatureCollectionResourceAssembler;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform.LotImportResultResourceAssembler;
import com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/lots")
@Tag(name = "Projects", description = "Catalog of real estate projects and their lots")
public class ProjectLotsController {

    static final String GEO_JSON = "application/geo+json";

    private final LotCommandService lotCommandService;
    private final ProjectQueryService projectQueryService;

    public ProjectLotsController(LotCommandService lotCommandService, ProjectQueryService projectQueryService) {
        this.lotCommandService = lotCommandService;
        this.projectQueryService = projectQueryService;
    }

    @GetMapping
    @Operation(summary = "Get and filter the lots of a project as GeoJSON (US-05, US-15, US-16)",
            description = "A FeatureCollection with one Polygon per lot and its code, measures, price and status. "
                    + "Public for published projects; for a draft, only CATALOG_ADMIN (404 otherwise). "
                    + "Optional inclusive minArea/maxArea (m2), minPrice/maxPrice (PEN), status and WGS84 viewport "
                    + "west/south/east/north (all four required together). Invalid ranges return 400; no matches "
                    + "return an empty FeatureCollection. Omitting filters returns the complete map.")
    public ResponseEntity<?> getLots(@PathVariable Long projectId,
                                    @RequestParam(required = false) BigDecimal minArea,
                                    @RequestParam(required = false) BigDecimal maxArea,
                                    @RequestParam(required = false) BigDecimal minPrice,
                                    @RequestParam(required = false) BigDecimal maxPrice,
                                    @RequestParam(required = false) LotStatus status,
                                    @RequestParam(required = false) Double west,
                                    @RequestParam(required = false) Double south,
                                    @RequestParam(required = false) Double east,
                                    @RequestParam(required = false) Double north) {
        MapBounds bounds = null;
        if (west != null || south != null || east != null || north != null) {
            if (west == null || south == null || east == null || north == null) {
                throw new IllegalArgumentException("west, south, east and north must be supplied together");
            }
            bounds = new MapBounds(west, south, east, north);
        }
        var filters = new LotFilters(minArea, maxArea, minPrice, maxPrice, status, bounds);
        var result = projectQueryService.handle(new GetProjectLotsQuery(projectId, filters));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, LotFeatureCollectionResourceAssembler::toResourceFromLots, HttpStatus.OK);
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
