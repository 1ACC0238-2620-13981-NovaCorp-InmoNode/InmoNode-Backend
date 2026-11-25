-- US-55: a buyer pays an installment through the payment gateway in two steps. Starting the payment opens an intent
-- that fixes the installment, amount and currency before the gateway is called; only the gateway's confirmed webhook
-- credits it. The id is sent to the gateway as the merchant's order reference. At most one intent per installment is
-- OPEN, and an idempotency key returns the same intent to a buyer who retries (Scenario 3).
CREATE TABLE financial_document_control.installment_payment_intents (
    id                         UUID PRIMARY KEY,
    account_statement_id       BIGINT         NOT NULL REFERENCES financial_document_control.account_statements (id),
    installment_number         INTEGER        NOT NULL,
    buyer_id                   BIGINT         NOT NULL,
    amount                     NUMERIC(14, 2) NOT NULL,
    currency                   VARCHAR(3)     NOT NULL,
    status                     VARCHAR(20)    NOT NULL,
    idempotency_key            VARCHAR(255),
    provider_payment_intent_id VARCHAR(255),
    provider_charge_id         VARCHAR(255),
    last_failure               VARCHAR(500),
    created_at                 TIMESTAMPTZ    NOT NULL,
    expires_at                 TIMESTAMPTZ    NOT NULL,
    confirmed_at               TIMESTAMPTZ,
    CONSTRAINT uq_installment_payment_intents_provider UNIQUE (provider_payment_intent_id),
    CONSTRAINT uq_installment_payment_intents_idempotency UNIQUE (buyer_id, idempotency_key),
    CONSTRAINT ck_installment_payment_intents_status
        CHECK (status IN ('OPEN', 'SUCCEEDED', 'UNAPPLIED', 'CANCELED', 'EXPIRED')),
    CONSTRAINT ck_installment_payment_intents_amount CHECK (amount > 0),
    CONSTRAINT ck_installment_payment_intents_confirmed
        CHECK ((status IN ('SUCCEEDED', 'UNAPPLIED')) = (confirmed_at IS NOT NULL))
);

CREATE UNIQUE INDEX uq_installment_payment_intents_open
    ON financial_document_control.installment_payment_intents (account_statement_id, installment_number)
    WHERE status = 'OPEN';

-- US-55, Scenario 2: every authentic notification of the gateway and what was done with it. The provider's event id
-- is the key, so a redelivered event is answered with the outcome it already had instead of crediting again.
CREATE TABLE financial_document_control.payment_gateway_events (
    event_id                   VARCHAR(255) PRIMARY KEY,
    type                       VARCHAR(100) NOT NULL,
    provider_payment_intent_id VARCHAR(255),
    outcome                    VARCHAR(20)  NOT NULL,
    detail                     VARCHAR(500),
    received_at                TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_payment_gateway_events_outcome
        CHECK (outcome IN ('CREDITED', 'DUPLICATE', 'UNAPPLIED', 'REJECTED', 'IGNORED', 'FAILED', 'CANCELED'))
);

-- Where the payment of an installment came from: the finance back office (US-23) or the gateway (US-55), with the
-- gateway's payment reference, which can credit a single installment.
ALTER TABLE financial_document_control.installments
    ADD COLUMN payment_source    VARCHAR(20),
    ADD COLUMN payment_reference VARCHAR(255);

UPDATE financial_document_control.installments SET payment_source = 'BACK_OFFICE' WHERE status = 'PAID';

ALTER TABLE financial_document_control.installments
    ADD CONSTRAINT uq_installments_payment_reference UNIQUE (payment_reference),
    ADD CONSTRAINT ck_installments_payment_source CHECK (payment_source IN ('BACK_OFFICE', 'GATEWAY')),
    ADD CONSTRAINT ck_installments_payment_origin CHECK ((status = 'PAID') = (payment_source IS NOT NULL)),
    ADD CONSTRAINT ck_installments_gateway_reference
        CHECK ((payment_source IS NOT DISTINCT FROM 'GATEWAY') = (payment_reference IS NOT NULL));
