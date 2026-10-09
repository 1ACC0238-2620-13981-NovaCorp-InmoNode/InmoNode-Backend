package com.novacorp.inmonode.inmonodebackend.shared.domain.model.valueobjects;

import java.util.List;

/** A database page and its unpaginated count. */
public record PageResult<T>(List<T> items, long totalCount, int page, int limit) {
    public PageResult {
        items = List.copyOf(items);
    }
}
