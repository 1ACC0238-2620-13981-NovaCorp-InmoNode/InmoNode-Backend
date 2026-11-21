-- US-45: PDFs generated in the background. A request answers "PROCESSING" at once; the document consumer marks it
-- READY with the size of the file it stored, or FAILED with the reason. The id is also the id of the stored file.
CREATE TABLE financial_document_control.document_requests (
    id uuid PRIMARY KEY,
    kind varchar(20) NOT NULL,
    reference varchar(64) NOT NULL,
    requested_by bigint NOT NULL,
    object_key varchar(255) NOT NULL,
    status varchar(20) NOT NULL,
    size_bytes bigint,
    failure_code varchar(64),
    requested_at timestamptz NOT NULL,
    completed_at timestamptz,
    CONSTRAINT ck_document_requests_kind CHECK (kind IN ('QUOTATION', 'CONTRACT')),
    CONSTRAINT ck_document_requests_status CHECK (status IN ('PROCESSING', 'READY', 'FAILED')),
    CONSTRAINT ck_document_requests_ready CHECK ((status = 'READY') = (size_bytes IS NOT NULL AND size_bytes > 0)),
    CONSTRAINT ck_document_requests_failed CHECK ((status = 'FAILED') = (failure_code IS NOT NULL)),
    CONSTRAINT ck_document_requests_completed CHECK ((status = 'PROCESSING') = (completed_at IS NULL))
);
