package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CoOwnerResource(@NotBlank @Size(max = 200) String fullName,
                              @NotBlank String documentType, @NotBlank String documentNumber) {}
