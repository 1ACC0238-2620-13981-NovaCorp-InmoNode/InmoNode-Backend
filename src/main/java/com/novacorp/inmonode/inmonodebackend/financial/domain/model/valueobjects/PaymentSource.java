package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

/**
 * Where the payment of an installment came from.
 */
public enum PaymentSource {
    /** The finance back office registered it (US-23). */
    BACK_OFFICE,
    /** The payment gateway charged it and confirmed it with its webhook (US-55). */
    GATEWAY
}
