-- Photo of the buyer's identity document captured in the field (US-59), stored in the file repository under
-- identity-documents/{source_event_id}/{identity_document_id}.{jpg|png}. A field reservation without it shows its file
-- as "Documento de identidad pendiente" until the agent attaches it.
ALTER TABLE financial_document_control.reservations
    ADD COLUMN identity_document_id           UUID,
    ADD COLUMN identity_document_content_type VARCHAR(20),
    ADD COLUMN identity_document_size_bytes   BIGINT,
    ADD COLUMN identity_document_attached_at  TIMESTAMP WITH TIME ZONE,
    ADD CONSTRAINT ck_reservations_identity_document CHECK (
        (identity_document_id IS NULL AND identity_document_content_type IS NULL
            AND identity_document_size_bytes IS NULL AND identity_document_attached_at IS NULL)
        OR (identity_document_id IS NOT NULL AND identity_document_content_type IN ('image/jpeg', 'image/png')
            AND identity_document_size_bytes BETWEEN 1 AND 5242880 AND identity_document_attached_at IS NOT NULL));
