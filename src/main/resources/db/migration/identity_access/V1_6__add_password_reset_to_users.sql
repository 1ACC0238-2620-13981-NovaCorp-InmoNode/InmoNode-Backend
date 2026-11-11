-- US-52: the link to reset a forgotten password. Only the SHA-256 of the emailed token is stored, so a leaked
-- table cannot be used to take over an account. requested_at is when the last link was emailed: it drives
-- the link expiration and the cooldown between emails.
ALTER TABLE identity_access.users
    ADD COLUMN password_reset_token_hash   VARCHAR(64),
    ADD COLUMN password_reset_requested_at TIMESTAMP WITH TIME ZONE,
    ADD CONSTRAINT uq_users_password_reset_token_hash UNIQUE (password_reset_token_hash);
