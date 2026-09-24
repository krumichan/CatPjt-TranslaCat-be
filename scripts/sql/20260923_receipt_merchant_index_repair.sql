-- MySQL 8, idempotent repair for environments whose receipt columns were
-- created by Hibernate before 20260921_receipt_payment_semantics.sql ran.
-- Stop writes and take a restorable backup before DDL. MySQL DDL auto-commits.
DELIMITER $$
CREATE PROCEDURE repair_receipt_merchant_index()
BEGIN
    DECLARE receipt_columns INT DEFAULT 0;
    DECLARE matching_indexes INT DEFAULT 0;
    DECLARE conflicting_indexes INT DEFAULT 0;

    SELECT COUNT(*) INTO receipt_columns
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'account_book_transactions'
      AND COLUMN_NAME IN ('account_book_id', 'merchant_key');

    IF receipt_columns <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'receipt merchant index repair: required columns are missing';
    END IF;

    SELECT COUNT(*) INTO matching_indexes
    FROM (
        SELECT INDEX_NAME
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'account_book_transactions'
        GROUP BY INDEX_NAME
        HAVING GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') =
               'account_book_id,merchant_key'
    ) AS matching;

    SELECT COUNT(*) INTO conflicting_indexes
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'account_book_transactions'
      AND INDEX_NAME = 'ix_account_book_transactions_merchant_key';

    IF matching_indexes = 0 AND conflicting_indexes = 0 THEN
        CREATE INDEX ix_account_book_transactions_merchant_key
            ON account_book_transactions(account_book_id, merchant_key);
    ELSEIF matching_indexes = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'receipt merchant index repair: expected index name has different columns';
    END IF;
END$$
DELIMITER ;

CALL repair_receipt_merchant_index();
DROP PROCEDURE repair_receipt_merchant_index;

-- Recovery: dropping this non-unique index is safe only after confirming the
-- previous schema did not already contain an equivalent index under another name.
