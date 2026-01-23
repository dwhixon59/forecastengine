-- Fix for Cross-Register Transaction Import Issue
-- Date: January 16, 2026
--
-- Problem: The UNIQUE constraint on importRecordId prevents the same FITID from being used
-- in different registers, which is incorrect. Different registers can have transactions with
-- the same FITID from their financial institution.
--
-- Solution: Change the unique constraint to be a composite of importRecordId + Register_idRegister
-- This allows the same FITID in different registers while preventing duplicate imports within
-- the same register.

USE forecastengine;

-- Drop the old unique constraint on importRecordId alone
ALTER TABLE `transaction` DROP INDEX `importRecord_UNIQUE`;

-- Add a new composite unique constraint on importRecordId + Register_idRegister
-- Note: We need to handle NULL values, so we use a regular index instead of UNIQUE
-- and enforce uniqueness in the application code
ALTER TABLE `transaction`
ADD UNIQUE KEY `importRecord_Register_UNIQUE` (`importRecordId`, `Register_idRegister`);

-- Verify the change
SHOW CREATE TABLE `transaction`;
