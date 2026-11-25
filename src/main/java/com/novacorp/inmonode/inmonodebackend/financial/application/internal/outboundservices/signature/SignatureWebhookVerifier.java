package com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature;

/**
 * Checks that a callback was signed by the signature provider and reads it, without calling the provider.
 */
public interface SignatureWebhookVerifier {

    /** Whether its secret is configured. */
    boolean isEnabled();

    /**
     * @throws InvalidSignatureCallbackException when the signature is missing, wrong or too old, or the body is not
     *                                           a callback
     */
    SignatureEvent verify(String payload, String signature);
}
