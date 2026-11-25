package com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments;

/** The payment gateway could not be reached, or it refused the operation. */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }

    public PaymentGatewayException(String message) {
        super(message);
    }
}
