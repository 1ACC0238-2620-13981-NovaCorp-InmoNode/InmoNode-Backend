package com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotBoundary;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotDimensions;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * Canonical inventory entry of a lot and its availability: this context is the only authority on it
 * (Context Map). It references its {@link Project} by id; its code is unique within the project.
 *
 * <p>A reservation blocks the lot for a limited time (Lot Block). An expired block already counts as available,
 * even before the release job frees it, so availability never depends on when that job runs.</p>
 */
public class Lot {

    public static final int MAX_CODE_LENGTH = 30;

    private String stageName = "Etapa 1";
    private final @Nullable Long id;
    private final Long projectId;
    private final String code;
    private final LotDimensions dimensions;
    private final Money price;
    private final LotBoundary boundary;
    private LotStatus status;
    private @Nullable Long currentReservationId;
    private @Nullable Instant blockedUntil;

    private Lot(@Nullable Long id, Long projectId, String code, LotDimensions dimensions, Money price,
                LotBoundary boundary, LotStatus status, @Nullable Long currentReservationId,
                @Nullable Instant blockedUntil) {
        this.id = id;
        this.projectId = projectId;
        this.code = code;
        this.dimensions = dimensions;
        this.price = price;
        this.boundary = boundary;
        this.status = status;
        this.currentReservationId = currentReservationId;
        this.blockedUntil = blockedUntil;
    }

    /** US-53, Scenario 2: a lot loaded from the project plan starts {@code AVAILABLE}. */
    public static Lot register(Long projectId, String code, LotDimensions dimensions, Money price,
                               LotBoundary boundary) {
        return new Lot(null, projectId, normalizeCode(code), dimensions, price, boundary, LotStatus.AVAILABLE,
                null, null);
    }

    public static Lot registerDraft(Long projectId, String stageName, String code, LotDimensions dimensions,
                                    Money price, LotBoundary boundary) {
        if (projectId == null || stageName == null || stageName.isBlank() || dimensions == null || price == null || boundary == null) {
            throw new IllegalArgumentException("a draft lot needs its project, stage, dimensions, price and polygon");
        }
        var lot = new Lot(null, projectId, normalizeCode(code), dimensions, price, boundary, LotStatus.DRAFT, null, null);
        lot.stageName = stageName.strip();
        return lot;
    }

    public boolean publish() {
        if (projectId == null || dimensions == null || price == null || boundary == null) {
            throw new IllegalArgumentException("project, dimensions, price and polygon are required before publishing");
        }
        if (status != LotStatus.DRAFT) return false;
        status = LotStatus.AVAILABLE;
        return true;
    }

    public static Lot withStage(Lot lot, String stageName) {
        lot.stageName = stageName;
        return lot;
    }
    public String getStageName() { return stageName; }

    /** Rebuilds an already persisted lot. */
    public static Lot restore(Long id, Long projectId, String code, LotDimensions dimensions, Money price,
                              LotBoundary boundary, LotStatus status, @Nullable Long currentReservationId,
                              @Nullable Instant blockedUntil) {
        return new Lot(id, projectId, code, dimensions, price, boundary, status, currentReservationId, blockedUntil);
    }

