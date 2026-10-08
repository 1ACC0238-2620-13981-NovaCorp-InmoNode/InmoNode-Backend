-- Long-lived, revocable credentials that renew the short-lived JWT. Only the SHA-256 of each token
-- is stored, so a leaked table cannot be replayed. Rotated on every use; revoked_at marks used,
-- signed-out or compromised tokens.
CREATE TABLE identity_access.refresh_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    token_hash VARCHAR(64)  NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL,
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id)
        REFERENCES identity_access.users (id) ON DELETE CASCADE,
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash)
);

CREATE INDEX ix_refresh_tokens_user_id ON identity_access.refresh_tokens (user_id);
