-- PRODUCTION ONLY (loaded through spring.flyway.locations in application-prod.properties; applied after
-- later versions thanks to spring.flyway.out-of-order).
-- Gives finance a staff account that can sign in, as V1_3_1 and V1_3_2 did for the field agent. V1_3 seeded
-- the FINANCE_ADMIN account once from STAFF_FINANCE_ADMIN_EMAIL while that variable could still hold
-- template text, which is not an e-mail. This binds the account to the current STAFF_FINANCE_ADMIN_EMAIL
-- and STAFF_FINANCE_ADMIN_PASSWORD_HASH variables:
-- - the account V1_3 seeded with a non-e-mail address takes the new e-mail;
-- - when there is no such account, a new FINANCE_ADMIN account is created with that e-mail;
-- - either way the account gets the hash, and its failed attempts and lock are cleared.
-- Set both variables in Render before deploying: while either is not valid, the migration fails and the
-- previous deploy keeps serving, instead of being marked as applied without effect.

DO $$
BEGIN
    IF position('@' IN '${staff_finance_admin_email}') = 0 THEN
        RAISE EXCEPTION 'STAFF_FINANCE_ADMIN_EMAIL must hold the real finance e-mail before V1_3_3 runs';
    END IF;
    IF length('${staff_finance_admin_password_hash}') <> 60
        OR left('${staff_finance_admin_password_hash}', 2) <> '$2' THEN
        RAISE EXCEPTION 'STAFF_FINANCE_ADMIN_PASSWORD_HASH must hold a complete BCrypt hash before V1_3_3 runs';
    END IF;
    IF EXISTS (SELECT 1
               FROM identity_access.users
               WHERE email = LOWER(TRIM('${staff_finance_admin_email}'))
                 AND role <> 'FINANCE_ADMIN') THEN
        RAISE EXCEPTION 'STAFF_FINANCE_ADMIN_EMAIL already belongs to an account with another role';
    END IF;
END
$$;

UPDATE identity_access.users
SET email      = LOWER(TRIM('${staff_finance_admin_email}')),
    updated_at = NOW()
WHERE id = (SELECT min(id)
            FROM identity_access.users
            WHERE role = 'FINANCE_ADMIN'
              AND position('@' IN email) = 0)
  AND NOT EXISTS (SELECT 1
                  FROM identity_access.users
                  WHERE email = LOWER(TRIM('${staff_finance_admin_email}')));

INSERT INTO identity_access.users (email, password_hash, role, status, failed_attempts, created_at, updated_at)
SELECT LOWER(TRIM('${staff_finance_admin_email}')), '${staff_finance_admin_password_hash}', 'FINANCE_ADMIN',
       'ACTIVE', 0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1
                  FROM identity_access.users
                  WHERE email = LOWER(TRIM('${staff_finance_admin_email}')));

UPDATE identity_access.users
SET password_hash   = '${staff_finance_admin_password_hash}',
    status          = 'ACTIVE',
    failed_attempts = 0,
    locked_until    = NULL,
    updated_at      = NOW()
WHERE role = 'FINANCE_ADMIN'
  AND email = LOWER(TRIM('${staff_finance_admin_email}'));
