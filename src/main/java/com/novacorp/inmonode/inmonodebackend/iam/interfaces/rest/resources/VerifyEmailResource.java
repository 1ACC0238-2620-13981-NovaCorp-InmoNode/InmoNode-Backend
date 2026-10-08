package com.novacorp.inmonode.inmonodebackend.iam.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailResource(@NotBlank String token) {
}
