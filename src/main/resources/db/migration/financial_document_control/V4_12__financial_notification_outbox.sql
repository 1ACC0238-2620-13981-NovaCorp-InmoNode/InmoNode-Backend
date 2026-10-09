CREATE TABLE financial_document_control.notification_outbox (
    id bigserial PRIMARY KEY,
    event_key varchar(200) NOT NULL UNIQUE,
    recipient varchar(254) NOT NULL,
    subject varchar(254) NOT NULL,
    body text NOT NULL,
    created_at timestamptz NOT NULL,
    next_attempt_at timestamptz NOT NULL,
    delivered_at timestamptz,
    attempts integer NOT NULL DEFAULT 0,
    last_error varchar(100),
    CONSTRAINT ck_notification_attempts CHECK (attempts >= 0)
);
CREATE INDEX ix_notification_outbox_pending ON financial_document_control.notification_outbox(next_attempt_at, id)
    WHERE delivered_at IS NULL;
