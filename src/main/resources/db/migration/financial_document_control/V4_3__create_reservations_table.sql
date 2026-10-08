-- Consolidated reservations of a lot (US-06, US-11, US-19), from the field app or the web portal.
-- source_event_id is the id generated on the device: unique, so a re-sent field reservation is not duplicated.
CREATE TABLE financial_document_control.reservations (
    id                      BIGSERIAL PRIMARY KEY,
    lot_id                  BIGINT         NOT NULL,
    channel                 VARCHAR(10)    NOT NULL,
    requester_id            BIGINT         NOT NULL,
    prospect_id             UUID,
    source_event_id         UUID,
    initial_amount          NUMERIC(14, 2) NOT NULL,
    initial_amount_currency VARCHAR(3)     NOT NULL,
    status                  VARCHAR(30)    NOT NULL,
    reserved_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at              TIMESTAMP      NOT NULL,
    updated_at              TIMESTAMP      NOT NULL,
    CONSTRAINT fk_reservations_lot FOREIGN KEY (lot_id) REFERENCES financial_document_control.lots (id),
    CONSTRAINT uq_reservations_source_event UNIQUE (source_event_id),
    CONSTRAINT ck_reservations_channel CHECK (channel IN ('FIELD', 'WEB')),
    CONSTRAINT ck_reservations_status CHECK (status IN ('PENDING_SYNC', 'BLOCKED', 'PENDING_VERIFICATION', 'VERIFIED',
                                                        'REJECTED', 'EXPIRED', 'CANCELLED_BY_CONFLICT'))
);

CREATE INDEX ix_reservations_lot_id ON financial_document_control.reservations (lot_id);

-- A blocked lot points to the reservation holding it and to when that block expires (Lot Block).
ALTER TABLE financial_document_control.lots
    ADD COLUMN current_reservation_id BIGINT,
    ADD COLUMN blocked_until          TIMESTAMP WITH TIME ZONE,
    ADD CONSTRAINT fk_lots_current_reservation
        FOREIGN KEY (current_reservation_id) REFERENCES financial_document_control.reservations (id);

-- Lets the release job find expired blocks without scanning every lot.
CREATE INDEX ix_lots_status_blocked_until ON financial_document_control.lots (status, blocked_until);
