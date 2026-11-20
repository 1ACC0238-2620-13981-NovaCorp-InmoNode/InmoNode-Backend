-- Negotiation terms of a field reservation (US-58): the discount on the lot price authorized by management, in the
-- currency of the initial amount; a bonus, free text that does not change the price; and notes of the negotiation.
-- All optional: web reservations, and field reservations without special conditions, have none.
ALTER TABLE financial_document_control.reservations
    ADD COLUMN discount_amount   NUMERIC(14, 2),
    ADD COLUMN bonus             VARCHAR(150),
    ADD COLUMN negotiation_notes VARCHAR(500),
    ADD CONSTRAINT ck_reservations_discount_amount CHECK (discount_amount IS NULL OR discount_amount >= 0);
