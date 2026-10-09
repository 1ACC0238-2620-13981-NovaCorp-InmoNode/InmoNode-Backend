package com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.shared.application.monitoring.DatabaseHealthProbe;
import com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest.resources.HealthResource;
import com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest.transform.HealthResourceAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
@Tag(name = "Health", description = "API and main database availability")
public class HealthController {

    private final DatabaseHealthProbe databaseHealthProbe;

    public HealthController(DatabaseHealthProbe databaseHealthProbe) {
        this.databaseHealthProbe = databaseHealthProbe;
    }

    @GetMapping
    @SecurityRequirements
    @Operation(summary = "Check API and database availability (US-34)",
            description = "Public endpoint for load balancers. Checks the database on each request without exposing connection details.")
    @ApiResponse(responseCode = "200", description = "API and database are available")
    @ApiResponse(responseCode = "503", description = "The main database is unavailable")
    public ResponseEntity<HealthResource> getHealth() {
        var available = databaseHealthProbe.isAvailable();
        return ResponseEntity.status(available ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .cacheControl(CacheControl.noStore())
                .body(HealthResourceAssembler.toResourceFromDatabaseAvailability(available));
    }
}
