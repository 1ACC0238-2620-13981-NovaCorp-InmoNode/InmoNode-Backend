package com.novacorp.inmonode.inmonodebackend.shared.domain.model.valueobjects;

/** One-based API pages (US-46), independent of the persistence framework. */
public record PageRequest(int page, int limit) {
    public static final int MAX_LIMIT = 100;

    public PageRequest {
        if (page < 1 || limit < 1) {
            throw new IllegalArgumentException("page and limit must be greater than zero");
        }
        limit = Math.min(limit, MAX_LIMIT);
        if ((long) (page - 1) * limit > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("page offset is too large");
        }
    }
}
