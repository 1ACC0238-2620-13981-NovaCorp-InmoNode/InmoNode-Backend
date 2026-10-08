-- Prospects registered by field agents, offline included (US-04), received when the app synchronizes (US-11).
-- prospect_id is the id generated on the device: unique, so a re-sent prospect updates the same row.
CREATE TABLE catalog_management.prospects (
    id            BIGSERIAL PRIMARY KEY,
    prospect_id   UUID         NOT NULL,
    agent_id      BIGINT       NOT NULL,
    document      VARCHAR(12)  NOT NULL,
    full_name     VARCHAR(150) NOT NULL,
    phone         VARCHAR(20),
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    CONSTRAINT uq_prospects_prospect_id UNIQUE (prospect_id)
);

CREATE INDEX ix_prospects_agent_id ON catalog_management.prospects (agent_id);
