# Global receipt analysis and currency conversion

One uploaded image produces an array of independent receipt candidates. The AI identifies
the original currency, decimal total, date, language and category; it never supplies FX.
The backend validates each item and attaches an authoritative source-to-account-book quote.
The default mode is VISION_FIRST. Existing explicit OCR_ONLY, OCR_WITH_AI and VISION_ONLY
modes remain supported.

## API contracts

All routes below use the existing authenticated response wrapper and account-book access check.

- POST /api/v1/account-books/{id}/transactions/receipt-analysis: multipart file and optional
  analysisMode. The response has receipts, receiptCount, warnings, ocrEngine and usedAi.
- POST /api/v1/account-books/{id}/transactions/receipt-conversion: originalAmount,
  originalCurrencyCode, transactionDate. Missing source fields return NEEDS_REVIEW.
  Recalculation does not require a title or category.
- POST /api/v1/account-books/{id}/transactions/receipt-batch: receipts array (1–30), each with
  receiptId, sourceImageId, analysisRevision, title, optional storeName/branchName,
  categoryName, purchaseTotal, paymentBreakdown, optional cashTendered/change, bookAmount,
  originalCurrencyCode, transactionDate/time, optional memo and the server-issued
  conversionQuoteId that the user reviewed.
  The request requires an Idempotency-Key header (8–100 characters from A-Z, a-z, 0-9,
  period, underscore, colon or hyphen). Duplicate identifiers within the batch are rejected.
  The response is an array of saved transaction DTOs. Replaying the same key and canonical
  payload returns the original response; reusing it for another payload returns HTTP 409.

All monetary response fields (receipts, transactions, fixed costs, goals, book/summary totals
and chart amounts) are serialized as decimal strings. Percentages and counts remain numeric.
Existing manual create/update routes still accept decimal
JSON numbers or strings. Batch registration accepts source facts only; any client-supplied
convertedAmount or exchangeRate has no effect. Transaction date is required to register.

Candidate conversion fields are originalCurrencyCode, accountBookCurrencyCode,
convertedAmount, exchangeRate, requestedRateDate, effectiveRateDate, exchangeRateProvider,
rateFetchedAt, convertedAt, roundingPrecision, roundingMode, conversionPolicyVersion,
conversionQuoteId, conversionStatus and rateDateFallback. The SHA-256 quote binds the account
book, source facts, exact rate observation and rounding policy. Registration recalculates on
the server and atomically rejects a changed or cross-account-book quote. Status values are NOT_REQUIRED, CONVERTED,
RATE_UNAVAILABLE and NEEDS_REVIEW. Incomplete/unreadable candidates remain visible while
other candidates succeed. Missing dates are never replaced with an invented historical date.

## Currency authority and historical policy

ExchangeRateProvider separates the provider from domain calculations. The first implementation
uses Frankfurter v2, an API with historical rates and no public API key requirement.
The implementation calls the scalar `/v2/rate/{base}/{quote}?date={date}` endpoint and checks
the returned base, quote and effective date. This avoids downloading a historical range and
still accepts the provider's most recent published observation within the configured lookback.
Reference: https://frankfurter.dev/ (checked 2026-09-21).

Defaults are configurable with exchange-rate.provider, exchange-rate.frankfurter.base-url,
exchange-rate.connect-timeout-ms (3000), exchange-rate.read-timeout-ms (5000), and
exchange-rate.max-lookback-days (31). Providers return target units per ONE source unit;
conversion is exact BigDecimal multiplication, then HALF_UP to Currency.decimalPlaces.
The existing Resilience4j RetryRegistry supplies a separate receiptExchangeRate policy:
two attempts with 250 ms between attempts. Only transport errors, HTTP 429 and HTTP 5xx
retry. Unsupported currency/404, malformed responses and missing historical quotes do not.
Timeouts apply per attempt; repeated transient failure still returns RATE_UNAVAILABLE.
Same-currency conversion uses rate 1 and no external request.

No quote in the configured window, an invalid response, or provider failure yields
RATE_UNAVAILABLE. No current quote, generated FX, or silent 1:1 replacement is used.
The default Frankfurter feed is blended reference data, not card-issuer settlement rates.
No live external provider call is required by the test suite.

## Cache and persistence

The existing exchange_rate table is reused with source/target ISO codes instead of Currency
foreign keys. Consequently a THB receipt works even when only JPY exists in the account-book
currency master. Codes are checked against the Java ISO catalog plus XCG/ZWG/XAD additions
newer than the deployed Java 21 catalog. Provider coverage is independently validated.

