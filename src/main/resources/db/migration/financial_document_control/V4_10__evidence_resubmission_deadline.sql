ALTER TABLE financial_document_control.reservations ADD COLUMN resubmission_deadline timestamptz;
CREATE INDEX ix_reservations_rejected_deadline ON financial_document_control.reservations (resubmission_deadline)
    WHERE status = 'REJECTED';
