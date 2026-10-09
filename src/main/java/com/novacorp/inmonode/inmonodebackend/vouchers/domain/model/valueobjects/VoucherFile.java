package com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * The file of a payment voucher (US-20): a JPEG, PNG or PDF of up to 5 MB. It is stored under a key derived from its
 * reservation and its own id, so a retry of the same voucher writes the same object instead of leaving orphans.
 *
 * @param voucherId id the device generated for the voucher
 * @param sizeBytes exact size of the file the client will upload
 */
public record VoucherFile(UUID reservationId, UUID voucherId, VoucherContentType contentType, long sizeBytes) {

    public static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

    public VoucherFile {
        if (reservationId == null || voucherId == null || contentType == null) {
            throw new IllegalArgumentException("a voucher file needs its reservation, its id and its content type");
        }
        if (sizeBytes < 1 || sizeBytes > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "sizeBytes must be between 1 and %d (5 MB)".formatted(MAX_SIZE_BYTES));
        }
    }

    /** @param mediaType one of the {@link VoucherContentType} media types, ignoring case */
    public static VoucherFile of(UUID reservationId, UUID voucherId, @Nullable String mediaType, long sizeBytes) {
        return new VoucherFile(reservationId, voucherId, VoucherContentType.fromMediaType(mediaType), sizeBytes);
    }

    /** Where the file lives in the file repository: {@code vouchers/{reservationId}/{voucherId}.{ext}}. */
    public String objectKey() {
        return "vouchers/%s/%s.%s".formatted(reservationId, voucherId, contentType.extension());
    }
}
