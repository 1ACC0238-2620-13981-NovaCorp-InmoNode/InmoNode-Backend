package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param latitude  WGS84 latitude of the project on the map; send it together with {@code longitude} or omit both
 * @param longitude WGS84 longitude of the project on the map
 */
public record CreateProjectResource(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 255) String location,
        @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @Size(max = 500) String coverImageUrl,
        @NotNull @Valid FinancingRulesResource financingRules) {
}
