package com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments;

/** A notification does not carry a valid, recent signature of the payment gateway. */
public class InvalidGatewaySignatureException extends RuntimeException {

    public InvalidGatewaySignatureException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidGatewaySignatureException(String message) {
        super(message);
    }
}
