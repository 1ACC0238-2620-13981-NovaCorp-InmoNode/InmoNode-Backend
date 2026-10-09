package com.novacorp.inmonode.inmonodebackend.shared.application.monitoring;

/** Outbound port for checking the main database without exposing connection details (US-34). */
public interface DatabaseHealthProbe {

    /** Whether a connection can be acquired and validated; an unavailable database returns {@code false}. */
    boolean isAvailable();
}
