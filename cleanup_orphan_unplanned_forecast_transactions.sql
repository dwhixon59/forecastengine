-- SQL Script to Clean Up Orphan Unplanned / On-Demand Forecast Transactions
-- =========================================================================
-- An "orphan" is a forecast transaction that:
--   1. belongs to a forecast item that is UNPLANNED (howOccurs = 'U') OR has an
--      ON_DEMAND period (period = 'On-Demand'),  AND
--   2. has a zero remaining amount (to the cent),  AND
--   3. is not linked to any forecast_transaction_split.
--
-- Unplanned / on-demand forecast transactions only belong in the forecast while they are
-- reconciling an actual transaction split.  Once the underlying (usually provisional)
-- transaction is deleted, its split link is removed by the cascade but the forecast
-- transaction is left behind with a zero remaining amount and no linked split.  These
-- serve no purpose and accumulate over time (they also show up as "duplicates" because
-- several identical zero-amount, no-split rows share the same item/date/amount signature).
--
-- The daily update now offers to delete these automatically (and removes them at the moment
-- a provisional transaction is invalidated).  This script cleans up any historical backlog.
--
-- BACKUP YOUR DATABASE BEFORE RUNNING THIS SCRIPT!

-- Step 1: Identify orphans (for review only - does not modify data)
SELECT
    BIN_TO_UUID(ft.idForecastTransaction) AS transaction_id,
    fi.category,
    fi.payee,
    ft.plannedDate,
    fi.period,
    fi.howOccurs,
    ft.remainingAmount
FROM forecast_transaction ft
INNER JOIN forecast_item fi
    ON ft.ForecastItem_idForecastItem = fi.idForecastItem
LEFT JOIN forecast_transaction_split fts
    ON ft.idForecastTransaction = fts.ForecastTransaction_idForecastTransaction
WHERE (fi.howOccurs = 'U' OR fi.period = 'On-Demand')
  AND ROUND(ft.remainingAmount, 2) = 0.00
  AND fts.ForecastTransaction_idForecastTransaction IS NULL
ORDER BY ft.plannedDate DESC, fi.category, fi.payee;

-- Step 2: Delete the orphans identified above.
DELETE ft
FROM forecast_transaction ft
INNER JOIN forecast_item fi
    ON ft.ForecastItem_idForecastItem = fi.idForecastItem
LEFT JOIN forecast_transaction_split fts
    ON ft.idForecastTransaction = fts.ForecastTransaction_idForecastTransaction
WHERE (fi.howOccurs = 'U' OR fi.period = 'On-Demand')
  AND ROUND(ft.remainingAmount, 2) = 0.00
  AND fts.ForecastTransaction_idForecastTransaction IS NULL;

-- Step 3: Verify no orphans remain (should return 0 rows).
SELECT
    BIN_TO_UUID(ft.idForecastTransaction) AS transaction_id,
    fi.category,
    fi.payee,
    ft.plannedDate
FROM forecast_transaction ft
INNER JOIN forecast_item fi
    ON ft.ForecastItem_idForecastItem = fi.idForecastItem
LEFT JOIN forecast_transaction_split fts
    ON ft.idForecastTransaction = fts.ForecastTransaction_idForecastTransaction
WHERE (fi.howOccurs = 'U' OR fi.period = 'On-Demand')
  AND ROUND(ft.remainingAmount, 2) = 0.00
  AND fts.ForecastTransaction_idForecastTransaction IS NULL;

