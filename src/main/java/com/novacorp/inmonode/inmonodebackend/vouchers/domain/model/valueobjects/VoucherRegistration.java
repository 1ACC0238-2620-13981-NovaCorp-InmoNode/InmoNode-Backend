package com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects;

import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.aggregates.Voucher;

/**
 * Answer to a voucher registration: the voucher as the server keeps it, and whether this request stored it.
 *
 * @param result RECEIVED when stored now; DUPLICATE when it was already registered (idempotent by the device id),
 *               answered with the original voucher
 */
public record VoucherRegistration(Voucher voucher, Result result) {

    public enum Result {
        RECEIVED,
        DUPLICATE
    }
}
