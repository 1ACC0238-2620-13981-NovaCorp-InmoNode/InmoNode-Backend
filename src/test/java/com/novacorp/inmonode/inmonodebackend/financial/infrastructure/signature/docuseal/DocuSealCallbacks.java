package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.signature.docuseal;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/** DocuSeal's callbacks for tests: bodies shaped like the ones the spike received, signed as DocuSeal signs them. */
public final class DocuSealCallbacks {

    private DocuSealCallbacks() {}

    /** {@code X-Docuseal-Signature: {timestamp}.{hex HMAC-SHA256(secret, "{timestamp}.{body}")}}. */
    public static String sign(String payload, String secret, long epochSecond) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            var digest = mac.doFinal((epochSecond + "." + payload).getBytes(StandardCharsets.UTF_8));
            return epochSecond + "." + HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** A {@code form.*} event: its data is the signer, with its submission and reference. */
    public static String form(String type, long submissionId, String externalId, String declineReason) {
        return """
                {"event_type":"%s","timestamp":"2026-11-22T21:31:56.844Z","data":{"id":%d,"submission_id":%d,\
                "email":"comprador@inmonode.test","external_id":"%s","status":"%s","role":"Comprador",\
                "decline_reason":%s,"submission_url":"http://localhost:3000/e/7kef1vxdpenJ3u",\
                "documents":[{"name":"hoja-de-firma","url":"http://localhost:3000/file/signed.pdf"}]}}"""
                .formatted(type, submissionId + 1000, submissionId, externalId,
                        type.endsWith("declined") ? "declined" : "completed",
                        declineReason == null ? "null" : "\"" + declineReason + "\"");
    }

    /** A {@code submission.*} event: its data is the submission, with its signers. */
    public static String submission(String type, long submissionId, String externalId) {
        return """
                {"event_type":"%s","timestamp":"2026-11-22T21:31:56.899Z","data":{"id":%d,"status":"completed",\
                "submitters":[{"id":%d,"submission_id":%d,"external_id":"%s","slug":"abc","status":"completed"}]}}"""
                .formatted(type, submissionId, submissionId + 1000, submissionId, externalId);
    }
}
