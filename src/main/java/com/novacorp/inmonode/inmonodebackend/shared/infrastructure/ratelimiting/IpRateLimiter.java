package com.novacorp.inmonode.inmonodebackend.shared.infrastructure.ratelimiting;

import java.time.Clock;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Per-instance, bounded IP counters. An exhausted category freezes that IP for the rest of its minute. */
public final class IpRateLimiter {

    public enum Category { CATALOG, PDF, API }

    public record Decision(boolean allowed, long retryAfterSeconds) { }

    private static final long WINDOW_MILLIS = 60_000;
    private final Clock clock;
    private final Map<Category, Integer> limits;
    private final int maxTrackedIps;
    private final Map<String, Window> windows = new HashMap<>();
    private long nextCleanup;

    public IpRateLimiter(Clock clock, int catalogLimit, int pdfLimit, int apiLimit, int maxTrackedIps) {
        if (catalogLimit < 1 || pdfLimit < 1 || apiLimit < 1 || maxTrackedIps < 1) {
            throw new IllegalArgumentException("rate limits and maxTrackedIps must be positive");
        }
        this.clock = clock;
        this.limits = Map.of(Category.CATALOG, catalogLimit, Category.PDF, pdfLimit, Category.API, apiLimit);
        this.maxTrackedIps = maxTrackedIps;
    }

    /** Atomic across categories so parallel requests cannot exceed the configured allowance. */
    public synchronized Decision consume(String ip, Category category) {
        var now = clock.millis();
        if (now >= nextCleanup) {
            windows.values().removeIf(window -> window.expiresAt <= now);
            nextCleanup = now + 10_000;
        }
        var window = windows.get(ip);
        if (window == null || window.expiresAt <= now) {
            // Never evict an active IP: rotating addresses must not reset an attacker's counters.
            if (window == null && windows.size() >= maxTrackedIps) {
                windows.values().removeIf(candidate -> candidate.expiresAt <= now);
                if (windows.size() >= maxTrackedIps) return new Decision(false, 60);
            }
            window = new Window(now + WINDOW_MILLIS);
            windows.put(ip, window);
        }
        var used = window.counts.getOrDefault(category, 0);
        if (window.blocked || used >= limits.get(category)) {
            window.blocked = true;
            return new Decision(false, Math.max(1, (window.expiresAt - now + 999) / 1000));
        }
        window.counts.put(category, used + 1);
        return new Decision(true, 0);
    }

    private static final class Window {
        private final long expiresAt;
        private final Map<Category, Integer> counts = new EnumMap<>(Category.class);
        private boolean blocked;

        private Window(long expiresAt) {
            this.expiresAt = expiresAt;
        }
    }
}
