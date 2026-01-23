-- SQL Script to Fix Category Name Typo
-- Fixes "pending Money" to "Spending Money"
-- Date: January 16, 2026

-- First, let's see if there are any budget items with the incorrect category
SELECT
    BIN_TO_UUID(idBudgetItem) as budgetItemId,
    category,
    payee,
    amount
FROM budget_item
WHERE category = 'pending Money';

-- Update the category name from "pending Money" to "Spending Money"
UPDATE budget_item
SET category = 'Spending Money'
WHERE category = 'pending Money';

-- Verify the fix
SELECT
    BIN_TO_UUID(idBudgetItem) as budgetItemId,
    category,
    payee,
    amount
FROM budget_item
WHERE category IN ('pending Money', 'Spending Money')
ORDER BY category, payee;

-- Check if there are any other typos in category names
SELECT DISTINCT category
FROM budget_item
ORDER BY category;

