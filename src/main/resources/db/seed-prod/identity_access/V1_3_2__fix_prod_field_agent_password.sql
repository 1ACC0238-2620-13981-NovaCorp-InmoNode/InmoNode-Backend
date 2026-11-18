-- PRODUCTION ONLY (loaded through spring.flyway.locations in application-prod.properties; applied after
-- later versions thanks to spring.flyway.out-of-order).
-- V1_3_1 copied STAFF_FIELD_AGENT_PASSWORD_HASH while its Render value had lost the last character, so
-- the stored digest is not a BCrypt hash and every sign-in of the field agent fails. This copies the
-- corrected variable again and clears the failed attempts and the lock left by those sign-ins.
-- Fix the variable in Render before deploying: while it is not a complete BCrypt hash (60 characters),
-- the migration fails and the previous deploy keeps serving, instead of storing another broken digest.

DO $$
BEGIN
    IF length('${staff_field_agent_password_hash}') <> 60
        OR left('${staff_field_agent_password_hash}', 2) <> '$2' THEN
        RAISE EXCEPTION 'STAFF_FIELD_AGENT_PASSWORD_HASH must hold a complete BCrypt hash before V1_3_2 runs';
    END IF;
END
$$;

UPDATE identity_access.users
SET password_hash   = '${staff_field_agent_password_hash}',
    failed_attempts = 0,
    locked_until    = NULL,
    updated_at      = NOW()
WHERE role = 'FIELD_AGENT'
  AND email = LOWER(TRIM('${staff_field_agent_email}'));
