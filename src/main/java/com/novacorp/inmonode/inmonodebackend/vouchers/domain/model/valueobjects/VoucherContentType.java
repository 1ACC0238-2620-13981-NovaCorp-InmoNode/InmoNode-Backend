package com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects;

import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Formats a payment voucher may have (US-20): a photo taken in the field or a PDF of the bank receipt.
 */
public enum VoucherContentType {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    PDF("application/pdf", "pdf");

    private final String mediaType;
    private final String extension;

    VoucherContentType(String mediaType, String extension) {
        this.mediaType = mediaType;
        this.extension = extension;
    }

    /** The format with this media type, ignoring case and surrounding spaces. */
    public static VoucherContentType fromMediaType(@Nullable String mediaType) {
        var normalized = mediaType == null ? "" : mediaType.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(type -> type.mediaType.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("contentType must be one of " + accepted()));
    }

    private static String accepted() {
        return Arrays.stream(values()).map(VoucherContentType::mediaType).collect(Collectors.joining(", "));
    }

    public String mediaType() { return mediaType; }
    public String extension() { return extension; }
}
