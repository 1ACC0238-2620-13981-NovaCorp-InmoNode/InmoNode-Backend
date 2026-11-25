package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.payments.stripe;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments.InvalidGatewaySignatureException;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments.PaymentGatewayEvent;
import com.stripe.Stripe;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.novacorp.inmonode.inmonodebackend.financial.infrastructure.payments.stripe.StripeSignatures.paymentIntentEvent;
import static com.novacorp.inmonode.inmonodebackend.financial.infrastructure.payments.stripe.StripeSignatures.sign;
import static org.junit.jupiter.api.Assertions.*;

/** The signature check of US-55, Scenario 2, with signatures computed as Stripe does and no network. */
class StripeWebhookVerifierTest {

    private static final String SECRET = "whsec_unit-test";
    private static final String METADATA = "{\"app\":\"inmonode\",\"installmentPaymentIntentId\":\"5f0c7a52-3a8e-4d2b-9a61-0c3f2e7b9d14\"}";

    private final StripeWebhookVerifier verifier = new StripeWebhookVerifier(SECRET);

    @Test
    void aSignedNotificationIsReadWithTheChargeAndItsMetadata() {
        var payload = paymentIntentEvent("evt_1", "payment_intent.succeeded", Stripe.API_VERSION, "pi_1", 318_823,
                "succeeded", METADATA, "null");

        var event = verifier.verify(payload, sign(payload, SECRET, Instant.now()));

        assertEquals("evt_1", event.id());
        assertEquals("payment_intent.succeeded", event.type());
        assertEquals(PaymentGatewayEvent.Kind.PAYMENT_SUCCEEDED, event.kind());
        assertEquals("pi_1", event.providerId());
        assertEquals("inmonode", event.app());
        assertTrue(event.isOfThisApp());
        assertEquals("5f0c7a52-3a8e-4d2b-9a61-0c3f2e7b9d14", event.intentReference());
        assertNull(event.failureMessage());
    }

    @Test
    void aNotificationInAnotherApiVersionIsReadToo() {
        var payload = paymentIntentEvent("evt_2", "payment_intent.payment_failed", "2024-06-20", "pi_2", 318_823,
                "requires_payment_method", METADATA,
                "{\"type\":\"card_error\",\"code\":\"card_declined\",\"message\":\"Your card was declined.\"}");

        var event = verifier.verify(payload, sign(payload, SECRET, Instant.now()));

        assertEquals(PaymentGatewayEvent.Kind.PAYMENT_FAILED, event.kind());
        assertEquals("pi_2", event.providerId());
        assertEquals("Your card was declined.", event.failureMessage());
    }

    @Test
    void notificationsAboutOtherThingsAreReadAsOther() {
        var canceled = paymentIntentEvent("evt_3", "payment_intent.canceled", Stripe.API_VERSION, "pi_3", 500,
                "canceled", "{\"app\":\"mindflow\"}", "null");
        var created = paymentIntentEvent("evt_4", "payment_intent.created", Stripe.API_VERSION, "pi_4", 500,
                "requires_payment_method", "{}", "null");
        var charge = """
                {"id":"evt_5","object":"event","api_version":"%s","created":1,"type":"charge.succeeded",\
                "data":{"object":{"id":"ch_1","object":"charge","amount":500,"currency":"pen"}}}"""
                .formatted(Stripe.API_VERSION);

        var canceledEvent = verifier.verify(canceled, sign(canceled, SECRET, Instant.now()));
        assertEquals(PaymentGatewayEvent.Kind.PAYMENT_CANCELED, canceledEvent.kind());
        assertFalse(canceledEvent.isOfThisApp());
        assertEquals(PaymentGatewayEvent.Kind.OTHER, verifier.verify(created, sign(created, SECRET, Instant.now())).kind());
        var chargeEvent = verifier.verify(charge, sign(charge, SECRET, Instant.now()));
        assertEquals(PaymentGatewayEvent.Kind.OTHER, chargeEvent.kind());
        assertNull(chargeEvent.providerId());
    }

    @Test
    void aNotificationNotSignedByStripeIsRefused() {
        var payload = paymentIntentEvent("evt_1", "payment_intent.succeeded", Stripe.API_VERSION, "pi_1", 318_823,
                "succeeded", METADATA, "null");
        var signature = sign(payload, SECRET, Instant.now());

        assertThrows(InvalidGatewaySignatureException.class,
                () -> verifier.verify(payload, sign(payload, "whsec_other", Instant.now())));
        assertThrows(InvalidGatewaySignatureException.class,
                () -> verifier.verify(payload.replace("318823", "1"), signature));
        assertThrows(InvalidGatewaySignatureException.class, () -> verifier.verify(payload, null));
        assertThrows(InvalidGatewaySignatureException.class, () -> verifier.verify(payload, "garbage"));
        assertThrows(InvalidGatewaySignatureException.class,
                () -> verifier.verify("not json", sign("not json", SECRET, Instant.now())));
    }

    @Test
    void aNotificationOlderThanFiveMinutesIsRefused() {
        var payload = paymentIntentEvent("evt_1", "payment_intent.succeeded", Stripe.API_VERSION, "pi_1", 318_823,
                "succeeded", METADATA, "null");

        assertThrows(InvalidGatewaySignatureException.class,
                () -> verifier.verify(payload, sign(payload, SECRET, Instant.now().minusSeconds(301))));
        assertDoesNotThrow(() -> verifier.verify(payload, sign(payload, SECRET, Instant.now().minusSeconds(240))));
    }

    @Test
    void withoutTheSigningSecretNothingIsTrusted() {
        var disabled = new StripeWebhookVerifier(" ");

        assertFalse(disabled.isEnabled());
        assertTrue(verifier.isEnabled());
        assertThrows(IllegalStateException.class, () -> disabled.verify("{}", "t=1,v1=abc"));
    }
}
