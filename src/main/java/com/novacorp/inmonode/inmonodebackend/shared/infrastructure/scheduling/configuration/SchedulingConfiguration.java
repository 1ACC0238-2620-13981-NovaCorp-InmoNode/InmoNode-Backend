package com.novacorp.inmonode.inmonodebackend.shared.infrastructure.scheduling.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled} jobs for every module, such as the release of expired lot blocks in financial.
 * Each job can be switched off with its own property.
 */
@Configuration
@EnableScheduling
public class SchedulingConfiguration {
}
