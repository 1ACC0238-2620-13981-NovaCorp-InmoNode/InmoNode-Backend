CREATE TABLE identity_access.users (
    id                 BIGSERIAL PRIMARY KEY,
    email              VARCHAR(255) NOT NULL,
    password_hash      VARCHAR(100) NOT NULL,
    role               VARCHAR(30)  NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    verification_token VARCHAR(64),
    failed_attempts    INTEGER      NOT NULL DEFAULT 0,
    locked_until       TIMESTAMP WITH TIME ZONE,
    created_at         TIMESTAMP    NOT NULL,
    updated_at         TIMESTAMP    NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_verification_token UNIQUE (verification_token),
    CONSTRAINT ck_users_role CHECK (role IN ('BUYER', 'FIELD_AGENT', 'CATALOG_ADMIN', 'FINANCE_ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('INACTIVE', 'ACTIVE'))
);
