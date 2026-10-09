package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.catalog;

import com.novacorp.inmonode.inmonodebackend.financial.application.catalog.CatalogChangedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.*;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class CatalogCommitInvalidationTest {
    @Configuration @EnableTransactionManagement
    static class Config {
        @Bean CaffeineCatalogReadCache cache() { return new CaffeineCatalogReadCache(); }
        @Bean PlatformTransactionManager transactionManager() {
            return new AbstractPlatformTransactionManager() {
                @Override protected Object doGetTransaction() { return new Object(); }
                @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
                @Override protected void doCommit(DefaultTransactionStatus status) {}
                @Override protected void doRollback(DefaultTransactionStatus status) {}
            };
        }
    }
    @Test void rollbackKeepsTheCachedCatalogAndCommitInvalidatesItOnlyAfterTheTransaction() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var cache = context.getBean(CaffeineCatalogReadCache.class);
            var transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            assertEquals("AVAILABLE", cache.get("lot", () -> "AVAILABLE"));
            transactions.executeWithoutResult(status -> {
                context.publishEvent(new CatalogChangedEvent(1L, 2L, "BLOCKED", Instant.EPOCH));
                assertEquals("AVAILABLE", cache.get("lot", () -> "BLOCKED"));
                status.setRollbackOnly();
            });
            assertEquals("AVAILABLE", cache.get("lot", () -> "BLOCKED"));
            transactions.executeWithoutResult(status -> {
                context.publishEvent(new CatalogChangedEvent(1L, 2L, "BLOCKED", Instant.EPOCH));
                assertEquals("AVAILABLE", cache.get("lot", () -> "BLOCKED"));
            });
            assertEquals("BLOCKED", cache.get("lot", () -> "BLOCKED"));
        }
    }
}
