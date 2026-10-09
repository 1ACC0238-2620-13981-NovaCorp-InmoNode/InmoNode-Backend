-- Web separation requests of the buyers (US-19), each backed by one of their quotations. transaction_id is the id
-- every context uses for the separation (the reservation's source_event_id in financial, the operation's
-- reservation_id in vouchers); it is unique. Rejected requests are kept to trace concurrency on a lot.
CREATE TABLE quoting_reservation.separation_requests (
    id               BIGSERIAL PRIMARY KEY,
    transaction_id   UUID           NOT NULL,
    lot_id           BIGINT         NOT NULL,
    buyer_id         BIGINT         NOT NULL,
    quotation_id     BIGINT         NOT NULL,
    initial_amount   NUMERIC(14, 2) NOT NULL,
    currency         VARCHAR(3)     NOT NULL,
    status           VARCHAR(30)    NOT NULL,
    requested_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    lock_expires_at  TIMESTAMP WITH TIME ZONE,
    rejection_reason VARCHAR(30),
    created_at       TIMESTAMP      NOT NULL,
    updated_at       TIMESTAMP      NOT NULL,
    CONSTRAINT uq_separation_requests_transaction_id UNIQUE (transaction_id),
    CONSTRAINT fk_separation_requests_quotation
        FOREIGN KEY (quotation_id) REFERENCES quoting_reservation.quotations (id),
    CONSTRAINT ck_separation_requests_status CHECK (status IN ('BLOCKED', 'REJECTED_UNAVAILABLE')),
    CONSTRAINT ck_separation_requests_lock CHECK (status <> 'BLOCKED' OR lock_expires_at IS NOT NULL)
);

-- Finds the request a buyer already holds on a lot.
CREATE INDEX ix_separation_requests_buyer_lot ON quoting_reservation.separation_requests (buyer_id, lot_id);
