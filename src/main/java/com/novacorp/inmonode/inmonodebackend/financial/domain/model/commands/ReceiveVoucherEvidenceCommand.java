package com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Attaches a voucher sent from the field app to its reservation as payment evidence (US-20).
 *
 * @param reservationId     id the device generated for the reservation (its {@code sourceEventId})
 * @param voucherId         id the device generated for the voucher; the reference of the evidence
 * @param manuallyCorrected whether the agent corrected the data the OCR read
 * @param objectKey         where the voucher file lives in the file repository
 * @param submittedAt       when the voucher reached the server; the evidence is on time if the block was still active
 */
public record ReceiveVoucherEvidenceCommand(UUID reservationId, UUID voucherId, Money amount, LocalDate operationDate,
                                            String operationCode, boolean manuallyCorrected, String objectKey,
                                            Instant submittedAt) {
}
