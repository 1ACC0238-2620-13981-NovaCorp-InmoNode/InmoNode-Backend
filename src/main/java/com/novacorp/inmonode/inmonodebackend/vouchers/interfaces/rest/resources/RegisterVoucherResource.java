package com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.rest.resources;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * @param voucherId         the id used to ask for the upload URL; send the same one when retrying
 * @param reservationId     the reservation the voucher pays, as synchronized by the agent
 * @param contentType       the type declared for the upload: image/jpeg, image/png or application/pdf
 * @param sizeBytes         the size declared for the upload, up to 5 MB (5242880 bytes)
 * @param amount            amount paid, as read from the voucher
 * @param currency          ISO 4217 code, such as PEN or USD
 * @param operationDate     day of the bank operation, not in the future
 * @param operationCode     operation number the bank printed on the voucher, up to 50 characters
 * @param ocrConfidence     how sure the OCR was, from 0 to 1; optional
 * @param manuallyCorrected whether the agent corrected the data the OCR read
 */
public record RegisterVoucherResource(
        @NotNull UUID voucherId,
        @NotNull UUID reservationId,
        @NotBlank String contentType,
        @NotNull @Positive Long sizeBytes,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
        @NotBlank String currency,
        @NotNull LocalDate operationDate,
        @NotBlank String operationCode,
        BigDecimal ocrConfidence,
        @NotNull Boolean manuallyCorrected) {
}
