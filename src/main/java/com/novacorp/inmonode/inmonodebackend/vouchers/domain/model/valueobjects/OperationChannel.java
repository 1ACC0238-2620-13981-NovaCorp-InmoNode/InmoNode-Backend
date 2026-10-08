package com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects;

/**
 * Where the reservation a voucher pays for was made: in the field by an agent, or on the web by a buyer.
 */
public enum OperationChannel {
    FIELD,
    WEB
}
