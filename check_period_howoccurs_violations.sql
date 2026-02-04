-- Check for invalid Period/HowOccurs combinations in budget_item table
-- Run this to identify any existing data that violates the consistency rules
-- Date: 2026-02-04

-- Check Rule 1: UNPLANNED items must have ON_DEMAND period
SELECT
    'VIOLATION: UNPLANNED with scheduled period' as violation_type,
    bin_to_uuid(idBudgetItem) as budget_item_id,
    payee,
    category,
    period,
    howOccurs,
    bin_to_uuid(Budget_idBudget) as budget_id
FROM budget_item
WHERE howOccurs = 'U' AND period != 'OD';

-- Check Rule 2: Scheduled occurrences cannot have ON_DEMAND period
SELECT
    'VIOLATION: Scheduled HowOccurs with ON_DEMAND period' as violation_type,
    bin_to_uuid(idBudgetItem) as budget_item_id,
    payee,
    category,
    period,
    howOccurs,
    bin_to_uuid(Budget_idBudget) as budget_id
FROM budget_item
WHERE period = 'OD' AND howOccurs IN ('P', 'VP', 'C');

-- Get summary count of violations
SELECT
    'Total violations found' as summary,
    COUNT(*) as violation_count
FROM budget_item
WHERE
    (howOccurs = 'U' AND period != 'OD') OR
    (period = 'OD' AND howOccurs IN ('P', 'VP', 'C'));

-- Show all valid ENVELOPE items with ON_DEMAND period (these are OK)
SELECT
    'VALID: ENVELOPE with ON_DEMAND (allowed)' as status,
    bin_to_uuid(idBudgetItem) as budget_item_id,
    payee,
    category,
    period,
    howOccurs,
    bin_to_uuid(Budget_idBudget) as budget_id
FROM budget_item
WHERE period = 'OD' AND howOccurs = 'E';

-- Example correction script (run only if violations found above)
-- UPDATE budget_item
-- SET period = 'OD'
-- WHERE howOccurs = 'U' AND period != 'OD';
--
-- UPDATE budget_item
-- SET howOccurs = 'U'
-- WHERE period = 'OD' AND howOccurs IN ('P', 'VP', 'C');
