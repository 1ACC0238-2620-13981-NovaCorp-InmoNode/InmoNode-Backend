package com.novacorp.inmonode.inmonodebackend.shared.infrastructure.time.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Exposes the system clock as a bean so time-dependent rules can be tested with a fixed clock.
 */
@Configuration
public class ClockConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
