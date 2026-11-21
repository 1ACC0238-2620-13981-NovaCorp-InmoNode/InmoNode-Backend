package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/**
 * @param documentId  id generated on the device for the photo; send the same one when retrying
 * @param contentType image/jpeg or image/png
 * @param sizeBytes   exact size of the file, up to 5 MB (5242880 bytes)
 */
public record IdentityDocumentFileResource(
        @NotNull UUID documentId,
        @NotBlank String contentType,
        @NotNull @Positive Long sizeBytes) {
}