The cache key is source code + target code + requested date + provider. The row keeps the
effective publication date and UTC fetch instant separately. Repeated weekend lookups reuse
the same result until `exchange-rate.cache-refresh-after-seconds` (default 86400) expires;
stale rows refresh in place so the daily unique key remains stable.
Bounded local locks coalesce simultaneous requests, while the database unique key handles
multiple processes. Cache transactions use REQUIRES_NEW and READ_COMMITTED: duplicate
inserts roll back independently and reload the committed winner without poisoning a receipt
batch. Daily cache records may survive a failed business batch; transaction/category writes
in that batch all roll back.

AccountBookTransaction keeps nullable originalAmount, originalCurrencyCode,
targetCurrencyCode, exchangeRate, requestedRateDate, effectiveRateDate,
exchangeRateProvider, rateFetchedAt, convertedAt, roundingPrecision, roundingMode and
conversionPolicyVersion. Its existing amount is the account-book amount. List QueryDSL
projections return these fields. Manual and fixed-cost transactions keep null conversion
metadata. Receipt metadata edits preserve provenance;
changing recorded amount/date/type through the manual update API is rejected.

Receipt transactions also preserve purchaseTotal, paymentBreakdown, cashTendered,
change, bookAmount, amount policy/reason/review status, branch, source image/revision,
and printed transaction time. The backend recomputes bookAmount from the source facts;
it does not trust a client total. Loyalty redemption is excluded, cash tendered minus
change is counted once, and duplicate payment groups are collapsed. A gross card summary
that conflicts with a separately printed point redemption is replaced by the uniquely
reconcilable net settlement and recorded with a warning.

The store chart groups by a normalized merchant key derived from branch-free storeName;
title and branch remain display/provenance fields. Manual store edits recompute the key.
Legacy rows fall back to their stored storeName and are not bulk rewritten.

Transaction, fixed-cost and monthly-goal monetary columns are DECIMAL(28,8), matching the
existing currency master's supported 0–8 decimal places. Rates use DECIMAL(38,18).
All three monetary entities normalize with Currency.decimalPlaces. Summary and chart
aggregation retain BigDecimal arithmetic and widened stored values.

Run scripts/sql/20260919_receipt_currency_precision.sql once against an existing MySQL 8
database with the application stopped and a backup available. Its preflight accepts only the
known legacy schema and raises SQLSTATE 45000 before DDL for an already-applied, new or partial
schema. MySQL DDL auto-commits, so a failure after the preflight still requires restoring the
backup rather than rerunning blindly. The script backfills original cache codes/dates and
removes installation-specific old FK constraints. This repository uses operator-run SQL;
Hibernate schema update does not replace this data migration.
Then run scripts/sql/20260921_receipt_payment_semantics.sql once while the application
is still stopped. Its preflight requires the first migration and rejects current or
partial schemas before DDL. It adds payment/provenance and merchant-key columns, backfills
only facts supported by existing originalAmount/storeName, and creates the merchant-key
index. Verify the exact column set, decimal types, row counts, exchange-rate unique key,
and application login/read path before starting traffic. Recovery for either migration is
restoring the pre-migration backup; do not attempt a partial down migration after new writes.
The migration has not been applied to a production database by this task.

## Categories, admin settings and diagnostics

The backend supplies active category names from the current account book. Analysis accepts
only exact candidate matches; an unknown category becomes null with a review warning.
Users can still enter a category directly, and the existing find-or-create behavior runs
inside the batch transaction.

Currency-to-OCR admin mappings remain editable legacy data but are dormant during automatic
global analysis. The backend no longer sends target currency or derives OCR language from
it. Only globally scoped (currencyCode null) keyword hints are passed, across their languages;
the AI treats them as advisory rather than truncating OCR text. Vision detects source
language from the image; OCR fallback language is an AI-server configuration concern.

Production receipt/FX logs contain account-book ID, mode, counts, currencies, dates, provider,
cache outcome and latency. No image or OCR text is included in the receipt response or logs.

## Verification and limits

Focused tests exercise identity conversion, direction, historical lookup, prior publication,
cache hit/miss/refresh, provider failure, target precision, absent source master, newer ISO
codes, mixed receipt results, malformed amount/date/category, decimal serialization, batch
rollback, provenance round trip/query projection, manual/fixed-cost/goal regression, stale and
cross-book quote rejection, committed-response replay, changed-payload conflict, and concurrent
unique insertion recovery. HTTP provider tests use a local fake server; application integration
tests use H2 in MySQL compatibility mode. The 2026-09-21 quality evidence also records isolated
MySQL 8.4.11 migration, precision and concurrent unique-key checks.

Limits: a maximum of 30 candidates per batch; a configurable 31-day published-rate lookback;
idempotency records are retained with the business data and currently have no automated expiry;
no dedicated financial correction API for an already registered receipt. MySQL production
migration execution is an operator deployment step.
