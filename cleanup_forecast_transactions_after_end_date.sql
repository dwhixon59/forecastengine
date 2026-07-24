all-- SQL Script to Clean Up Forecast Transactions Planned After the Budget Item End Date
-- ====================================================================================
-- These are forecast transactions that:
--   1. belong to a budget item that HAS an end date (bi.endDate IS NOT NULL),  AND
--   2. have a planned date strictly AFTER that end date (ft.plannedDate > bi.endDate),  AND
--   3. are not linked to any forecast_transaction_split.
--
-- A budget item with an end date should have no forecast activity beyond that date.  When an item
-- is given an end date after its forecast was generated (or the forecast otherwise projects past the
-- end date), stale forecast transactions are left behind.  They cause expired items to keep showing a
-- future planned date instead of "Expired." in item lists.
--
-- Condition (3) protects real, reconciled activity: a forecast transaction that is linked to an actual
-- transaction split (e.g. a real payment that posted after the end date) is never deleted.
--
-- The daily update now offers to delete these automatically.  This script cleans up any historical
-- backlog.
--
-- BACKUP YOUR DATABASE BEFORE RUNNING THIS SCRIPT!

-- Step 1: Identify after-end-date transactions (for review only - does not modify data)
SELECT
    BIN_TO_UUID(ft.idForecastTransaction) AS transaction_id,
    fi.category,
    fi.payee,
    ft.plannedDate,
    bi.endDate,
    ft.remainingAmount
FROM forecast_transaction ft
INNER JOIN forecast_item fi
    ON ft.ForecastItem_idForecastItem = fi.idForecastItem
INNER JOIN budget_item bi
    ON fi.BudgetItem_idBudgetItem = bi.idBudgetItem
LEFT JOIN forecast_transaction_split fts
    ON ft.idForecastTransaction = fts.ForecastTransaction_idForecastTransaction
WHERE bi.endDate IS NOT NULL
  AND ft.plannedDate > bi.endDate
  AND fts.ForecastTransaction_idForecastTransaction IS NULL
ORDER BY ft.plannedDate DESC, fi.category, fi.payee;

-- Step 2: Delete the after-end-date transactions identified above.
DELETE ft
FROM forecast_transaction ft
INNER JOIN forecast_item fi
    ON ft.ForecastItem_idForecastItem = fi.idForecastItem
INNER JOIN budget_item bi
    ON fi.BudgetItem_idBudgetItem = bi.idBudgetItem
LEFT JOIN forecast_transaction_split fts
    ON ft.idForecastTransaction = fts.ForecastTransaction_idForecastTransaction
WHERE bi.endDate IS NOT NULL
  AND ft.plannedDate > bi.endDate
  AND fts.ForecastTransaction_idForecastTransaction IS NULL;

-- Step 3: Verify none remain (should return 0 rows).
SELECT
    BIN_TO_UUID(ft.idForecastTransaction) AS transaction_id,
    fi.category,
    fi.payee,
    ft.plannedDate,
    bi.endDate
FROM forecast_transaction ft
INNER JOIN forecast_item fi
    ON ft.ForecastItem_idForecastItem = fi.idForecastItem
INNER JOIN budget_item bi
    ON fi.BudgetItem_idBudgetItem = bi.idBudgetItem
LEFT JOIN forecast_transaction_split fts
    ON ft.idForecastTransaction = fts.ForecastTransaction_idForecastTransaction
WHERE bi.endDate IS NOT NULL
  AND ft.plannedDate > bi.endDate
  AND fts.ForecastTransaction_idForecastTransaction IS NULL;

