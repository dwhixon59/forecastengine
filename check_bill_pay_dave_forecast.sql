-- Check if forecast transactions exist for Bill Pay Dave
-- This will help us understand if the forecast transactions were deleted or if they're just not being retrieved

USE forecastengine;

-- First, find the Bill Pay Dave register
SELECT '=== BILL PAY DAVE REGISTER ===' as '';
SELECT bin_to_uuid(idRegister) as register_id, name, bin_to_uuid(Budget_idBudget) as budget_id
FROM register
WHERE name LIKE '%Bill Pay Dave%';

-- Find the budget for Bill Pay Dave
SELECT '=== BUDGET FOR BILL PAY DAVE ===' as '';
SELECT bin_to_uuid(b.idBudget) as budget_id, b.name as budget_name
FROM budget b
JOIN register r ON b.idBudget = r.Budget_idBudget
WHERE r.name LIKE '%Bill Pay Dave%';

-- Find forecasts for that budget
SELECT '=== FORECASTS FOR BILL PAY DAVE BUDGET ===' as '';
SELECT bin_to_uuid(f.idForecast) as forecast_id, f.forecastName, f.startDate, f.numberOfMonths
FROM forecast f
JOIN budget b ON f.Budget_idBudget = b.idBudget
JOIN register r ON b.idBudget = r.Budget_idBudget
WHERE r.name LIKE '%Bill Pay Dave%';

-- Count forecast transactions for Bill Pay Dave's forecast
SELECT '=== FORECAST TRANSACTION COUNT ===' as '';
SELECT
    COUNT(*) as total_forecast_transactions,
    COUNT(CASE WHEN ft.remainingAmount > 0 THEN 1 END) as credits,
    COUNT(CASE WHEN ft.remainingAmount < 0 THEN 1 END) as debits,
    COUNT(CASE WHEN ft.remainingAmount = 0 THEN 1 END) as zero_amount,
    MIN(ft.plannedDate) as earliest_date,
    MAX(ft.plannedDate) as latest_date
FROM forecast_transaction ft
JOIN forecast_item fi ON ft.ForecastItem_idForecastItem = fi.idForecastItem
JOIN forecast f ON fi.Forecast_idForecast = f.idForecast
JOIN budget b ON f.Budget_idBudget = b.idBudget
JOIN register r ON b.idBudget = r.Budget_idBudget
WHERE r.name LIKE '%Bill Pay Dave%';

-- Show some sample forecast transactions
SELECT '=== SAMPLE FORECAST TRANSACTIONS FOR BILL PAY DAVE ===' as '';
SELECT
    bin_to_uuid(ft.idForecastTransaction) as transaction_id,
    ft.plannedDate,
    ft.budgetedAmount,
    ft.remainingAmount,
    bi.payee,
    bi.memo
FROM forecast_transaction ft
JOIN forecast_item fi ON ft.ForecastItem_idForecastItem = fi.idForecastItem
JOIN forecast f ON fi.Forecast_idForecast = f.idForecast
JOIN budget b ON f.Budget_idBudget = b.idBudget
JOIN register r ON b.idBudget = r.Budget_idBudget
JOIN budget_item bi ON fi.BudgetItem_idBudgetItem = bi.idBudgetItem
WHERE r.name LIKE '%Bill Pay Dave%'
ORDER BY ft.plannedDate
LIMIT 20;
