-- Payment vouchers of the reservation operations (US-20): the file uploaded to the file repository with a presigned
-- URL (US-33) and the data the field app read from it with OCR, as the agent confirmed or corrected it (US-08..10).
-- voucher_id is the id generated on the device; it is unique, so a re-sent voucher is answered with the original.
CREATE TABLE voucher_management.vouchers (
    id                 BIGSERIAL PRIMARY KEY,
    voucher_id         UUID           NOT NULL,
    reservation_id     UUID           NOT NULL,
    object_key         VARCHAR(255)   NOT NULL,
    content_type       VARCHAR(50)    NOT NULL,
    size_bytes         BIGINT         NOT NULL,
    amount             NUMERIC(14, 2) NOT NULL,
    currency           VARCHAR(3)     NOT NULL,
    operation_date     DATE           NOT NULL,
    operation_code     VARCHAR(50)    NOT NULL,
    ocr_confidence     NUMERIC(4, 3),
    manually_corrected BOOLEAN        NOT NULL,
    status             VARCHAR(20)    NOT NULL,
    received_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at         TIMESTAMP      NOT NULL,
    updated_at         TIMESTAMP      NOT NULL,
    CONSTRAINT uq_vouchers_voucher_id UNIQUE (voucher_id),
    CONSTRAINT fk_vouchers_reservation_operation
        FOREIGN KEY (reservation_id) REFERENCES voucher_management.reservation_operations (reservation_id),
    CONSTRAINT ck_vouchers_content_type CHECK (content_type IN ('image/jpeg', 'image/png', 'application/pdf')),
    CONSTRAINT ck_vouchers_size_bytes CHECK (size_bytes BETWEEN 1 AND 5242880),
    CONSTRAINT ck_vouchers_amount CHECK (amount > 0),
    CONSTRAINT ck_vouchers_ocr_confidence CHECK (ocr_confidence BETWEEN 0 AND 1),
    CONSTRAINT ck_vouchers_status CHECK (status IN ('SYNCED'))
);

CREATE INDEX ix_vouchers_reservation_id ON voucher_management.vouchers (reservation_id);
