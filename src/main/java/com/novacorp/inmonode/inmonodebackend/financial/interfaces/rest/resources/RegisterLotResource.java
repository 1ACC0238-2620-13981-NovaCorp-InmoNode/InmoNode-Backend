package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record RegisterLotResource(@NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 80) String stageName,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal area,
        @DecimalMin(value = "0", inclusive = false) BigDecimal front,
        @DecimalMin(value = "0", inclusive = false) BigDecimal depth,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal price,
        @NotEmpty List<List<List<Double>>> polygon) {}
