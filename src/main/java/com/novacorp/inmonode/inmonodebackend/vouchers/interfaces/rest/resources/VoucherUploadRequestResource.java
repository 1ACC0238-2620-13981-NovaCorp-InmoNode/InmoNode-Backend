package com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/**
 * @param voucherId     id generated on the device for the voucher; send the same one when retrying
 * @param reservationId the reservation the voucher pays, as synchronized by the agent
 * @param contentType   image/jpeg, image/png or application/pdf
 * @param sizeBytes     exact size of the file, up to 5 MB (5242880 bytes)
 */
public record VoucherUploadRequestResource(
        @NotNull UUID voucherId,
        @NotNull UUID reservationId,
        @NotBlank String contentType,
        @NotNull @Positive Long sizeBytes) {
}
