-- Existing persisted schedules remain unchanged; new quotations use TEA and HALF_EVEN.
ALTER TABLE quoting_reservation.quotations ALTER COLUMN annual_interest_rate TYPE numeric(7,4);
