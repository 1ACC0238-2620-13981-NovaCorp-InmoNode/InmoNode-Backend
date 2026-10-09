package com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.rest.resources;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Voucher receipt data; verification decisions and download links remain in reservation payment evidences. */
public record VoucherHistoryResource(UUID voucherId, UUID reservationId, BigDecimal amount, String currency,
                                     LocalDate operationDate, String operationCode, String contentType,
                                     long sizeBytes, boolean manuallyCorrected, String status, Instant receivedAt) { }