    /** Codes are compared case-insensitively, so "a-01" and "A-01" are the same lot. */
    public static String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        var normalized = code.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > MAX_CODE_LENGTH) {
            throw new IllegalArgumentException("code must have at most %d characters".formatted(MAX_CODE_LENGTH));
        }
        return normalized;
    }

    /** A lot can be reserved when it is available or its block has expired. */
    public boolean isAvailable(Instant now) {
        return status == LotStatus.AVAILABLE || hasExpiredBlock(now);
    }

    public boolean hasExpiredBlock(Instant now) {
        return (status == LotStatus.BLOCKED || status == LotStatus.PENDING_VERIFICATION) && blockedUntil != null && !now.isBefore(blockedUntil);
    }

    /**
     * Holds the lot for the reservation during {@code validity} from {@code now}.
     *
     * @return {@code false} when the lot is not available, so it stays as it was (availability conflict, US-12)
     */
    public boolean block(Long reservationId, Instant now, Duration validity) {
        if (!isAvailable(now)) {
            return false;
        }
        status = LotStatus.BLOCKED;
        currentReservationId = reservationId;
        blockedUntil = now.plus(validity);
        return true;
    }

    /**
     * The payment evidence of the reservation holding the lot arrived in time: the lot waits for its verification,
     * with no deadline, so the release job no longer frees it. It stays assigned to that reservation.
     *
     * @return {@code false} when the lot is not held by that reservation or its block already ran out, so it stays as
     *         it was
     */
    public boolean moveToPendingVerification(Long reservationId, Instant now) {
        if ((status != LotStatus.BLOCKED && status != LotStatus.PENDING_VERIFICATION)
                || hasExpiredBlock(now) || !reservationId.equals(currentReservationId)) {
            return false;
        }
        status = LotStatus.PENDING_VERIFICATION;
        blockedUntil = null;
        return true;
    }

    /**
     * The payment evidence of the reservation waiting for verification was approved: the lot is reserved for it and
     * no longer offered.
     *
     * @return {@code false} when the lot is not waiting for that reservation's verification, so it stays as it was
     */
    public boolean markReserved(Long reservationId) {
        if (!isWaitingForVerificationOf(reservationId)) {
            return false;
        }
        status = LotStatus.RESERVED;
        blockedUntil = null;
        return true;
    }

    /**
     * The payment evidence under review was rejected: the lot is held again for the same reservation, for a new
     * window, while a substitute voucher arrives (US-25). If none arrives, the block runs out as any other.
     *
     * @return {@code false} when the lot is not waiting for that reservation's verification, so it stays as it was
     */
    public boolean awaitReplacement(Long reservationId, Instant deadlineWithDeliveryGrace) {
        if (!isWaitingForVerificationOf(reservationId)) return false;
        blockedUntil = deadlineWithDeliveryGrace;
        return true;
    }

    public boolean reopenBlock(Long reservationId, Instant now, Duration validity) {
        if (!isWaitingForVerificationOf(reservationId)) {
            return false;
        }
        status = LotStatus.BLOCKED;
        blockedUntil = now.plus(validity);
        return true;
    }

    /**
     * The contract of the reservation holding the lot was signed and verified: the lot is sold (US-56). It
     * stays assigned to that reservation.
     *
     * @return {@code false} when the lot is not reserved for that reservation, so it stays as it was
     */
    public boolean markSold(Long reservationId) {
        if (status != LotStatus.RESERVED || !reservationId.equals(currentReservationId)) {
            return false;
        }
        status = LotStatus.SOLD;
        return true;
    }

    private boolean isWaitingForVerificationOf(Long reservationId) {
        return status == LotStatus.PENDING_VERIFICATION && reservationId.equals(currentReservationId);
    }

    /**
     * Makes the lot available again when its block ran out without payment evidence.
     *
     * @return the reservation that held the expired block; empty when the lot had no expired block
     */
    public Optional<Long> releaseExpiredBlock(Instant now) {
        if (!hasExpiredBlock(now)) {
            return Optional.empty();
        }
        var released = currentReservationId;
        status = LotStatus.AVAILABLE;
        currentReservationId = null;
        blockedUntil = null;
        return Optional.ofNullable(released);
    }

    public @Nullable Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public String getCode() { return code; }
    public LotDimensions getDimensions() { return dimensions; }
    public Money getPrice() { return price; }
    public LotBoundary getBoundary() { return boundary; }
    public LotStatus getStatus() { return status; }
    public @Nullable Long getCurrentReservationId() { return currentReservationId; }
    public @Nullable Instant getBlockedUntil() { return blockedUntil; }
}
