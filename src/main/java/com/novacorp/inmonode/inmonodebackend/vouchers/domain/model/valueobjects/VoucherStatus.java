package com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects;

/**
 * Status of a voucher on the server. The earlier ones (captured, read, waiting for connection) only exist on the
 * device: a voucher reaches the server already synchronized, and its verification belongs to financial.
 */
public enum VoucherStatus {
    SYNCED
}
