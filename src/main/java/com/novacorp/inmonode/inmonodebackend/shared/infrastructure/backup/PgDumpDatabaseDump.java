package com.novacorp.inmonode.inmonodebackend.shared.infrastructure.backup;

import com.novacorp.inmonode.inmonodebackend.shared.application.backup.DatabaseDump;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

public class PgDumpDatabaseDump implements DatabaseDump {
    private final String executable, host, port, database, user, password;
    public PgDumpDatabaseDump(String executable, String host, String port, String database, String user, String password) {
        this.executable = executable; this.host = host; this.port = port; this.database = database;
        this.user = user; this.password = password;
    }

    @Override
    public Path compressedDump() {
        Path sql = null, errors = null, compressed = null;
        Process process = null;
        try {
            sql = Files.createTempFile("inmonode-backup-", ".sql");
            errors = Files.createTempFile("inmonode-backup-", ".stderr");
            compressed = Files.createTempFile("inmonode-backup-", ".sql.gz");
            var builder = new ProcessBuilder(executable, "--host", host, "--port", port, "--username", user,
                    "--dbname", database, "--no-password", "--format=plain", "--file", sql.toString());
            // The password never appears in process arguments or logs.
            builder.environment().put("PGPASSWORD", password);
            builder.environment().put("PGCONNECT_TIMEOUT", "10");
            builder.redirectError(errors.toFile()).redirectOutput(ProcessBuilder.Redirect.DISCARD);
            process = builder.start();
            if (!process.waitFor(15, TimeUnit.MINUTES)) throw new IllegalStateException("pg_dump exceeded 15 minutes");
            if (process.exitValue() != 0 || Files.size(sql) == 0) throw new IllegalStateException("pg_dump did not complete successfully");
            try (var input = Files.newInputStream(sql); var output = new GZIPOutputStream(Files.newOutputStream(compressed))) {
                input.transferTo(output);
            }
            var result = compressed;
            compressed = null;
            return result;
        } catch (IOException e) { throw new IllegalStateException("Could not execute pg_dump", e); }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Database backup interrupted", e);
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            remove(sql); remove(errors); remove(compressed);
        }
    }

    private static void remove(Path file) {
        if (file != null) try { Files.deleteIfExists(file); } catch (IOException ignored) { /* Cleanup is best effort. */ }
    }
}
