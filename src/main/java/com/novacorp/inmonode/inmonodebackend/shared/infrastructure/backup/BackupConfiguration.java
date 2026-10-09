package com.novacorp.inmonode.inmonodebackend.shared.infrastructure.backup;

import com.novacorp.inmonode.inmonodebackend.shared.application.backup.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.*;
import java.net.URI;
import java.time.Duration;

@Configuration
@org.springframework.scheduling.annotation.EnableAsync
@ConditionalOnProperty(name = "backup.enabled", havingValue = "true")
public class BackupConfiguration {
    @Bean(name = "backupExecutor")
    public org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor backupExecutor() {
        var executor = new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("database-backup-");
        return executor;
    }

    @Bean
    public DatabaseDump databaseDump(@Value("${backup.pg-dump:pg_dump}") String executable,
            @Value("${DATABASE_URL:localhost}") String host, @Value("${DATABASE_PORT:5432}") String port,
            @Value("${DATABASE_NAME:inmonode}") String database, @Value("${spring.datasource.username}") String user,
            @Value("${spring.datasource.password}") String password) {
        return new PgDumpDatabaseDump(executable, host, port, database, user, password);
    }

    @Bean(destroyMethod = "close")
    public BackupVault backupVault(@Value("${backup.vault.bucket}") String bucket,
            @Value("${backup.vault.prefix:inmonode/backups/}") String prefix,
            @Value("${backup.vault.endpoint:}") String endpoint, @Value("${backup.vault.region:us-east-1}") String region,
            @Value("${storage.s3.bucket}") String documentBucket) {
        if (bucket.isBlank() || bucket.equals(documentBucket)) throw new IllegalArgumentException("Backups require a distinct external vault bucket");
        var builder = S3Client.builder().region(Region.of(region)).credentialsProvider(DefaultCredentialsProvider.builder().build())
                .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofMinutes(10))
                        .apiCallAttemptTimeout(Duration.ofMinutes(3)));
        if (!endpoint.isBlank()) {
            var uri = URI.create(endpoint);
            if (!"https".equals(uri.getScheme())) throw new IllegalArgumentException("The external backup vault must use HTTPS");
            builder.endpointOverride(uri);
        }
        return new S3BackupVault(builder.build(), bucket, prefix);
    }
}
