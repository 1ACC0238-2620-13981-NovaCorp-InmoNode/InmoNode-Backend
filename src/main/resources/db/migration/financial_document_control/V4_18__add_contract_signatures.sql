-- Electronic signature of the issued contracts (US-56) through DocuSeal. The buyer signs a signature sheet that cites
-- the contract and the SHA-256 of the PDF the backend issued; the signed sheet is kept in the file repository.

ALTER TABLE financial_document_control.contracts
    ADD COLUMN document_sha256          VARCHAR(64),
    ADD COLUMN signature_submission_id  BIGINT,
    ADD COLUMN signing_url              VARCHAR(500),
    ADD COLUMN signature_requested_at   TIMESTAMP WITH TIME ZONE,
    ADD COLUMN signed_at                TIMESTAMP WITH TIME ZONE,
    ADD COLUMN signed_object_key        VARCHAR(255),
    ADD COLUMN signed_size_bytes        BIGINT,
    ADD COLUMN signed_sha256            VARCHAR(64),
    ADD COLUMN signature_declined_at    TIMESTAMP WITH TIME ZONE,
    ADD COLUMN signature_decline_reason VARCHAR(500);

ALTER TABLE financial_document_control.contracts DROP CONSTRAINT ck_contracts_status;
ALTER TABLE financial_document_control.contracts
    ADD CONSTRAINT ck_contracts_status
        CHECK (status IN ('ISSUED', 'SIGNATURE_REQUESTED', 'SIGNED', 'SIGNATURE_DECLINED')),
    ADD CONSTRAINT uq_contracts_signature_submission UNIQUE (signature_submission_id),
    ADD CONSTRAINT ck_contracts_document_sha256 CHECK (document_sha256 ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT ck_contracts_signature_requested
        CHECK (status = 'ISSUED' OR (signature_submission_id IS NOT NULL AND signature_requested_at IS NOT NULL
                                     AND document_sha256 IS NOT NULL)),
    ADD CONSTRAINT ck_contracts_signed
        CHECK ((status = 'SIGNED') = (signed_at IS NOT NULL AND signed_object_key IS NOT NULL
                                      AND signed_size_bytes > 0 AND signed_sha256 IS NOT NULL)),
    ADD CONSTRAINT ck_contracts_signature_declined
        CHECK ((status = 'SIGNATURE_DECLINED') = (signature_declined_at IS NOT NULL));

-- Outbox of the requests to sign: one per contract, sent by a job with growing waits between attempts.
CREATE TABLE financial_document_control.contract_signature_requests (
    contract_id     BIGINT PRIMARY KEY,
    status          VARCHAR(10)              NOT NULL,
    attempts        INTEGER                  NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_error      VARCHAR(500),
    requested_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at         TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_contract_signature_requests_contract
        FOREIGN KEY (contract_id) REFERENCES financial_document_control.contracts (id),
    CONSTRAINT ck_contract_signature_requests_status CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT ck_contract_signature_requests_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_contract_signature_requests_sent CHECK ((status = 'SENT') = (sent_at IS NOT NULL))
);

CREATE INDEX ix_contract_signature_requests_due
    ON financial_document_control.contract_signature_requests (next_attempt_at)
    WHERE status = 'PENDING';

-- Every authentic callback of the signature provider, by a key made of its type and its submission: DocuSeal sends
-- no event id. A redelivery answers the outcome it already had.
CREATE TABLE financial_document_control.signature_provider_events (
    event_key     VARCHAR(150) PRIMARY KEY,
    event_type    VARCHAR(50)              NOT NULL,
    submission_id BIGINT,
    contract_id   BIGINT,
    outcome       VARCHAR(20)              NOT NULL,
    detail        VARCHAR(500),
    received_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_signature_provider_events_outcome
        CHECK (outcome IN ('SIGNED', 'DUPLICATE', 'DECLINED', 'REJECTED', 'IGNORED'))
);
