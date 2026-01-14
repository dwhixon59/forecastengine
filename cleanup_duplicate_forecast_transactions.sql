-- SQL Script to Clean Up Duplicate Forecast Transactions
-- This script removes duplicate forecast transactions that have the same:
--   - ForecastItem_idForecastItem
--   - plannedDate
-- It keeps only the most recently updated version of each duplicate.

-- BACKUP YOUR DATABASE BEFORE RUNNING THIS SCRIPT!

-- Step 1: Identify duplicates (for review only - does not modify data)
SELECT
    BIN_TO_UUID(ForecastItem_idForecastItem) as forecast_item_id,
    plannedDate,
    COUNT(*) as duplicate_count,
    GROUP_CONCAT(BIN_TO_UUID(idForecastTransaction) SEPARATOR ', ') as transaction_ids,
    GROUP_CONCAT(updatedTimeStamp SEPARATOR ', ') as timestamps
FROM
    forecast_transaction
GROUP BY
    ForecastItem_idForecastItem,
    plannedDate
HAVING
    COUNT(*) > 1
ORDER BY
    plannedDate DESC;

-- Step 2: Delete duplicates, keeping only the most recent (by updatedTimeStamp)
-- This uses a self-join to find and delete older duplicates
DELETE ft1
FROM forecast_transaction ft1
INNER JOIN forecast_transaction ft2
    ON ft1.ForecastItem_idForecastItem = ft2.ForecastItem_idForecastItem
    AND ft1.plannedDate = ft2.plannedDate
    AND ft1.updatedTimeStamp < ft2.updatedTimeStamp
WHERE ft1.overridden = FALSE
  AND ft2.overridden = FALSE;

-- Step 3: For any remaining duplicates with the same timestamp, keep the one with the lower ID
DELETE ft1
FROM forecast_transaction ft1
INNER JOIN forecast_transaction ft2
    ON ft1.ForecastItem_idForecastItem = ft2.ForecastItem_idForecastItem
    AND ft1.plannedDate = ft2.plannedDate
    AND ft1.updatedTimeStamp = ft2.updatedTimeStamp
    AND ft1.idForecastTransaction > ft2.idForecastTransaction
WHERE ft1.overridden = FALSE
  AND ft2.overridden = FALSE;

-- Step 4: Verify no duplicates remain (should return 0 rows)
SELECT
    BIN_TO_UUID(ForecastItem_idForecastItem) as forecast_item_id,
    plannedDate,
    COUNT(*) as duplicate_count
FROM
    forecast_transaction
GROUP BY
    ForecastItem_idForecastItem,
    plannedDate
HAVING
    COUNT(*) > 1;

-- Step 5: Add UNIQUE constraint to prevent future duplicates
-- This will ensure that no two forecast transactions can have the same
-- ForecastItem_idForecastItem and plannedDate combination
ALTER TABLE forecast_transaction
ADD UNIQUE KEY uk_forecast_item_date (ForecastItem_idForecastItem, plannedDate);

-- Verify the constraint was added
SHOW KEYS FROM forecast_transaction WHERE Key_name = 'uk_forecast_item_date';

