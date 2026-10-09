-- When the current verification token was emailed: drives the resend cooldown and the link expiration.
ALTER TABLE identity_access.users ADD COLUMN verification_sent_at TIMESTAMP WITH TIME ZONE;

-- Accounts already waiting for verification get a full validity window from this migration on,
-- instead of their links expiring at once.
UPDATE identity_access.users SET verification_sent_at = NOW() WHERE verification_token IS NOT NULL;
