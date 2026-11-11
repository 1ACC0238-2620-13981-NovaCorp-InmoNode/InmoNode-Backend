package com.novacorp.inmonode.inmonodebackend.iam.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The new password follows the same rules as the one chosen at registration.
 */
public record ResetPasswordResource(
        @NotBlank String token,
        @NotBlank @Size(min = 8, max = 72) String newPassword) {
}
