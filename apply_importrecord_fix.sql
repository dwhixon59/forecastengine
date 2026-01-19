-- Complete Fix for Cross-Register Transaction Import Issue
-- Date: January 16, 2026
--
-- This script does the following:
-- 1. Identifies and reports duplicate importRecordIds across registers
-- 2. Drops the old unique constraint
-- 3. Adds the correct composite unique constraint
--
-- IMPORTANT: Review the duplicate report before proceeding with the fix!

USE forecastengine;

-- Step 1: Report on duplicate importRecordIds across different registers
SELECT '=== DUPLICATE IMPORTRECORDIDS ACROSS REGISTERS ===' as '';
SELECT
    t.importRecordId,
    COUNT(DISTINCT bin_to_uuid(t.Register_idRegister)) as register_count,
    COUNT(*) as transaction_count,
    GROUP_CONCAT(DISTINCT r.name ORDER BY r.name) as registers,
    GROUP_CONCAT(DISTINCT DATE_FORMAT(t.postDate, '%Y-%m-%d') ORDER BY t.postDate) as dates,
    GROUP_CONCAT(DISTINCT t.payee ORDER BY t.payee SEPARATOR ' | ') as payees
FROM transaction t
JOIN register r ON t.Register_idRegister = r.idRegister
WHERE t.importRecordId IS NOT NULL
GROUP BY t.importRecordId
HAVING COUNT(DISTINCT bin_to_uuid(t.Register_idRegister)) > 1
ORDER BY transaction_count DESC, t.importRecordId;

-- If you see duplicates above, those are likely cross-contaminated transactions
-- from the bug. You may want to review and delete the incorrect ones manually.

-- Step 2: Drop the old unique constraint on importRecordId alone
SELECT '=== DROPPING OLD CONSTRAINT ===' as '';
ALTER TABLE `transaction` DROP INDEX `importRecord_UNIQUE`;
SELECT 'Old constraint dropped successfully' as '';

-- Step 3: Add a new composite unique constraint on importRecordId + Register_idRegister
SELECT '=== ADDING NEW COMPOSITE CONSTRAINT ===' as '';
ALTER TABLE `transaction`
ADD UNIQUE KEY `importRecord_Register_UNIQUE` (`importRecordId`, `Register_idRegister`);
SELECT 'New composite constraint added successfully' as '';

-- Step 4: Verify the change
SELECT '=== VERIFYING NEW SCHEMA ===' as '';
SHOW KEYS FROM `transaction` WHERE Key_name LIKE '%import%';

SELECT '=== FIX COMPLETE ===' as '';
SELECT 'The transaction table now has a composite unique constraint on (importRecordId, Register_idRegister)' as '';
SELECT 'This allows the same FITID in different registers while preventing duplicates within a register' as '';
