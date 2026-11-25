package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The signature sheet the provider returned once the buyer signed (US-56), kept in the file repository next to the
 * contract it cites.
 *
 * @param objectKey where it is stored; see {@link #objectKeyFor}
 * @param sizeBytes its size
 * @param sha256    the SHA-256 of its bytes, in lowercase hexadecimal
 */
public record SignedContractDocument(String objectKey, long sizeBytes, String sha256) {

    public static final String CONTENT_TYPE = "application/pdf";
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    public SignedContractDocument {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("a signed document needs its object key");
        }
        if (sizeBytes < 1) {
            throw new IllegalArgumentException("a signed document cannot be empty");
        }
        if (!isSha256(sha256)) {
            throw new IllegalArgumentException("sha256 must be 64 lowercase hexadecimal characters");
        }
    }

    /** {@code contracts/{transactionId}/signed/{documentId}.pdf}: writing it again replaces it, so retries leave no orphans. */
    public static String objectKeyFor(UUID transactionId, UUID documentId) {
        return "contracts/%s/signed/%s.pdf".formatted(transactionId, documentId);
    }

    public static boolean isSha256(String value) {
        return value != null && SHA256.matcher(value).matches();
    }

    /** The SHA-256 of a file, in lowercase hexadecimal, as the signature sheet cites it. */
    public static String sha256Of(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** Whether the bytes look like a PDF. */
    public static boolean isPdf(byte[] content) {
        return content != null && content.length > 4 && content[0] == '%' && content[1] == 'P' && content[2] == 'D'
                && content[3] == 'F';
    }
}
