ALTER TABLE transaction_entity
    ADD COLUMN type ENUM('EXPENSE','INCOME'),
    ADD COLUMN occurred_at DATETIME(6);

UPDATE transaction_entity
SET type = 'EXPENSE', occurred_at = created_at;

ALTER TABLE transaction_entity
    MODIFY COLUMN type ENUM('EXPENSE','INCOME') NOT NULL;
