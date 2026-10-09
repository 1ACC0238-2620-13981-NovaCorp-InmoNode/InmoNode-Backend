package com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * "Comprobante de pago recibido" (Context Map): the payment voucher of a reservation reached the server, with its
 * file in the file repository. Published once, when the voucher is registered, inside the same transaction, so a
 * synchronous listener stores the payment evidence together with the voucher or neither is stored.
 *
 * @param voucherId         id the device generated for the voucher; the reference of the payment evidence
 * @param reservationId     id the device generated for the reservation, shared by every context
 * @param amount            amount paid, as read from the voucher
 * @param operationDate     day of the bank operation
 * @param operationCode     operation number the bank printed on the voucher
 * @param manuallyCorrected whether the agent corrected the data the OCR read
 * @param objectKey         where the voucher file lives in the file repository
 * @param receivedAt        when the server received the voucher
 */
public record PaymentVoucherReceivedEvent(UUID voucherId, UUID reservationId, BigDecimal amount, String currency,
                                          LocalDate operationDate, String operationCode, boolean manuallyCorrected,
                                          String objectKey, Instant receivedAt) {
}
