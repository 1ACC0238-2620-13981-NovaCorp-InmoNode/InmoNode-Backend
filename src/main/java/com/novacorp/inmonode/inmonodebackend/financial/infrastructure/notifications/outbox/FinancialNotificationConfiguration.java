package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.notifications.outbox;

import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class FinancialNotificationConfiguration {
    @Bean(name = "notificationExecutor")
    public ThreadPoolTaskExecutor notificationExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1); executor.setMaxPoolSize(1); executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("financial-mail-");
        return executor;
    }
}
