package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetFieldPortfolioQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.FieldPortfolioQueryService;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.FieldPortfolioResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform.FieldPortfolioResourceAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Data exchange with the offline field app. The upload of reservations made offline (US-11, US-12)
 * will join this controller.
 */
@RestController
@RequestMapping("/api/v1/field-sync")
@Tag(name = "Field sync", description = "Catalog download and synchronization for the field agent app")
public class FieldSyncController {

    private final FieldPortfolioQueryService fieldPortfolioQueryService;

    public FieldSyncController(FieldPortfolioQueryService fieldPortfolioQueryService) {
        this.fieldPortfolioQueryService = fieldPortfolioQueryService;
    }

    @GetMapping("/portfolio")
    @PreAuthorize("hasRole('FIELD_AGENT')")
    @Operation(summary = "Download the portfolio for offline work (US-02)",
            description = "Field agents only (FIELD_AGENT). Every published project with its financing rules and its "
                    + "lots as GeoJSON. The response has an ETag: send it back in If-None-Match and an unchanged "
                    + "portfolio is answered with 304 Not Modified and no body (US-39).")
    public FieldPortfolioResource getPortfolio() {
        return FieldPortfolioResourceAssembler.toResourceFromPortfolio(
                fieldPortfolioQueryService.handle(new GetFieldPortfolioQuery()));
    }
}
