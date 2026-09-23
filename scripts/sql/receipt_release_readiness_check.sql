-- MySQL 8 read-only release preflight for VISION_PARALLEL_REVIEW_ASSISTED.
-- Run against the intended production schema before deployment. Every row must
-- report PASS. This script does not create, alter, update, or lock application data.

SELECT 'mysql_version_8_or_newer' AS check_name,
       'major version >= 8' AS expected,
       VERSION() AS actual,
       IF(CAST(SUBSTRING_INDEX(VERSION(), '.', 1) AS UNSIGNED) >= 8, 'PASS', 'FAIL') AS result
UNION ALL
SELECT 'account_book_transactions_receipt_columns', '27 required columns',
       CAST(COUNT(*) AS CHAR), IF(COUNT(*) = 27, 'PASS', 'FAIL')
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'account_book_transactions'
  AND COLUMN_NAME IN (
      'amount', 'original_amount', 'original_currency_code', 'exchange_rate',
      'requested_rate_date', 'effective_rate_date', 'exchange_rate_provider',
      'target_currency_code', 'rate_fetched_at', 'converted_at',
      'rounding_precision', 'rounding_mode', 'conversion_policy_version',
      'conversion_quote_id', 'purchase_total', 'book_amount',
      'receipt_payment_breakdown_json', 'cash_tendered', 'change_amount',
      'amount_policy_version', 'amount_reason', 'amount_review_status',
      'receipt_branch_name', 'merchant_key', 'receipt_source_image_id',
      'receipt_analysis_revision', 'receipt_transaction_time'
  )
UNION ALL
SELECT 'account_book_transactions_decimal_contract',
       'amount/original/purchase/book=decimal(28,8); rate=decimal(38,18)',
       GROUP_CONCAT(CONCAT(COLUMN_NAME, '=', COLUMN_TYPE) ORDER BY COLUMN_NAME SEPARATOR ', '),
       IF(SUM(CASE
           WHEN COLUMN_NAME IN ('amount','original_amount','purchase_total','book_amount')
                AND COLUMN_TYPE = 'decimal(28,8)' THEN 1
           WHEN COLUMN_NAME = 'exchange_rate' AND COLUMN_TYPE = 'decimal(38,18)' THEN 1
           ELSE 0 END) = 5, 'PASS', 'FAIL')
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'account_book_transactions'
  AND COLUMN_NAME IN ('amount','original_amount','purchase_total','book_amount','exchange_rate')
UNION ALL
SELECT 'exchange_rate_daily_contract',
       'rate decimal(38,18) plus source/target/requested/provider',
       CONCAT(COUNT(*), ' columns'),
       IF(COUNT(*) = 5 AND SUM(CASE WHEN COLUMN_NAME = 'rate' AND COLUMN_TYPE = 'decimal(38,18)' THEN 1 ELSE 0 END) = 1,
          'PASS', 'FAIL')
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'exchange_rate'
  AND COLUMN_NAME IN ('source_currency_code','target_currency_code','requested_rate_date','provider','rate')
UNION ALL
SELECT 'legacy_exchange_rate_foreign_keys_removed', '0 legacy columns',
       CAST(COUNT(*) AS CHAR), IF(COUNT(*) = 0, 'PASS', 'FAIL')
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'exchange_rate'
  AND COLUMN_NAME IN ('base_currency_id','target_currency_id')
UNION ALL
SELECT 'receipt_batch_registration_table', 'table exists',
       CAST(COUNT(*) AS CHAR), IF(COUNT(*) = 1, 'PASS', 'FAIL')
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'receipt_batch_registration'
UNION ALL
SELECT 'receipt_batch_idempotency_unique',
       'account_book_id,user_id,idempotency_key unique in that order',
       COALESCE(GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ','), 'missing'),
       IF(GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') = 'account_book_id,user_id,idempotency_key'
          AND MIN(NON_UNIQUE) = 0, 'PASS', 'FAIL')
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'receipt_batch_registration'
  AND INDEX_NAME = 'uk_receipt_batch_idempotency'
UNION ALL
SELECT 'exchange_rate_daily_unique',
       'source_currency_code,target_currency_code,requested_rate_date,provider unique',
       COALESCE(GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ','), 'missing'),
       IF(GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') =
          'source_currency_code,target_currency_code,requested_rate_date,provider'
          AND MIN(NON_UNIQUE) = 0, 'PASS', 'FAIL')
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'exchange_rate'
  AND INDEX_NAME = 'uk_exchange_rate_daily'
UNION ALL
SELECT 'merchant_aggregation_index', 'account_book_id,merchant_key index',
       COALESCE(GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ','), 'missing'),
       IF(GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') = 'account_book_id,merchant_key',
          'PASS', 'FAIL')
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'account_book_transactions'
  AND INDEX_NAME = 'ix_account_book_transactions_merchant_key';
