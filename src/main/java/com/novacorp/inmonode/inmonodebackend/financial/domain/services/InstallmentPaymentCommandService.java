package com.novacorp.inmonode.inmonodebackend.financial.domain.services;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ReceivePaymentGatewayEventCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.StartInstallmentPaymentCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.InstallmentPaymentStart;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PaymentGatewayEventRecord;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;

/**
 * US-55: installments paid online through the payment gateway, in two steps: the buyer starts the payment, and only
 * the gateway's confirmation credits it.
 */
public interface InstallmentPaymentCommandService {

    /**
     * Opens the payment of an installment of the buyer for exactly what it owes, or returns the one already open,
     * which is also what the same idempotency key returns (Scenario 3). Fails with {@code PAYMENTS_UNAVAILABLE}
     * without a gateway, {@code ACCOUNT_STATEMENT_NOT_FOUND} or {@code INSTALLMENT_NOT_FOUND} (also for another
     * buyer's), {@code INSTALLMENT_CONFLICT} when it is already paid, {@code PAYMENT_INTENT_CONFLICT} when the key
     * was used for another installment and {@code PAYMENT_GATEWAY_ERROR} when the gateway fails.
     */
    Result<InstallmentPaymentStart, ApplicationError> handle(StartInstallmentPaymentCommand command);

    /**
     * Verifies a notification of the gateway and acts on it once (Scenario 2): a confirmed charge credits its
     * installment, or is kept as unapplied and finance is told to refund it when the installment no longer owes it.
     * A notification received again is answered with its first outcome. Fails with {@code PAYMENTS_UNAVAILABLE}
     * without a gateway, {@code INVALID_WEBHOOK_SIGNATURE} when it is not signed by the gateway and
     * {@code PAYMENT_GATEWAY_ERROR} when the gateway cannot confirm the charge, so it is sent again later.
     */
    Result<PaymentGatewayEventRecord, ApplicationError> handle(ReceivePaymentGatewayEventCommand command);
}
