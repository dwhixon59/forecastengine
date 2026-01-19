C-- SQL Script to Clean Up Duplicate Forecast Transactions
-- This script removes duplicate forecast transactions that have the same:
--   - ForecastItem_idForecastItem
--   - plannedDate
-- It keeps only the most recently updated version of each duplicate.
--
-- NOTE: Forecast transactions linked to different splits are NOT duplicates - they represent
-- different actual transactions that matched the same forecast item on the same date.
-- These are EXCLUDED from the cleanup.

-- BACKUP YOUR DATABASE BEFORE RUNNING THIS SCRIPT!

-- Step 1: Identify duplicates (for review only - does not modify data)
-- Excludes cases where multiple transactions have different splits (which are valid)
SELECT
    BIN_TO_UUID(ft.ForecastItem_idForecastItem) as forecast_item_id,
    ft.plannedDate,
    COUNT(*) as duplicate_count,
    COUNT(DISTINCT fts.Transaction_Split_idTransaction) as distinct_transaction_count,
    GROUP_CONCAT(BIN_TO_UUID(ft.idForecastTransaction) SEPARATOR ', ') as transaction_ids,
    GROUP_CONCAT(ft.updatedTimeStamp SEPARATOR ', ') as timestamps
FROM
    forecast_transaction ft
    LEFT JOIN forecast_transaction_split fts ON ft.idForecastTransaction = fts.ForecastTransaction_idForecastTransaction
GROUP BY
    ft.ForecastItem_idForecastItem,
    ft.plannedDate
HAVING
    COUNT(*) > 1
    AND (COUNT(DISTINCT fts.Transaction_Split_idTransaction) <= 1 OR COUNT(DISTINCT fts.Transaction_Split_idTransaction) IS NULL)
ORDER BY
    ft.plannedDate DESC;

-- Step 2: Delete duplicates, keeping only the most recent (by updatedTimeStamp)
-- Only delete if they don't have different splits
DELETE ft1
FROM forecast_transaction ft1
INNER JOIN forecast_transaction ft2
    ON ft1.ForecastItem_idForecastItem = ft2.ForecastItem_idForecastItem
    AND ft1.plannedDate = ft2.plannedDate
    AND ft1.updatedTimeStamp < ft2.updatedTimeStamp
LEFT JOIN forecast_transaction_split fts1 ON ft1.idForecastTransaction = fts1.ForecastTransaction_idForecastTransaction
LEFT JOIN forecast_transaction_split fts2 ON ft2.idForecastTransaction = fts2.ForecastTransaction_idForecastTransaction
WHERE ft1.overridden = FALSE
  AND ft2.overridden = FALSE
  AND (fts1.Transaction_Split_idTransaction IS NULL
       OR fts2.Transaction_Split_idTransaction IS NULL
       OR fts1.Transaction_Split_idTransaction = fts2.Transaction_Split_idTransaction);

-- Step 3: For any remaining duplicates with the same timestamp, keep the one with the lower ID
-- Only delete if they don't have different splits
DELETE ft1
FROM forecast_transaction ft1
INNER JOIN forecast_transaction ft2
    ON ft1.ForecastItem_idForecastItem = ft2.ForecastItem_idForecastItem
    AND ft1.plannedDate = ft2.plannedDate
    AND ft1.updatedTimeStamp = ft2.updatedTimeStamp
    AND ft1.idForecastTransaction > ft2.idForecastTransaction
LEFT JOIN forecast_transaction_split fts1 ON ft1.idForecastTransaction = fts1.ForecastTransaction_idForecastTransaction
LEFT JOIN forecast_transaction_split fts2 ON ft2.idForecastTransaction = fts2.ForecastTransaction_idForecastTransaction
WHERE ft1.overridden = FALSE
  AND ft2.overridden = FALSE
  AND (fts1.Transaction_Split_idTransaction IS NULL
       OR fts2.Transaction_Split_idTransaction IS NULL
       OR fts1.Transaction_Split_idTransaction = fts2.Transaction_Split_idTransaction);

-- Step 4: Verify no duplicates remain (should return 0 rows)
-- Excludes valid cases where transactions have different splits
SELECT
    BIN_TO_UUID(ft.ForecastItem_idForecastItem) as forecast_item_id,
    ft.plannedDate,
    COUNT(*) as duplicate_count,
    COUNT(DISTINCT fts.Transaction_Split_idTransaction) as distinct_transaction_count
FROM
    forecast_transaction ft
    LEFT JOIN forecast_transaction_split fts ON ft.idForecastTransaction = fts.ForecastTransaction_idForecastTransaction
GROUP BY
    ft.ForecastItem_idForecastItem,
    ft.plannedDate
HAVING
    COUNT(*) > 1
    AND (COUNT(DISTINCT fts.Transaction_Split_idTransaction) <= 1 OR COUNT(DISTINCT fts.Transaction_Split_idTransaction) IS NULL);

-- Step 5: Add UNIQUE constraint to prevent future duplicates
-- Note: This constraint will prevent multiple forecast transactions with the same item+date,
-- but the application must handle the valid case of multiple actual transactions matching
-- the same forecast item on the same date by using splits properly.
ALTER TABLE forecast_transaction
ADD UNIQUE KEY uk_forecast_item_date (ForecastItem_idForecastItem, plannedDate);

-- Verify the constraint was added
SHOW KEYS FROM forecast_transaction WHERE Key_name = 'uk_forecast_item_date';

