-- Canonical lot inventory (US-53): the only authority on lot availability. The boundary is the lot polygon
-- from the project plan in WKT (WGS84, longitude latitude), so it can move to PostGIS without reshaping.
CREATE TABLE financial_document_control.lots (
    id             BIGSERIAL PRIMARY KEY,
    project_id     BIGINT         NOT NULL,
    code           VARCHAR(30)    NOT NULL,
    area           NUMERIC(12, 2) NOT NULL,
    front          NUMERIC(10, 2),
    depth          NUMERIC(10, 2),
    price_amount   NUMERIC(14, 2) NOT NULL,
    price_currency VARCHAR(3)     NOT NULL,
    status         VARCHAR(30)    NOT NULL,
    boundary_wkt   TEXT           NOT NULL,
    created_at     TIMESTAMP      NOT NULL,
    updated_at     TIMESTAMP      NOT NULL,
    CONSTRAINT fk_lots_project FOREIGN KEY (project_id) REFERENCES financial_document_control.projects (id),
    -- Also serves the lookups by project, as project_id leads the index.
    CONSTRAINT uq_lots_project_code UNIQUE (project_id, code),
    CONSTRAINT ck_lots_status CHECK (status IN ('AVAILABLE', 'BLOCKED', 'PENDING_VERIFICATION', 'RESERVED', 'SOLD'))
);
