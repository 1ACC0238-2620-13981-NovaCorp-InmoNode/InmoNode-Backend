ALTER TABLE financial_document_control.reservations
    ADD COLUMN co_owner_full_name varchar(200),
    ADD COLUMN co_owner_document_type varchar(20),
    ADD COLUMN co_owner_document_number varchar(20),
    ADD COLUMN quotation_id bigint,
    ALTER COLUMN annual_interest_rate TYPE numeric(7,4);

ALTER TABLE financial_document_control.contracts
    ADD COLUMN co_owner_full_name varchar(200),
    ADD COLUMN co_owner_document_type varchar(20),
    ADD COLUMN co_owner_document_number varchar(20);

ALTER TABLE financial_document_control.projects ALTER COLUMN annual_interest_rate TYPE numeric(7,4);
ALTER TABLE financial_document_control.account_statements ALTER COLUMN annual_interest_rate TYPE numeric(7,4);

ALTER TABLE financial_document_control.reservations ADD CONSTRAINT ck_reservation_co_owner_complete
    CHECK ((co_owner_full_name IS NULL AND co_owner_document_type IS NULL AND co_owner_document_number IS NULL)
        OR (co_owner_full_name IS NOT NULL AND co_owner_document_type IS NOT NULL AND co_owner_document_number IS NOT NULL));
ALTER TABLE financial_document_control.contracts ADD CONSTRAINT ck_contract_co_owner_complete
    CHECK ((co_owner_full_name IS NULL AND co_owner_document_type IS NULL AND co_owner_document_number IS NULL)
        OR (co_owner_full_name IS NOT NULL AND co_owner_document_type IS NOT NULL AND co_owner_document_number IS NOT NULL));
