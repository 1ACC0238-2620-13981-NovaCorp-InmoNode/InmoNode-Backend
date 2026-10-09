package com.novacorp.inmonode.inmonodebackend.financial.application.catalog;

import java.util.function.Supplier;

/** Only display queries may use this port. Financial decisions always use locked repositories. */
public interface CatalogReadCache {
    <T> T get(String key, Supplier<T> loader);
    void invalidate();
}
