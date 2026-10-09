package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record CreateCatalogProjectResource(@NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 255) String location,
        @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @Size(max = 500) String coverImageUrl,
        @NotNull @Valid FinancingRulesResource financingRules,
        @NotEmpty @Size(max = 50) List<@NotBlank @Size(max = 80) String> stages) {}
