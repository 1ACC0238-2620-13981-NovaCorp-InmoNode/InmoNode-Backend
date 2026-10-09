package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.catalog;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class CaffeineCatalogReadCacheTest {
    @Test void hitsAvoidReloadingAndEntriesExpireAtFiveMinutes() {
        var now = new AtomicLong();
        var cache = new CaffeineCatalogReadCache(now::get);
        var queries = new AtomicInteger();
        assertEquals(1, cache.get("projects", queries::incrementAndGet));
        assertEquals(1, cache.get("projects", queries::incrementAndGet));
        now.set(Duration.ofMinutes(5).toNanos());
        assertEquals(2, cache.get("projects", queries::incrementAndGet));
        cache.invalidate();
        assertEquals(3, cache.get("projects", queries::incrementAndGet));
    }

    @Test void aLoaderStartedBeforeInvalidationCannotPopulateTheNewGeneration() throws Exception {
        var cache = new CaffeineCatalogReadCache();
        var started = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var oldRead = executor.submit(() -> cache.get("lot", () -> {
                started.countDown();
                try { assertTrue(finish.await(5, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new RuntimeException(e); }
                return "AVAILABLE";
            }));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            cache.invalidate();
            try {
                assertEquals("BLOCKED", cache.get("lot", () -> "BLOCKED"));
            } finally { finish.countDown(); }
            assertEquals("AVAILABLE", oldRead.get(5, TimeUnit.SECONDS));
            assertEquals("BLOCKED", cache.get("lot", () -> "BLOCKED"));
        }
    }
}
