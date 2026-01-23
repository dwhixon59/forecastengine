-- Add lastRenderedDate column to forecast table
-- This tracks when the forecast was last rendered to an external file (Excel, CSV, etc.)
-- Used to detect if user has modified the external file and may want to import changes

ALTER TABLE forecast
ADD COLUMN lastRenderedDate DATETIME NULL
COMMENT 'Timestamp when forecast was last rendered to external file (Excel/CSV)';

-- Update existing forecasts to NULL (unknown last render date)
-- This will cause the system to not prompt for import until after first render with new code
