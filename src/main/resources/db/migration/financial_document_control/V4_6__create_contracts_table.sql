-- Preliminary purchase contracts (2.6.4, US-21): one per reservation, issued by the back office once the payment was
-- verified, with its PDF in the file repository. transaction_id repeats the reservation's source_event_id so the buyer
-- can reach the contract with the id they already know.
CREATE TABLE financial_document_control.contracts (
    id                    BIGSERIAL PRIMARY KEY,
    reservation_id        BIGINT       NOT NULL,
    transaction_id        UUID         NOT NULL,
    buyer_id              BIGINT       NOT NULL,
    lot_id                BIGINT       NOT NULL,
    document_id           UUID         NOT NULL,
    object_key            VARCHAR(255) NOT NULL,
    size_bytes            BIGINT       NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    issued_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    issued_by             BIGINT       NOT NULL,
    buyer_acknowledged_at TIMESTAMP WITH TIME ZONE,
    created_at            TIMESTAMP    NOT NULL,
    updated_at            TIMESTAMP    NOT NULL,
    CONSTRAINT fk_contracts_reservation
        FOREIGN KEY (reservation_id) REFERENCES financial_document_control.reservations (id),
    CONSTRAINT uq_contracts_reservation UNIQUE (reservation_id),
    CONSTRAINT uq_contracts_transaction UNIQUE (transaction_id),
    CONSTRAINT ck_contracts_status CHECK (status IN ('ISSUED')),
    CONSTRAINT ck_contracts_size_bytes CHECK (size_bytes BETWEEN 1 AND 10485760)
);
