-- Payment evidences of the reservations (2.6.4.1), part of the reservation aggregate: vouchers sent from the field
-- app (US-20) and, later, payments confirmed by a gateway. reference identifies the evidence at its source (the
-- voucher id); it is unique, so the same voucher is never attached twice. A late evidence arrived when the
-- reservation no longer held its lot: it is kept for the back office, but it did not move the reservation.
CREATE TABLE financial_document_control.payment_evidences (
    id                 BIGSERIAL PRIMARY KEY,
    reservation_id     BIGINT         NOT NULL,
    reference          UUID           NOT NULL,
    source             VARCHAR(10)    NOT NULL,
    amount             NUMERIC(14, 2) NOT NULL,
    currency           VARCHAR(3)     NOT NULL,
    operation_date     DATE           NOT NULL,
    operation_code     VARCHAR(50)    NOT NULL,
    manually_corrected BOOLEAN        NOT NULL,
    object_key         VARCHAR(255),
    status             VARCHAR(20)    NOT NULL,
    late               BOOLEAN        NOT NULL,
    submitted_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at         TIMESTAMP      NOT NULL,
    updated_at         TIMESTAMP      NOT NULL,
    CONSTRAINT fk_payment_evidences_reservation
        FOREIGN KEY (reservation_id) REFERENCES financial_document_control.reservations (id),
    CONSTRAINT uq_payment_evidences_reference UNIQUE (reference),
    CONSTRAINT ck_payment_evidences_source CHECK (source IN ('VOUCHER', 'GATEWAY')),
    CONSTRAINT ck_payment_evidences_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_evidences_status CHECK (status IN ('PENDING')),
    CONSTRAINT ck_payment_evidences_voucher_file CHECK (source <> 'VOUCHER' OR object_key IS NOT NULL)
);

CREATE INDEX ix_payment_evidences_reservation_id ON financial_document_control.payment_evidences (reservation_id);
