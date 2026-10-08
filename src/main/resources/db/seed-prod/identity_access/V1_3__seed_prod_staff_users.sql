-- PRODUCTION ONLY (loaded through spring.flyway.locations in application-prod.properties).
-- Initial staff accounts for the non-public roles; buyers register themselves (US-14).
-- Emails and BCrypt hashes come from Flyway placeholders bound to the STAFF_* environment variables,
-- so no credential is stored in the repository. Runs once: changing the variables later does not
-- update these accounts.
INSERT INTO identity_access.users (email, password_hash, role, status, failed_attempts, created_at, updated_at)
VALUES (LOWER(TRIM('${staff_field_agent_email}')),   '${staff_field_agent_password_hash}',   'FIELD_AGENT',   'ACTIVE', 0, NOW(), NOW()),
       (LOWER(TRIM('${staff_catalog_admin_email}')), '${staff_catalog_admin_password_hash}', 'CATALOG_ADMIN', 'ACTIVE', 0, NOW(), NOW()),
       (LOWER(TRIM('${staff_finance_admin_email}')), '${staff_finance_admin_password_hash}', 'FINANCE_ADMIN', 'ACTIVE', 0, NOW(), NOW());
