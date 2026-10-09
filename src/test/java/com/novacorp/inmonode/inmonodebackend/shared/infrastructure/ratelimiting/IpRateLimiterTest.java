package com.novacorp.inmonode.inmonodebackend.shared.infrastructure.ratelimiting;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static com.novacorp.inmonode.inmonodebackend.shared.infrastructure.ratelimiting.IpRateLimiter.Category.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IpRateLimiterTest {
    private final AtomicLong now = new AtomicLong(1_000);
    private final Clock clock = mock(Clock.class);

    private IpRateLimiter limiter(int catalog, int pdf, int api, int capacity) {
        when(clock.millis()).thenAnswer(invocation -> now.get());
        return new IpRateLimiter(clock, catalog, pdf, api, capacity);
    }

    @Test
    void exceedingPdfFreezesTheIpAcrossCategoriesButNotOtherIps() {
        var limiter = limiter(150, 1, 60, 100);
        assertTrue(limiter.consume("one", PDF).allowed());
        assertFalse(limiter.consume("one", PDF).allowed());
        assertFalse(limiter.consume("one", CATALOG).allowed());
        assertTrue(limiter.consume("two", PDF).allowed());
    }

    @Test
    void theIpRecoversAtTheWindowBoundaryWithACorrectRetryAfter() {
        var limiter = limiter(1, 1, 1, 100);
        limiter.consume("one", API);
        now.addAndGet(1_501);
        assertEquals(59, limiter.consume("one", API).retryAfterSeconds());
        now.set(60_999);
        assertFalse(limiter.consume("one", API).allowed());
        now.set(61_000);
        assertTrue(limiter.consume("one", API).allowed());
    }

    @Test
    void catalogAndGeneralAllowancesAreDistinctBeforeEitherIsExhausted() {
        var limiter = limiter(2, 1, 1, 100);
        assertTrue(limiter.consume("one", API).allowed());
        assertTrue(limiter.consume("one", CATALOG).allowed());
        assertTrue(limiter.consume("one", CATALOG).allowed());
        assertFalse(limiter.consume("one", CATALOG).allowed());
    }

    @Test
    void parallelRequestsCannotExceedTheAllowance() throws Exception {
        var limiter = limiter(15, 1, 1, 100);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var results = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 100; i++) results.add(executor.submit(() -> limiter.consume("one", CATALOG).allowed()));
            var accepted = 0;
            for (var result : results) if (result.get()) accepted++;
            assertEquals(15, accepted);
        }
    }

    @Test
    void capacityDoesNotEvictAnActiveIpAndExpiredEntriesAreReusable() {
        var limiter = limiter(1, 1, 1, 1);
        assertTrue(limiter.consume("one", API).allowed());
        assertFalse(limiter.consume("two", API).allowed());
        assertFalse(limiter.consume("one", API).allowed());
        now.addAndGet(60_000);
        assertTrue(limiter.consume("two", API).allowed());
    }

    @Test
    void invalidConfigurationFailsAtStartup() {
        assertThrows(IllegalArgumentException.class, () -> limiter(0, 1, 1, 100));
        assertThrows(IllegalArgumentException.class, () -> limiter(1, 1, 1, 0));
    }
}
