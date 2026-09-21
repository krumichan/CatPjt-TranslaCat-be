-- MySQL 8 migration, run ONCE with the application stopped, after a database backup.
-- This project uses operator-run SQL rather than Flyway. Hibernate ddl-auto=update
-- cannot backfill the old currency FKs and must not be used instead of this migration.
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
    ADD exchange_rate_provider VARCHAR(50) NULL;
ALTER TABLE account_book_fixed_cost MODIFY amount DECIMAL(28,8) NOT NULL;
ALTER TABLE account_book_monthly_goals MODIFY goal_amount DECIMAL(28,8) NOT NULL;

ALTER TABLE exchange_rate
    ADD source_currency_code VARCHAR(3) NULL,
    ADD target_currency_code VARCHAR(3) NULL,
    ADD requested_rate_date DATE NULL,
    MODIFY rate DECIMAL(38,18) NOT NULL;
UPDATE exchange_rate er
JOIN currency source ON source.id = er.base_currency_id
JOIN currency target ON target.id = er.target_currency_id
SET er.source_currency_code = UPPER(source.code),
    er.target_currency_code = UPPER(target.code),
    er.requested_rate_date = er.rate_date;

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
