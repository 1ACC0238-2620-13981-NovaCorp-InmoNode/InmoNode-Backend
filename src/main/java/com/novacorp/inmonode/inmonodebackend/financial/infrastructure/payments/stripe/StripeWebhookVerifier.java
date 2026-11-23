package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.payments.stripe;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments.InvalidGatewaySignatureException;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments.PaymentGatewayEvent;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments.PaymentGatewayWebhookVerifier;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Verifies the {@code Stripe-Signature} of a webhook with the endpoint's signing secret, and rejects it when it is
 * older than {@value #TOLERANCE_SECONDS} seconds, so a captured notification cannot be replayed later. The payload
 * is read even when Stripe sends it in an API version other than the SDK's, since only a few fields are needed.
 */
public class StripeWebhookVerifier implements PaymentGatewayWebhookVerifier {

    static final long TOLERANCE_SECONDS = 300;

    private final @Nullable String signingSecret;

    public StripeWebhookVerifier(@Nullable String signingSecret) {
        this.signingSecret = signingSecret == null || signingSecret.isBlank() ? null : signingSecret;
    }

    @Override
    public boolean isEnabled() {
        return signingSecret != null;
    }

    @Override
    public PaymentGatewayEvent verify(String payload, @Nullable String signature) {
        if (signingSecret == null) {
            throw new IllegalStateException("the webhook signing secret is not configured");
        }
        if (payload == null || signature == null || signature.isBlank()) {
            throw new InvalidGatewaySignatureException("the notification is not signed");
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, signingSecret, TOLERANCE_SECONDS);
        } catch (SignatureVerificationException ex) {
            throw new InvalidGatewaySignatureException("the signature is wrong or too old", ex);
        } catch (RuntimeException ex) {
            throw new InvalidGatewaySignatureException("the notification is not a Stripe event", ex);
        }
        if (event.getId() == null || event.getType() == null) {
            throw new InvalidGatewaySignatureException("the notification is not a Stripe event");
        }
        var kind = switch (event.getType()) {
            case "payment_intent.succeeded" -> PaymentGatewayEvent.Kind.PAYMENT_SUCCEEDED;
            case "payment_intent.payment_failed" -> PaymentGatewayEvent.Kind.PAYMENT_FAILED;
            case "payment_intent.canceled" -> PaymentGatewayEvent.Kind.PAYMENT_CANCELED;
            default -> PaymentGatewayEvent.Kind.OTHER;
        };
        if (!(object(event) instanceof PaymentIntent paymentIntent)) {
            return new PaymentGatewayEvent(event.getId(), event.getType(), PaymentGatewayEvent.Kind.OTHER, null,
                    null, null, null);
        }
        Map<String, String> metadata = paymentIntent.getMetadata() == null ? Map.of() : paymentIntent.getMetadata();
        var error = paymentIntent.getLastPaymentError();
        return new PaymentGatewayEvent(event.getId(), event.getType(), kind, paymentIntent.getId(),
                metadata.get(StripePaymentGateway.APP_KEY), metadata.get(StripePaymentGateway.INTENT_KEY),
                error == null ? null : error.getMessage());
    }

    private static @Nullable StripeObject object(Event event) {
        var deserializer = event.getDataObjectDeserializer();
        if (deserializer == null) return null;
        var object = deserializer.getObject();
        if (object.isPresent()) return object.get();
        try {
            // Sent in another API version: the few fields read here are the same in all of them.
            return deserializer.deserializeUnsafe();
        } catch (EventDataObjectDeserializationException | RuntimeException ex) {
            return null;
        }
    }
}
