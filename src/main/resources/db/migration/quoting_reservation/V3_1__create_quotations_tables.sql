-- Financing simulations of the buyers (US-17): a copy of the lot price and the project's rate at that moment, so a
-- quotation stays as it was shown, and its projected schedule (French amortization) as one row per installment.
-- The financed amount, the fixed installment and the totals are derived from the installments, so they are not stored.
CREATE TABLE quoting_reservation.quotations (
    id                   BIGSERIAL PRIMARY KEY,
    buyer_id             BIGINT         NOT NULL,
    lot_id               BIGINT         NOT NULL,
    project_id           BIGINT         NOT NULL,
    lot_code             VARCHAR(30)    NOT NULL,
    lot_price            NUMERIC(14, 2) NOT NULL,
    currency             VARCHAR(3)     NOT NULL,
    initial_payment      NUMERIC(14, 2) NOT NULL,
    term_months          INTEGER        NOT NULL,
    annual_interest_rate NUMERIC(6, 3)  NOT NULL,
    generated_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    valid_until          TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at           TIMESTAMP      NOT NULL,
    updated_at           TIMESTAMP      NOT NULL,
    CONSTRAINT ck_quotations_initial_payment CHECK (initial_payment > 0 AND initial_payment < lot_price),
    CONSTRAINT ck_quotations_term_months CHECK (term_months BETWEEN 1 AND 360)
);

CREATE INDEX ix_quotations_buyer_id ON quoting_reservation.quotations (buyer_id);

CREATE TABLE quoting_reservation.quotation_installments (
    id           BIGSERIAL PRIMARY KEY,
    quotation_id BIGINT         NOT NULL,
    number       INTEGER        NOT NULL,
    due_date     DATE           NOT NULL,
    amount       NUMERIC(14, 2) NOT NULL,
    principal    NUMERIC(14, 2) NOT NULL,
    interest     NUMERIC(14, 2) NOT NULL,
    balance      NUMERIC(14, 2) NOT NULL,
    created_at   TIMESTAMP      NOT NULL,
    updated_at   TIMESTAMP      NOT NULL,
    CONSTRAINT fk_quotation_installments_quotation
        FOREIGN KEY (quotation_id) REFERENCES quoting_reservation.quotations (id),
    CONSTRAINT uq_quotation_installments_number UNIQUE (quotation_id, number)
);
