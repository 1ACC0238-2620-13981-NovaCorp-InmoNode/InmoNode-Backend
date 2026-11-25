package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

/**
 * What was done with a notification of the gateway (US-55, Scenario 2).
 *
 * @param outcome CREDITED, DUPLICATE, UNAPPLIED, REJECTED, IGNORED, FAILED or CANCELED
 */
public record PaymentGatewayEventResource(String eventId, String outcome) {
}
