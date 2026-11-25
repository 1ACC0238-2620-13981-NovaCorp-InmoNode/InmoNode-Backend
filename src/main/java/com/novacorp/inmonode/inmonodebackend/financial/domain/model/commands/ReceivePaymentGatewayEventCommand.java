package com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands;

import org.jspecify.annotations.Nullable;

/**
 * The payment gateway notifies what happened to a charge (US-55, Scenario 2).
 *
 * @param payload   the body exactly as received: its signature is computed over these bytes
 * @param signature the gateway's signature header; absent when the request is not from the gateway
 */
public record ReceivePaymentGatewayEventCommand(String payload, @Nullable String signature) {

    public ReceivePaymentGatewayEventCommand {
        payload = payload == null ? "" : payload;
    }
}
