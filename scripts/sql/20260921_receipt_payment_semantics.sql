-- MySQL 8, run ONCE after 20260919_receipt_currency_precision.sql.
-- Stop the application and take a restorable backup before DDL. MySQL DDL auto-commits.
DELIMITER $$
CREATE PROCEDURE receipt_payment_semantics_preflight()
BEGIN
    DECLARE prior_columns INT DEFAULT 0;
    DECLARE new_columns INT DEFAULT 0;
    SELECT COUNT(*) INTO prior_columns FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'account_book_transactions'
        AND COLUMN_NAME IN ('original_amount', 'conversion_quote_id');
    SELECT COUNT(*) INTO new_columns FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'account_book_transactions'
        AND COLUMN_NAME IN ('purchase_total', 'book_amount', 'merchant_key',
                            'receipt_payment_breakdown_json', 'receipt_source_image_id',
                            'cash_tendered', 'change_amount', 'amount_policy_version',
                            'amount_reason', 'amount_review_status', 'receipt_branch_name',
                            'receipt_analysis_revision', 'receipt_transaction_time');
    IF prior_columns = 2 AND new_columns = 0 THEN
        SET @receipt_payment_state = 'READY';
    ELSEIF prior_columns = 2 AND new_columns = 13 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'receipt payment semantics migration: already applied';
    ELSE
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'receipt payment semantics migration: unsupported partial schema; restore backup';
    END IF;
END$$
DELIMITER ;
CALL receipt_payment_semantics_preflight();
DROP PROCEDURE receipt_payment_semantics_preflight;

ALTER TABLE account_book_transactions
    ADD purchase_total DECIMAL(28,8) NULL,
    ADD book_amount DECIMAL(28,8) NULL,
    ADD receipt_payment_breakdown_json TEXT NULL,
    ADD cash_tendered DECIMAL(28,8) NULL,
    ADD change_amount DECIMAL(28,8) NULL,
    ADD amount_policy_version VARCHAR(40) NULL,
    ADD amount_reason VARCHAR(100) NULL,
    ADD amount_review_status VARCHAR(20) NULL,
    ADD receipt_branch_name VARCHAR(100) NULL,
    ADD merchant_key VARCHAR(120) NULL,
    ADD receipt_source_image_id VARCHAR(100) NULL,
    ADD receipt_analysis_revision INT NULL,
    ADD receipt_transaction_time VARCHAR(8) NULL;

-- Preserve existing transactions as legacy rows. Receipt-created rows already use
-- original_amount as their bookkeeping amount, but no payment allocation is invented.
UPDATE account_book_transactions
SET purchase_total = original_amount,
    book_amount = original_amount,
    amount_policy_version = CASE WHEN original_amount IS NULL THEN NULL ELSE 'legacy-receipt-v0' END,
    amount_reason = CASE WHEN original_amount IS NULL THEN NULL ELSE 'LEGACY_ORIGINAL_AMOUNT' END,
    amount_review_status = CASE WHEN original_amount IS NULL THEN NULL ELSE 'READY' END,
    merchant_key = CASE WHEN store_name IS NULL OR TRIM(store_name) = '' THEN NULL
                        ELSE LOWER(REGEXP_REPLACE(TRIM(store_name), '[[:space:]-]+', ' ')) END;

CREATE INDEX ix_account_book_transactions_merchant_key
    ON account_book_transactions(account_book_id, merchant_key);

-- Recovery: restore the pre-migration backup. Do not attempt a partial down migration
-- after writes using the new receipt contract because payment evidence would be lost.
