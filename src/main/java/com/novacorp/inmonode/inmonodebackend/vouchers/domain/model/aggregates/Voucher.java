package com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.aggregates;

import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects.ExtractedData;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects.VoucherContentType;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects.VoucherFile;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects.VoucherStatus;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Payment voucher of a {@link ReservationOperation} (US-20): its file in the file repository and the data the field
 * app read from it. It is the evidence financial verifies before the reservation becomes a contract.
 */
public class Voucher {

    /** Vouchers come from Peruvian banks, so their dates are days in Lima. */
    static final ZoneId BANK_ZONE = ZoneId.of("America/Lima");

    private final @Nullable Long id;
    private final UUID voucherId;
    private final UUID reservationId;
    private final String objectKey;
    private final VoucherContentType contentType;
    private final long sizeBytes;
    private final ExtractedData extractedData;
    private final boolean manuallyCorrected;
    private final VoucherStatus status;
    private final Instant receivedAt;

    private Voucher(@Nullable Long id, UUID voucherId, UUID reservationId, String objectKey,
                    VoucherContentType contentType, long sizeBytes, ExtractedData extractedData,
                    boolean manuallyCorrected, VoucherStatus status, Instant receivedAt) {
        this.id = id;
        this.voucherId = voucherId;
        this.reservationId = reservationId;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.extractedData = extractedData;
        this.manuallyCorrected = manuallyCorrected;
        this.status = status;
        this.receivedAt = receivedAt;
    }

    /**
     * A voucher whose file is already in the file repository reaches the server: it is synchronized.
     *
     * @param manuallyCorrected whether the agent corrected the data the OCR read (US-10, Scenario 2)
     * @throws IllegalArgumentException when the operation is dated after the day the voucher is received
     */
    public static Voucher receive(VoucherFile file, ExtractedData extractedData, boolean manuallyCorrected,
                                  Instant receivedAt) {
        if (file == null || extractedData == null || receivedAt == null) {
            throw new IllegalArgumentException("a voucher needs its file, its data and when it was received");
        }
        if (extractedData.operationDate().isAfter(LocalDate.ofInstant(receivedAt, BANK_ZONE))) {
            throw new IllegalArgumentException("operationDate cannot be in the future");
        }
        return new Voucher(null, file.voucherId(), file.reservationId(), file.objectKey(), file.contentType(),
                file.sizeBytes(), extractedData, manuallyCorrected, VoucherStatus.SYNCED, receivedAt);
    }

    /** Rebuilds an already persisted voucher. */
    public static Voucher restore(Long id, UUID voucherId, UUID reservationId, String objectKey,
                                  VoucherContentType contentType, long sizeBytes, ExtractedData extractedData,
                                  boolean manuallyCorrected, VoucherStatus status, Instant receivedAt) {
        return new Voucher(id, voucherId, reservationId, objectKey, contentType, sizeBytes, extractedData,
                manuallyCorrected, status, receivedAt);
    }

    public boolean belongsTo(UUID reservationId) {
        return this.reservationId.equals(reservationId);
    }

    public @Nullable Long getId() { return id; }
    public UUID getVoucherId() { return voucherId; }
    public UUID getReservationId() { return reservationId; }
    public String getObjectKey() { return objectKey; }
    public VoucherContentType getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public ExtractedData getExtractedData() { return extractedData; }
    public boolean isManuallyCorrected() { return manuallyCorrected; }
    public VoucherStatus getStatus() { return status; }
    public Instant getReceivedAt() { return receivedAt; }
}
