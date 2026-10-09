-- Back-office verification of the payment evidences (2.6.4): each one is approved, or rejected with a reason the
-- requester sees (US-25). A decided evidence keeps who decided and when; an approved one verifies its reservation.
ALTER TABLE financial_document_control.payment_evidences
    ADD COLUMN reviewer_id   BIGINT,
    ADD COLUMN reviewer_note VARCHAR(500),
    ADD COLUMN reviewed_at   TIMESTAMP WITH TIME ZONE,
    DROP CONSTRAINT ck_payment_evidences_status,
    ADD CONSTRAINT ck_payment_evidences_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    ADD CONSTRAINT ck_payment_evidences_review
        CHECK (status = 'PENDING' OR (reviewer_id IS NOT NULL AND reviewed_at IS NOT NULL)),
    ADD CONSTRAINT ck_payment_evidences_rejection_reason CHECK (status <> 'REJECTED' OR reviewer_note IS NOT NULL);

-- Lets the verification queue find the pending evidences without scanning every one.
CREATE INDEX ix_payment_evidences_status ON financial_document_control.payment_evidences (status);

ALTER TABLE financial_document_control.reservations
    ADD COLUMN verified_at TIMESTAMP WITH TIME ZONE;
