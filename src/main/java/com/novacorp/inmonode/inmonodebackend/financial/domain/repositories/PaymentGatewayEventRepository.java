package com.novacorp.inmonode.inmonodebackend.financial.domain.repositories;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PaymentGatewayEventRecord;

import java.util.Optional;

/**
 * The log of the gateway's notifications (US-55, Scenario 2).
 */
public interface PaymentGatewayEventRepository {

    Optional<PaymentGatewayEventRecord> findById(String eventId);

    /**
     * Records a notification once.
     *
     * @return false when it was already recorded, which keeps the first outcome
     */
    boolean record(PaymentGatewayEventRecord event);
}
