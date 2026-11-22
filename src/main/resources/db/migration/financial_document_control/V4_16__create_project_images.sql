-- US-57: photos and renders of a project for the gallery of its page. The catalog admin uploads each file straight
-- to the file repository (US-33) with a presigned URL and registers it here; object_key is where it lives there.
-- JPEG or PNG up to 5 MB, like the identity documents. The page of the project shows them in the order they were
-- registered.
CREATE TABLE financial_document_control.project_images (
    id            BIGSERIAL PRIMARY KEY,
    project_id    BIGINT       NOT NULL REFERENCES financial_document_control.projects (id),
    image_id      UUID         NOT NULL,
    content_type  VARCHAR(20)  NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    object_key    VARCHAR(255) NOT NULL,
    registered_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_project_images_image_id UNIQUE (image_id),
    CONSTRAINT ck_project_images_content_type CHECK (content_type IN ('image/jpeg', 'image/png')),
    CONSTRAINT ck_project_images_size_bytes CHECK (size_bytes BETWEEN 1 AND 5242880)
);

CREATE INDEX ix_project_images_project ON financial_document_control.project_images (project_id, registered_at, id);
