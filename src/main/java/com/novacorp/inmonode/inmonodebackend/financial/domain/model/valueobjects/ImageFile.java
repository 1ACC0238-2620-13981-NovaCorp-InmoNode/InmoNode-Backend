package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/**
 * A photo uploaded straight to the file repository (US-33): the identity document of a buyer (US-59) or an image of a
 * project (US-57). Both accept the same files: JPEG or PNG up to 5 MB.
 *
 * @param contentType {@code image/jpeg} or {@code image/png}, in lower case
 * @param sizeBytes   exact size of the file, up to 5 MB
 */
public record ImageFile(String contentType, long sizeBytes) {

    public static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;
    private static final Map<String, String> EXTENSIONS = Map.of("image/jpeg", "jpg", "image/png", "png");

    public ImageFile {
        contentType = normalized(contentType);
        if (!EXTENSIONS.containsKey(contentType)) {
            throw new IllegalArgumentException("contentType must be image/jpeg or image/png");
        }
        if (sizeBytes < 1 || sizeBytes > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "sizeBytes must be between 1 and %d (5 MB)".formatted(MAX_SIZE_BYTES));
        }
    }

    /** The extension of the stored object: {@code jpg} or {@code png}. */
    public String extension() {
        return EXTENSIONS.get(contentType);
    }

    private static String normalized(@Nullable String contentType) {
        return contentType == null ? "" : contentType.strip().toLowerCase(Locale.ROOT);
    }
}
