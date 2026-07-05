-- SQL Script to Clean Up Duplicate Forecast Transactions
-- This script removes duplicate forecast transactions that have the same:
--   - ForecastItem_idForecastItem
--   - plannedDate
--   - remainingAmount
--   - the exact same set of linked transaction splits (the "split signature")
-- It keeps only the most recently updated version of each duplicate.
--
-- NOTE: Forecast transactions linked to DIFFERENT splits, or that differ in remaining amount, are
-- NOT duplicates - they represent different actual entries that share the same forecast item and
-- date.  A split's identity is the composite of (Transaction_Split_idTransaction,
-- Transaction_Split_idBudgetItem), so the split signature below incorporates both.  Two forecast
-- transactions are only duplicates when their forecast item, planned date, remaining amount, and
-- split signature all match (which includes the case where neither is linked to any split, i.e. a
-- NULL signature).

-- BACKUP YOUR DATABASE BEFORE RUNNING THIS SCRIPT!

-- Step 1: Identify duplicates (for review only - does not modify data)
-- A forecast transaction's split signature is a deterministic, ordered concatenation of the
-- composite ids of every split linked to it (NULL when it is linked to no split).
SELECT
    forecast_item_id,
    plannedDate,
    remainingAmount,
    split_signature,
    COUNT(*) as duplicate_count,
    GROUP_CONCAT(transaction_id ORDER BY updatedTimeStamp DESC SEPARATOR ', ') as transaction_ids,
    GROUP_CONCAT(updatedTimeStamp ORDER BY updatedTimeStamp DESC SEPARATOR ', ') as timestamps
FROM (
    SELECT
        BIN_TO_UUID(ft.ForecastItem_idForecastItem) as forecast_item_id,
        ft.plannedDate as plannedDate,
        ft.remainingAmount as remainingAmount,
        ft.updatedTimeStamp as updatedTimeStamp,
        BIN_TO_UUID(ft.idForecastTransaction) as transaction_id,
        GROUP_CONCAT(DISTINCT CONCAT_WS(':',
            BIN_TO_UUID(fts.Transaction_Split_idTransaction),
            BIN_TO_UUID(fts.Transaction_Split_idBudgetItem))
            ORDER BY BIN_TO_UUID(fts.Transaction_Split_idTransaction),
                     BIN_TO_UUID(fts.Transaction_Split_idBudgetItem) SEPARATOR '|') as split_signature
    FROM forecast_transaction ft
    LEFT JOIN forecast_transaction_split fts
        ON ft.idForecastTransaction = fts.ForecastTransaction_idForecastTransaction
    GROUP BY ft.idForecastTransaction
) per_ft
GROUP BY forecast_item_id, plannedDate, remainingAmount, split_signature
HAVING COUNT(*) > 1
ORDER BY plannedDate DESC;

-- Step 2: Delete duplicates, keeping only the most recent (by updatedTimeStamp)
-- Only delete when the two forecast transactions share the same amount and split signature.
DELETE ft1
FROM forecast_transaction ft1
INNER JOIN forecast_transaction ft2
    ON ft1.ForecastItem_idForecastItem = ft2.ForecastItem_idForecastItem
    AND ft1.plannedDate = ft2.plannedDate
    AND ft1.remainingAmount = ft2.remainingAmount
    AND ft1.updatedTimeStamp < ft2.updatedTimeStamp
LEFT JOIN (
    SELECT ForecastTransaction_idForecastTransaction as ftId,
           GROUP_CONCAT(DISTINCT CONCAT_WS(':',
               BIN_TO_UUID(Transaction_Split_idTransaction),
               BIN_TO_UUID(Transaction_Split_idBudgetItem))
               ORDER BY BIN_TO_UUID(Transaction_Split_idTransaction),
                        BIN_TO_UUID(Transaction_Split_idBudgetItem) SEPARATOR '|') as sig
    FROM forecast_transaction_split
    GROUP BY ForecastTransaction_idForecastTransaction
) s1 ON s1.ftId = ft1.idForecastTransaction
LEFT JOIN (
    SELECT ForecastTransaction_idForecastTransaction as ftId,
           GROUP_CONCAT(DISTINCT CONCAT_WS(':',
               BIN_TO_UUID(Transaction_Split_idTransaction),
               BIN_TO_UUID(Transaction_Split_idBudgetItem))
               ORDER BY BIN_TO_UUID(Transaction_Split_idTransaction),
                        BIN_TO_UUID(Transaction_Split_idBudgetItem) SEPARATOR '|') as sig
    FROM forecast_transaction_split
    GROUP BY ForecastTransaction_idForecastTransaction
) s2 ON s2.ftId = ft2.idForecastTransaction
WHERE ft1.overridden = FALSE
  AND ft2.overridden = FALSE
  AND ((s1.sig IS NULL AND s2.sig IS NULL) OR s1.sig = s2.sig);

