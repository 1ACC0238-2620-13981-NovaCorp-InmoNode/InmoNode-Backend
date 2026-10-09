package com.novacorp.inmonode.inmonodebackend.shared.application.backup;

import java.nio.file.Path;

public interface DatabaseDump {
    /** Returns a temporary, complete compressed dump; the caller deletes it after upload. */
    Path compressedDump();
}
