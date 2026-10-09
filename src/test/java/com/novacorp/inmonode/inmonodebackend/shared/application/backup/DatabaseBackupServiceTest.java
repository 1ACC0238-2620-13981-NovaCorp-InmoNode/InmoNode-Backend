package com.novacorp.inmonode.inmonodebackend.shared.application.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabaseBackupServiceTest {
    @TempDir Path directory;
    @Test void retentionRunsOnlyAfterTheNewArchiveIsVerified() throws Exception {
        var file = Files.writeString(directory.resolve("backup.sql.gz"), "compressed-dump");
        var dump = mock(DatabaseDump.class);
        var vault = mock(BackupVault.class);
        when(dump.compressedDump()).thenReturn(file);
        var now = Instant.parse("2026-10-09T02:00:00Z");
        new DatabaseBackupService(dump, vault, Clock.fixed(now, ZoneOffset.UTC)).backupNow();
        var order = inOrder(vault);
        order.verify(vault).uploadVerified(startsWith("backup-2026-10-09T02-00-00Z-"), eq(file));
        order.verify(vault).deleteExpired(now.minus(Duration.ofDays(7)));
        assertFalse(Files.exists(file));
    }

    @Test void anUploadFailurePreservesAllExistingCopiesAndCleansTheTemporaryFile() throws Exception {
        var file = Files.writeString(directory.resolve("backup.sql.gz"), "compressed-dump");
        var dump = mock(DatabaseDump.class);
        var vault = mock(BackupVault.class);
        when(dump.compressedDump()).thenReturn(file);
        doThrow(new IllegalStateException("vault unavailable")).when(vault).uploadVerified(anyString(), eq(file));
        assertThrows(IllegalStateException.class, () -> new DatabaseBackupService(dump, vault, Clock.systemUTC()).backupNow());
        verify(vault, never()).deleteExpired(any());
        assertFalse(Files.exists(file));
    }

    @Test void aDumpFailureDoesNotContactTheVault() {
        var dump = mock(DatabaseDump.class);
        var vault = mock(BackupVault.class);
        when(dump.compressedDump()).thenThrow(new IllegalStateException("database unavailable"));
        assertThrows(IllegalStateException.class, () -> new DatabaseBackupService(dump, vault, Clock.systemUTC()).backupNow());
        verifyNoInteractions(vault);
    }
}
