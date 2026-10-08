package com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.rest.resources;

import java.time.Instant;
import java.util.UUID;

/**
 * @param status     SYNCED: the server has the voucher; mark it as synchronized on the device
 * @param result     RECEIVED (stored now) or DUPLICATE (already registered; this is the original voucher)
 * @param receivedAt when the server received the voucher
 */
public record VoucherRegistrationResource(UUID voucherId, UUID reservationId, String status, String result,
                                          Instant receivedAt) {
}
