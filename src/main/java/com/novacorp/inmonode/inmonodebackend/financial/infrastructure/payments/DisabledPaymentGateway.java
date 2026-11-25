package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.payments;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments.GatewayPaymentIntent;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments.PaymentGateway;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments.PaymentGatewayException;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.InstallmentPaymentIntent;
import org.jspecify.annotations.Nullable;

/**
 * Stands in for the payment gateway when it has no credentials, as in CI and in environments without them: nothing
 * can be charged, so starting a payment answers that payments are unavailable.
 */
public class DisabledPaymentGateway implements PaymentGateway {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public @Nullable String publishableKey() {
        return null;
    }

    @Override
    public GatewayPaymentIntent open(InstallmentPaymentIntent intent) {
        throw disabled();
    }

    @Override
    public GatewayPaymentIntent retrieve(String providerId) {
        throw disabled();
    }

    @Override
    public GatewayPaymentIntent cancel(String providerId) {
        throw disabled();
    }

    private static PaymentGatewayException disabled() {
        return new PaymentGatewayException("the payment gateway is not configured");
    }
}
