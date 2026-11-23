package com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature;

/** The signature provider failed or answered something unexpected. Its message never carries the API token. */
public class SignatureProviderException extends RuntimeException {

    public SignatureProviderException(String message) {
        super(message);
    }

    public SignatureProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
