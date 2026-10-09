-- Financing plan of the web reservations (2.6.4.1): the term and rate of the quotation the buyer accepted, on the lot
-- price when the lot was blocked. It is the base of the account statement. Field reservations have none, and
-- neither do web reservations made before this migration.
ALTER TABLE financial_document_control.reservations
    ADD COLUMN lot_price            NUMERIC(14, 2),
    ADD COLUMN term_months          INTEGER,
    ADD COLUMN annual_interest_rate NUMERIC(6, 3),
    ADD CONSTRAINT ck_reservations_financing_plan
        CHECK ((lot_price IS NULL AND term_months IS NULL AND annual_interest_rate IS NULL)
            OR (lot_price > 0 AND term_months BETWEEN 1 AND 360 AND annual_interest_rate BETWEEN 0 AND 100));
