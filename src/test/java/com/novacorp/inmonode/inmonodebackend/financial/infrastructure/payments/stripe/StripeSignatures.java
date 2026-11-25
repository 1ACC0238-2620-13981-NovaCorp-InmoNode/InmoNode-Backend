package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.payments.stripe;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.HexFormat;

/** Signs test webhooks as Stripe does: HMAC-SHA256 of {@code <timestamp>.<payload>} with the signing secret. */
public final class StripeSignatures {

    private StripeSignatures() {}

    public static String sign(String payload, String secret, Instant at) {
        var timestamp = at.getEpochSecond();
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            var digest = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
            return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** A Stripe event about a PaymentIntent, in {@code apiVersion}. */
    public static String paymentIntentEvent(String eventId, String type, String apiVersion, String paymentIntentId,
                                            long amount, String status, String metadata, String lastPaymentError) {
        return """
                {"id":"%s","object":"event","api_version":"%s","created":%d,"livemode":false,"type":"%s",\
                "data":{"object":{"id":"%s","object":"payment_intent","amount":%d,"amount_received":%d,\
                "currency":"pen","status":"%s","metadata":%s,"last_payment_error":%s}}}"""
                .formatted(eventId, apiVersion, Instant.now().getEpochSecond(), type, paymentIntentId, amount,
                        "succeeded".equals(status) ? amount : 0, status, metadata, lastPaymentError);
    }
}
