package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.signature.docuseal;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature.InvalidSignatureCallbackException;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature.SignatureEvent;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature.SignatureWebhookVerifier;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;

/**
 * DocuSeal signs each webhook with {@code X-Docuseal-Signature: {timestamp}.{hex HMAC-SHA256(secret,
 * "{timestamp}.{body}")}}. The signature is checked in constant time, and a timestamp older or newer than
 * {@link #TOLERANCE_SECONDS} is refused, so a captured callback cannot be replayed later.
 */
public class DocuSealWebhookVerifier implements SignatureWebhookVerifier {

    static final long TOLERANCE_SECONDS = 300;

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String HMAC = "HmacSHA256";

    private final String secret;
    private final Clock clock;

    public DocuSealWebhookVerifier(String secret, Clock clock) {
        this.secret = secret;
        this.clock = clock;
    }

    @Override
    public boolean isEnabled() {
        return !secret.isBlank();
    }

    @Override
    public SignatureEvent verify(String payload, String signature) {
        if (!isEnabled()) {
            throw new InvalidSignatureCallbackException("no webhook secret is configured");
        }
        if (payload == null || signature == null || signature.isBlank()) {
            throw new InvalidSignatureCallbackException("the callback is not signed");
        }
        var separator = signature.indexOf('.');
        if (separator < 1) {
            throw new InvalidSignatureCallbackException("the signature is malformed");
        }
        var timestamp = signature.substring(0, separator).strip();
        var given = signature.substring(separator + 1).strip();
        long signedAt;
        try {
            signedAt = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw new InvalidSignatureCallbackException("the signature has no valid timestamp");
        }
        if (Math.abs(clock.instant().getEpochSecond() - signedAt) > TOLERANCE_SECONDS) {
            throw new InvalidSignatureCallbackException("the signature is too old");
        }
        var expected = hmac(timestamp + "." + payload);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                given.toLowerCase().getBytes(StandardCharsets.US_ASCII))) {
            throw new InvalidSignatureCallbackException("the signature does not match");
        }
        return read(payload);
    }

    private String hmac(String content) {
        try {
            var mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC));
            return HexFormat.of().formatHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }

    /**
     * {@code form.*} events carry the signer, with its submission and reference; {@code submission.*} events carry
     * the submission, with its signers.
     */
    private static SignatureEvent read(String payload) {
        JsonNode root;
        try {
            root = JSON.readTree(payload);
        } catch (JacksonException e) {
            throw new InvalidSignatureCallbackException("the callback is not JSON");
        }
        var type = text(root, "event_type");
        if (type == null) {
            throw new InvalidSignatureCallbackException("the callback has no event_type");
        }
        var data = root.path("data");
        Long submissionId;
        JsonNode signer;
        if (type.startsWith("form.")) {
            submissionId = id(data.path("submission_id"));
            signer = data;
        } else if (type.startsWith("submission.")) {
            submissionId = id(data.path("id"));
            signer = data.path("submitters").path(0);
        } else {
            return new SignatureEvent(type, SignatureEvent.Kind.OTHER, null, null, null);
        }
        var kind = switch (type) {
            case "form.completed", "submission.completed" -> SignatureEvent.Kind.COMPLETED;
            case "form.declined", "submission.declined" -> SignatureEvent.Kind.DECLINED;
            default -> SignatureEvent.Kind.OTHER;
        };
        return new SignatureEvent(type, kind, submissionId, text(signer, "external_id"),
                text(signer, "decline_reason"));
    }

    private static Long id(JsonNode node) {
        return node.isIntegralNumber() && node.asLong() > 0 ? node.asLong() : null;
    }

    private static String text(JsonNode node, String field) {
        var value = node.path(field);
        return value.isString() && !value.asString().isBlank() ? value.asString() : null;
    }
}
