package com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotBoundary;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotDimensions;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Canonical inventory entry of a lot and its availability: this context is the only authority on it
 * (Context Map). It references its {@link Project} by id; its code is unique within the project.
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

    private Lot(@Nullable Long id, Long projectId, String code, LotDimensions dimensions, Money price,
                LotBoundary boundary, LotStatus status) {
        this.id = id;
        this.projectId = projectId;
        this.code = code;
        this.dimensions = dimensions;
        this.price = price;
        this.boundary = boundary;
        this.status = status;
    }

    /** US-53, Scenario 2: a lot loaded from the project plan starts {@code AVAILABLE}. */
    public static Lot register(Long projectId, String code, LotDimensions dimensions, Money price,
                               LotBoundary boundary) {
        return new Lot(null, projectId, normalizeCode(code), dimensions, price, boundary, LotStatus.AVAILABLE);
    }

    /** Rebuilds an already persisted lot. */
    public static Lot restore(Long id, Long projectId, String code, LotDimensions dimensions, Money price,
                              LotBoundary boundary, LotStatus status) {
        return new Lot(id, projectId, code, dimensions, price, boundary, status);
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

    public boolean isAvailable() {
        return status == LotStatus.AVAILABLE;
    }

    public @Nullable Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public String getCode() { return code; }
    public LotDimensions getDimensions() { return dimensions; }
    public Money getPrice() { return price; }
    public LotBoundary getBoundary() { return boundary; }
    public LotStatus getStatus() { return status; }
}
