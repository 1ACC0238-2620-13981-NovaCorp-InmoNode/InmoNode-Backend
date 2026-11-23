package com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.InstallmentPaymentIntent;
import org.jspecify.annotations.Nullable;

/**
 * The payment gateway that charges installments (US-55). Every charge it opens carries {@link #APP} and the id of
 * its intent, so the notifications of other apps and environments sharing the gateway account are told apart.
 */
public interface PaymentGateway {

    /** Tags the charges of InmoNode in the gateway. */
    String APP = "inmonode";

    /** Without credentials there are no payments: starting one answers that they are unavailable. */
    boolean isEnabled();

    /** The public key the app needs to show the gateway's payment form; empty when disabled. */
    @Nullable String publishableKey();

    /**
     * Opens the charge of {@code intent} for its amount and currency. Opening the same intent again returns the
     * charge already opened instead of a second one.
     *
     * @return the charge, with the secret the app confirms it with
     * @throws PaymentGatewayException when the gateway cannot be reached or refuses it
     */
    GatewayPaymentIntent open(InstallmentPaymentIntent intent);

    /**
     * Reads the charge as the gateway has it now, with the secret the app confirms it with.
     *
     * @throws PaymentGatewayException when the gateway cannot be reached or does not know it
     */
    GatewayPaymentIntent retrieve(String providerId);

    /**
     * Cancels a charge that was not paid, so it can no longer be paid.
     *
     * @throws PaymentGatewayException when the gateway cannot be reached or the charge can no longer be canceled
     */
    GatewayPaymentIntent cancel(String providerId);
}
