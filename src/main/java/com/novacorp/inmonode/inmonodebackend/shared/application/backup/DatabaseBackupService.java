package com.novacorp.inmonode.inmonodebackend.shared.application.backup;

import java.io.IOException;
import java.nio.file.Files;
import java.time.*;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "backup.enabled", havingValue = "true")
public class DatabaseBackupService {
    private static final Logger LOG = LoggerFactory.getLogger(DatabaseBackupService.class);
    private final DatabaseDump dump;
    private final BackupVault vault;
    private final Clock clock;

    public DatabaseBackupService(DatabaseDump dump, BackupVault vault, Clock clock) {
        this.dump = dump;
        this.vault = vault;
        this.clock = clock;
    }

    @org.springframework.scheduling.annotation.Async("backupExecutor")
    @Scheduled(cron = "${backup.cron:0 0 2 * * *}", zone = "UTC")
    public void scheduledBackup() {
        try { backupNow(); }
        catch (RuntimeException failure) { LOG.error("Database backup failed; existing vault copies were preserved ({})",
                failure.getClass().getSimpleName()); }
    }

    public synchronized void backupNow() {
        var archive = dump.compressedDump();
        try {
            if (Files.size(archive) == 0) throw new IllegalStateException("Database dump is empty");
            var now = clock.instant();
            var key = "backup-" + now.toString().replace(':', '-') + "-" + UUID.randomUUID() + ".sql.gz";
            vault.uploadVerified(key, archive);
            // Never prune when a new dump or its upload/verification failed.
            vault.deleteExpired(now.minus(Duration.ofDays(7)));
            LOG.info("Database backup uploaded and verified: {}", key);
        } catch (IOException e) { throw new IllegalStateException("Could not read the database backup", e); }
        finally {
            try { Files.deleteIfExists(archive); }
            catch (IOException e) { LOG.warn("Could not remove a temporary database backup file"); }
        }
    }
}
