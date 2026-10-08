-- DEVELOPMENT ONLY (loaded through spring.flyway.locations in application-dev.properties).
-- Staff accounts for the non-public roles. Password for all of them: Dev-Pass-123
INSERT INTO identity_access.users (email, password_hash, role, status, failed_attempts, created_at, updated_at)
VALUES ('agent@inmonode.dev',   '$2a$10$W3XWJYgEYadhWhMjUeV15ecZGEw9nc4xWYtqm.0LdXUb4Ld6khxmu', 'FIELD_AGENT',   'ACTIVE', 0, NOW(), NOW()),
       ('catalog@inmonode.dev', '$2a$10$W3XWJYgEYadhWhMjUeV15ecZGEw9nc4xWYtqm.0LdXUb4Ld6khxmu', 'CATALOG_ADMIN', 'ACTIVE', 0, NOW(), NOW()),
       ('finance@inmonode.dev', '$2a$10$W3XWJYgEYadhWhMjUeV15ecZGEw9nc4xWYtqm.0LdXUb4Ld6khxmu', 'FINANCE_ADMIN', 'ACTIVE', 0, NOW(), NOW());
