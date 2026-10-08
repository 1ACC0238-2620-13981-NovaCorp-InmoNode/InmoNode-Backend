package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

/**
 * Where a payment evidence comes from: a voucher sent from the field app, or a payment gateway on the web portal.
 */
public enum PaymentEvidenceSource {
    VOUCHER,
    GATEWAY
}
