package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.catalog;

import com.github.benmanes.caffeine.cache.*;
import com.novacorp.inmonode.inmonodebackend.financial.application.catalog.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
import java.time.Duration;
import java.util.function.Supplier;

@Component
public class CaffeineCatalogReadCache implements CatalogReadCache {
    private volatile Cache<String, Object> cache;
    private final Ticker ticker;

    public CaffeineCatalogReadCache() { this(Ticker.systemTicker()); }
    CaffeineCatalogReadCache(Ticker ticker) {
        this.ticker = ticker;
        this.cache = newGeneration();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Supplier<T> loader) {
        return (T) cache.get(key, ignored -> loader.get());
    }

    @Override
    public void invalidate() {
        // A loader already running before a commit cannot repopulate the current generation with stale data.
        cache = newGeneration();
    }

    private Cache<String, Object> newGeneration() {
        return Caffeine.newBuilder().maximumSize(1000).expireAfterWrite(Duration.ofMinutes(5)).ticker(ticker).build();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(CatalogChangedEvent event) { invalidate(); }
}
