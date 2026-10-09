-- Reservations this context accepts payment vouchers for ("operación de separación" in the vouchers canvas):
-- its own copy, fed by the "Lote separado" event of the field context (and later by web reservation requests).
-- reservation_id is the id shared by every context; it is unique, so a repeated event is ignored.
CREATE TABLE voucher_management.reservation_operations (
    id              BIGSERIAL PRIMARY KEY,
    reservation_id  UUID           NOT NULL,
    channel         VARCHAR(10)    NOT NULL,
    owner_id        BIGINT         NOT NULL,
    lot_id          BIGINT         NOT NULL,
    initial_amount  NUMERIC(14, 2) NOT NULL,
    reserved_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    evidence_due_at TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP      NOT NULL,
    updated_at      TIMESTAMP      NOT NULL,
    CONSTRAINT uq_reservation_operations_reservation_id UNIQUE (reservation_id),
    CONSTRAINT ck_reservation_operations_channel CHECK (channel IN ('FIELD', 'WEB'))
);
