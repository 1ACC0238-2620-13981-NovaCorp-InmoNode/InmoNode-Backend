package com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature;

/** A callback that the signature provider did not sign, or signed too long ago. */
public class InvalidSignatureCallbackException extends RuntimeException {

    public InvalidSignatureCallbackException(String message) {
        super(message);
    }
}
