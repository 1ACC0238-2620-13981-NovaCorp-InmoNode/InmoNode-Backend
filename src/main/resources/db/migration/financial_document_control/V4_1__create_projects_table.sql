-- Real estate projects of the catalog (US-53). Their lots are added later and they are published only
-- once at least one lot is loaded. Rates and percentages are stored as percentages (12.5 = 12.5 %).
CREATE TABLE financial_document_control.projects (
    id                          BIGSERIAL PRIMARY KEY,
    name                        VARCHAR(150)     NOT NULL,
    location                    VARCHAR(255)     NOT NULL,
    latitude                    DOUBLE PRECISION,
    longitude                   DOUBLE PRECISION,
    cover_image_url             VARCHAR(500),
    min_down_payment_percentage NUMERIC(5, 2)    NOT NULL,
    annual_interest_rate        NUMERIC(6, 3)    NOT NULL,
    max_term_months             INTEGER          NOT NULL,
    late_fee_rate               NUMERIC(6, 3)    NOT NULL,
    status                      VARCHAR(20)      NOT NULL,
    created_at                  TIMESTAMP        NOT NULL,
    updated_at                  TIMESTAMP        NOT NULL,
    CONSTRAINT ck_projects_status CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT ck_projects_coordinates CHECK ((latitude IS NULL) = (longitude IS NULL))
);
