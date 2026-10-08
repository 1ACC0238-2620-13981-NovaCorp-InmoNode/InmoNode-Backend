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
        return status == LotStatus.BLOCKED && blockedUntil != null && !now.isBefore(blockedUntil);
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
