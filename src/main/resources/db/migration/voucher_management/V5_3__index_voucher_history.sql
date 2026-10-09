-- US-46: restrict by owner/reservation before paging the receipt history.
CREATE INDEX IF NOT EXISTS idx_reservation_operations_owner_reservation
    ON voucher_management.reservation_operations (owner_id, reservation_id);

CREATE INDEX IF NOT EXISTS idx_vouchers_reservation_received_id
    ON voucher_management.vouchers (reservation_id, received_at DESC, id DESC);
