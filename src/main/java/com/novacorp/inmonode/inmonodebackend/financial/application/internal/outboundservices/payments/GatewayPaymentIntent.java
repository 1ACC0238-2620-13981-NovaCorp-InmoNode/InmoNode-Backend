package com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments;

import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/**
 * A charge as the payment gateway has it.
 *
 * @param amountReceived what was charged, in the smallest unit of the currency
 * @param currency       ISO 4217 code, in capitals
 * @param chargeId       the gateway's id of the latest attempt, when there was one
 * @param clientSecret   the secret the app confirms the charge with; absent when it is not shown
 * @param lastFailure    why the latest attempt was declined, when it was
 */
public record GatewayPaymentIntent(String id, GatewayPaymentStatus status, long amount, long amountReceived,
                                   String currency, @Nullable String chargeId, @Nullable String clientSecret,
                                   @Nullable String lastFailure, Map<String, String> metadata) {

    public GatewayPaymentIntent {
        currency = currency == null ? "" : currency.toUpperCase(Locale.ROOT);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public boolean isSucceeded() {
        return status == GatewayPaymentStatus.SUCCEEDED;
    }
}
