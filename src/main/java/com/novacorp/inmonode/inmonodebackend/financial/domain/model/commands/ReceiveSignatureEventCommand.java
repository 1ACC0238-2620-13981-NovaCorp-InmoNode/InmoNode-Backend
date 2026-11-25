package com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands;

import org.jspecify.annotations.Nullable;

/**
 * The signature provider tells what happened to a contract sent to signature (US-56).
 *
 * @param payload   the body exactly as received: its signature is computed over these bytes
 * @param signature the provider's signature header; absent when the request is not from the provider
 */
public record ReceiveSignatureEventCommand(String payload, @Nullable String signature) {

    public ReceiveSignatureEventCommand {
        payload = payload == null ? "" : payload;
    }
}