-- Step 3: For any remaining duplicates with the same timestamp, keep the one with the lower ID
-- Only delete when the two forecast transactions share the same amount and split signature.
DELETE ft1
FROM forecast_transaction ft1
INNER JOIN forecast_transaction ft2
    ON ft1.ForecastItem_idForecastItem = ft2.ForecastItem_idForecastItem
    AND ft1.plannedDate = ft2.plannedDate
    AND ft1.remainingAmount = ft2.remainingAmount
    AND ft1.updatedTimeStamp = ft2.updatedTimeStamp
    AND ft1.idForecastTransaction > ft2.idForecastTransaction
LEFT JOIN (
    SELECT ForecastTransaction_idForecastTransaction as ftId,
           GROUP_CONCAT(DISTINCT CONCAT_WS(':',
               BIN_TO_UUID(Transaction_Split_idTransaction),
               BIN_TO_UUID(Transaction_Split_idBudgetItem))
               ORDER BY BIN_TO_UUID(Transaction_Split_idTransaction),
                        BIN_TO_UUID(Transaction_Split_idBudgetItem) SEPARATOR '|') as sig
    FROM forecast_transaction_split
    GROUP BY ForecastTransaction_idForecastTransaction
) s1 ON s1.ftId = ft1.idForecastTransaction
LEFT JOIN (
    SELECT ForecastTransaction_idForecastTransaction as ftId,
           GROUP_CONCAT(DISTINCT CONCAT_WS(':',
               BIN_TO_UUID(Transaction_Split_idTransaction),
               BIN_TO_UUID(Transaction_Split_idBudgetItem))
               ORDER BY BIN_TO_UUID(Transaction_Split_idTransaction),
                        BIN_TO_UUID(Transaction_Split_idBudgetItem) SEPARATOR '|') as sig
    FROM forecast_transaction_split
    GROUP BY ForecastTransaction_idForecastTransaction
) s2 ON s2.ftId = ft2.idForecastTransaction
WHERE ft1.overridden = FALSE
  AND ft2.overridden = FALSE
  AND ((s1.sig IS NULL AND s2.sig IS NULL) OR s1.sig = s2.sig);

-- Step 4: Verify no duplicates remain (should return 0 rows)
SELECT
    forecast_item_id,
    plannedDate,
    remainingAmount,
    split_signature,
    COUNT(*) as duplicate_count
FROM (
    SELECT
        BIN_TO_UUID(ft.ForecastItem_idForecastItem) as forecast_item_id,
        ft.plannedDate as plannedDate,
        ft.remainingAmount as remainingAmount,
        GROUP_CONCAT(DISTINCT CONCAT_WS(':',
            BIN_TO_UUID(fts.Transaction_Split_idTransaction),
            BIN_TO_UUID(fts.Transaction_Split_idBudgetItem))
            ORDER BY BIN_TO_UUID(fts.Transaction_Split_idTransaction),
                     BIN_TO_UUID(fts.Transaction_Split_idBudgetItem) SEPARATOR '|') as split_signature
    FROM forecast_transaction ft
    LEFT JOIN forecast_transaction_split fts
        ON ft.idForecastTransaction = fts.ForecastTransaction_idForecastTransaction
    GROUP BY ft.idForecastTransaction
) per_ft
GROUP BY forecast_item_id, plannedDate, remainingAmount, split_signature
HAVING COUNT(*) > 1;

-- NOTE: A UNIQUE constraint on (ForecastItem_idForecastItem, plannedDate) is intentionally NOT added,
-- because it is valid to have multiple forecast transactions for the same forecast item on the same
-- date when they are linked to different transaction splits or have different amounts.

