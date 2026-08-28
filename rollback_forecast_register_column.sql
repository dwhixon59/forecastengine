-- ============================================================================
-- Rollback for add_forecast_register_column.sql.
--
-- Dropping the column restores the budget-only association.  Nothing else in
-- the schema depends on it, so this is a complete reversal -- but the
-- register assignments are lost, and re-running the up migration recreates
-- them from the forecast descriptions.
-- ============================================================================

ALTER TABLE forecast DROP FOREIGN KEY fk_Forecast_Register1;
DROP INDEX fk_Forecast_Register1_idx ON forecast;
ALTER TABLE forecast DROP COLUMN Register_idRegister;
