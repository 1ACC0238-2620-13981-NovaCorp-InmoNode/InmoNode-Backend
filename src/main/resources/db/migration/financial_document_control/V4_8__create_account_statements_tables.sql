-- Account statements (2.6.4, US-23): one per contract, opened when the buyer agrees to it, with the real schedule of
-- installments (French amortization) of the financed balance. The figures it was opened with are kept, so it stays as
-- agreed even if the project changes. transaction_id is the reservation's id shared by every context.
CREATE TABLE financial_document_control.account_statements (
    id                   BIGSERIAL PRIMARY KEY,
    contract_id          BIGINT         NOT NULL,
    reservation_id       BIGINT         NOT NULL,
    transaction_id       UUID           NOT NULL,
    buyer_id             BIGINT         NOT NULL,
    lot_id               BIGINT         NOT NULL,
    currency             VARCHAR(3)     NOT NULL,
    lot_price            NUMERIC(14, 2) NOT NULL,
    initial_payment      NUMERIC(14, 2) NOT NULL,
    term_months          INTEGER        NOT NULL,
    annual_interest_rate NUMERIC(6, 3)  NOT NULL,
    opened_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at           TIMESTAMP      NOT NULL,
    updated_at           TIMESTAMP      NOT NULL,
    CONSTRAINT fk_account_statements_contract
        FOREIGN KEY (contract_id) REFERENCES financial_document_control.contracts (id),
    CONSTRAINT fk_account_statements_reservation
        FOREIGN KEY (reservation_id) REFERENCES financial_document_control.reservations (id),
    CONSTRAINT uq_account_statements_contract UNIQUE (contract_id),
    CONSTRAINT uq_account_statements_reservation UNIQUE (reservation_id),
    CONSTRAINT ck_account_statements_amounts CHECK (initial_payment > 0 AND initial_payment < lot_price),
    CONSTRAINT ck_account_statements_term CHECK (term_months BETWEEN 1 AND 360)
);

-- Lets a buyer find all their statements (US-27).
CREATE INDEX ix_account_statements_buyer_id ON financial_document_control.account_statements (buyer_id);

-- reminder_sent_at and overdue_notified_at record the notices of US-24, so none is sent twice.
CREATE TABLE financial_document_control.installments (
    id                   BIGSERIAL PRIMARY KEY,
    account_statement_id BIGINT         NOT NULL,
    number               INTEGER        NOT NULL,
    due_date             DATE           NOT NULL,
    amount               NUMERIC(14, 2) NOT NULL,
    principal            NUMERIC(14, 2) NOT NULL,
    interest             NUMERIC(14, 2) NOT NULL,
    status               VARCHAR(20)    NOT NULL,
    paid_at              TIMESTAMP WITH TIME ZONE,
    paid_amount          NUMERIC(14, 2),
    penalty              NUMERIC(14, 2) NOT NULL,
    reminder_sent_at     TIMESTAMP WITH TIME ZONE,
    overdue_notified_at  TIMESTAMP WITH TIME ZONE,
    created_at           TIMESTAMP      NOT NULL,
    updated_at           TIMESTAMP      NOT NULL,
    CONSTRAINT fk_installments_account_statement
        FOREIGN KEY (account_statement_id) REFERENCES financial_document_control.account_statements (id),
    CONSTRAINT uq_installments_number UNIQUE (account_statement_id, number),
    CONSTRAINT ck_installments_status CHECK (status IN ('PENDING', 'PAID', 'OVERDUE')),
    CONSTRAINT ck_installments_paid CHECK (status <> 'PAID' OR (paid_at IS NOT NULL AND paid_amount IS NOT NULL)),
    CONSTRAINT ck_installments_amounts CHECK (amount >= 0 AND principal >= 0 AND interest >= 0 AND penalty >= 0)
);

-- Lets the daily review find the unpaid installments by due date (US-24).
CREATE INDEX ix_installments_status_due_date ON financial_document_control.installments (status, due_date);
