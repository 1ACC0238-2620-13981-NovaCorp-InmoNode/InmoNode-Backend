-- PRODUCTION ONLY (loaded through spring.flyway.locations in application-prod.properties; applied after
-- later versions thanks to spring.flyway.out-of-order).
-- V1_3 ran while STAFF_FIELD_AGENT_EMAIL still held the template text 'PEGA_EMAIL_AGENTE', so the field
-- agent account got an address that is not an e-mail and cannot sign in from the field app. This rebinds
-- that account to the current STAFF_FIELD_AGENT_EMAIL and STAFF_FIELD_AGENT_PASSWORD_HASH variables.
-- Set both variables in Render before deploying: while the e-mail is still the template, the migration
-- fails and the previous deploy keeps serving, instead of being marked as applied without effect.

DO $$
BEGIN
    IF position('@' IN '${staff_field_agent_email}') = 0 THEN
        RAISE EXCEPTION 'STAFF_FIELD_AGENT_EMAIL must hold the real field agent e-mail before V1_3_1 runs';
    END IF;
END
$$;

UPDATE identity_access.users
SET email           = LOWER(TRIM('${staff_field_agent_email}')),
    password_hash   = '${staff_field_agent_password_hash}',
    failed_attempts = 0,
    locked_until    = NULL,
    updated_at      = NOW()
WHERE role = 'FIELD_AGENT'
  AND position('@' IN email) = 0;
