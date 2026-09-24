-- MySQL 8 migration, run ONCE with the application stopped, after a database backup.
-- This project uses operator-run SQL rather than Flyway. Hibernate ddl-auto=update
-- cannot backfill the old currency FKs and must not be used instead of this migration.
-- This migration deliberately fails before DDL when the schema is already migrated,
-- freshly created from the current entities, or only partially migrated. MySQL DDL is
-- auto-committing, so a partial run must be restored from backup before retrying.
DELIMITER $$
CREATE PROCEDURE receipt_currency_migration_preflight()
BEGIN
    DECLARE legacy_columns INT DEFAULT 0;
    DECLARE applied_columns INT DEFAULT 0;
    DECLARE legacy_rate_columns INT DEFAULT 0;
    DECLARE applied_rate_columns INT DEFAULT 0;
    DECLARE idempotency_tables INT DEFAULT 0;

    SELECT COUNT(*) INTO legacy_columns FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'account_book_transactions'
      AND COLUMN_NAME IN ('amount');
    SELECT COUNT(*) INTO applied_columns FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'account_book_transactions'
      AND COLUMN_NAME IN ('original_amount', 'conversion_quote_id');
    SELECT COUNT(*) INTO legacy_rate_columns FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'exchange_rate'
      AND COLUMN_NAME IN ('base_currency_id', 'target_currency_id');
    SELECT COUNT(*) INTO applied_rate_columns FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'exchange_rate'
      AND COLUMN_NAME IN ('source_currency_code', 'target_currency_code', 'requested_rate_date', 'rate_fetched_at');
    SELECT COUNT(*) INTO idempotency_tables FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'receipt_batch_registration';

    IF legacy_columns = 1 AND applied_columns = 0
       AND legacy_rate_columns = 2 AND applied_rate_columns = 0
       AND idempotency_tables = 0 THEN
        SET @receipt_migration_state = 'LEGACY_READY';
    ELSEIF legacy_columns = 1 AND applied_columns = 2
       AND legacy_rate_columns = 0 AND applied_rate_columns = 4
       AND idempotency_tables = 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'receipt currency migration: schema is already applied/current';
    ELSE
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'receipt currency migration: unsupported new or partial schema; restore backup or initialize with current schema';
    END IF;
END$$
DELIMITER ;
CALL receipt_currency_migration_preflight();
DROP PROCEDURE receipt_currency_migration_preflight;

-- Preflight: all existing exchange currencies must have three-letter ISO codes.
SELECT er.id, source.code, target.code FROM exchange_rate er
JOIN currency source ON source.id = er.base_currency_id
JOIN currency target ON target.id = er.target_currency_id
WHERE source.code NOT REGEXP '^[A-Za-z]{3}$' OR target.code NOT REGEXP '^[A-Za-z]{3}$';
-- Stop and resolve any rows returned above before continuing.

ALTER TABLE account_book_transactions
    MODIFY amount DECIMAL(28,8) NOT NULL,
    ADD original_amount DECIMAL(28,8) NULL,
    ADD original_currency_code VARCHAR(3) NULL,
    ADD exchange_rate DECIMAL(38,18) NULL,
    ADD requested_rate_date DATE NULL,
    ADD effective_rate_date DATE NULL,
    ADD exchange_rate_provider VARCHAR(50) NULL,
    ADD target_currency_code VARCHAR(3) NULL,
    ADD rate_fetched_at TIMESTAMP(6) NULL,
    ADD converted_at TIMESTAMP(6) NULL,
    ADD rounding_precision INT NULL,
    ADD rounding_mode VARCHAR(20) NULL,
    ADD conversion_policy_version VARCHAR(40) NULL,
    ADD conversion_quote_id VARCHAR(64) NULL;
ALTER TABLE account_book_fixed_cost MODIFY amount DECIMAL(28,8) NOT NULL;
ALTER TABLE account_book_monthly_goals MODIFY goal_amount DECIMAL(28,8) NOT NULL;

ALTER TABLE exchange_rate
    ADD source_currency_code VARCHAR(3) NULL,
    ADD target_currency_code VARCHAR(3) NULL,
    ADD requested_rate_date DATE NULL,
    ADD rate_fetched_at TIMESTAMP(6) NULL,
    MODIFY rate DECIMAL(38,18) NOT NULL;
UPDATE exchange_rate er
JOIN currency source ON source.id = er.base_currency_id
JOIN currency target ON target.id = er.target_currency_id
SET er.source_currency_code = UPPER(source.code),
    er.target_currency_code = UPPER(target.code),
    er.requested_rate_date = er.rate_date,
    er.rate_fetched_at = er.created_at;

-- Hibernate-generated FK names differ by installation; discover them safely.
DELIMITER $$
CREATE PROCEDURE migrate_receipt_exchange_fks()
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE fk_name VARCHAR(64);
    DECLARE fks CURSOR FOR
        SELECT DISTINCT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'exchange_rate'
        AND REFERENCED_TABLE_NAME IS NOT NULL
        AND COLUMN_NAME IN ('base_currency_id', 'target_currency_id');
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;
    OPEN fks;
    drop_loop: LOOP
        FETCH fks INTO fk_name;
        IF done THEN LEAVE drop_loop; END IF;
        SET @receipt_fk_sql = CONCAT('ALTER TABLE exchange_rate DROP FOREIGN KEY `', REPLACE(fk_name, '`', '``'), '`');
        PREPARE receipt_stmt FROM @receipt_fk_sql;
        EXECUTE receipt_stmt;
        DEALLOCATE PREPARE receipt_stmt;
    END LOOP;
    CLOSE fks;
END$$
DELIMITER ;
CALL migrate_receipt_exchange_fks();
DROP PROCEDURE migrate_receipt_exchange_fks;

ALTER TABLE exchange_rate
    DROP INDEX uk_exchange_rate_daily,
    DROP COLUMN base_currency_id,
    DROP COLUMN target_currency_id,
    MODIFY source_currency_code VARCHAR(3) NOT NULL,
    MODIFY target_currency_code VARCHAR(3) NOT NULL,
    MODIFY requested_rate_date DATE NOT NULL,
    ADD CONSTRAINT uk_exchange_rate_daily
        UNIQUE (source_currency_code, target_currency_code, requested_rate_date, provider);

CREATE TABLE receipt_batch_registration (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_book_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    payload_fingerprint VARCHAR(64) NOT NULL,
    response_json LONGTEXT NULL,
    created_by VARCHAR(50) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_receipt_batch_idempotency
        UNIQUE (account_book_id, user_id, idempotency_key)
);
