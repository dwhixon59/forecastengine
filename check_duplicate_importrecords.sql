-- Check for existing duplicate importRecordIds across different registers
-- This will help us understand if there's existing data that needs to be cleaned up

USE forecastengine;

-- Find duplicate importRecordIds across different registers
SELECT
    t.importRecordId,
    COUNT(DISTINCT bin_to_uuid(t.Register_idRegister)) as register_count,
    COUNT(*) as transaction_count,
    GROUP_CONCAT(DISTINCT r.name ORDER BY r.name) as registers
FROM transaction t
JOIN register r ON t.Register_idRegister = r.idRegister
WHERE t.importRecordId IS NOT NULL
GROUP BY t.importRecordId
HAVING COUNT(DISTINCT bin_to_uuid(t.Register_idRegister)) > 1
ORDER BY transaction_count DESC, t.importRecordId;
