package com.novacorp.inmonode.inmonodebackend.shared.application.backup;

import java.nio.file.Path;
import java.time.Instant;

public interface BackupVault {
    /** Uploads and verifies the checksum before returning. */
    void uploadVerified(String key, Path compressedDump);
    void deleteExpired(Instant cutoff);
}
