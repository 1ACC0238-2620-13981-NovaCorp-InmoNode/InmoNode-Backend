CREATE TABLE financial_document_control.project_stages (
    project_id bigint NOT NULL REFERENCES financial_document_control.projects(id),
    stage_order integer NOT NULL,
    stage_name varchar(80) NOT NULL,
    PRIMARY KEY (project_id, stage_order),
    UNIQUE (project_id, stage_name)
);
-- Preserve projects/lots created through the original bulk-import flow.
INSERT INTO financial_document_control.project_stages(project_id, stage_order, stage_name)
SELECT id, 0, 'Etapa 1' FROM financial_document_control.projects;
ALTER TABLE financial_document_control.lots ADD COLUMN stage_name varchar(80) NOT NULL DEFAULT 'Etapa 1';
ALTER TABLE financial_document_control.lots ADD CONSTRAINT fk_lots_project_stage
    FOREIGN KEY (project_id, stage_name) REFERENCES financial_document_control.project_stages(project_id, stage_name);
ALTER TABLE financial_document_control.lots DROP CONSTRAINT ck_lots_status;
ALTER TABLE financial_document_control.lots ADD CONSTRAINT ck_lots_status
    CHECK (status IN ('DRAFT', 'AVAILABLE', 'BLOCKED', 'PENDING_VERIFICATION', 'RESERVED', 'SOLD'));
