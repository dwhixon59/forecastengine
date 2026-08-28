-- Rollback for add_forecast_transaction_transfer_columns.sql.
--
-- Any counterpart forecast transactions already created stay behind as ordinary
-- overridden forecast transactions once these columns are gone.  Delete them
-- first if you want a clean reversal:
--
--   DELETE FROM forecast_transaction WHERE SourceTransaction_idTransaction IS NOT NULL;

ALTER TABLE forecast_transaction DROP FOREIGN KEY fk_ForecastTransaction_SourceTransaction;
ALTER TABLE forecast_transaction DROP FOREIGN KEY fk_ForecastTransaction_SourceBudgetItem;
DROP INDEX fk_ForecastTransaction_SourceTransaction_idx ON forecast_transaction;
DROP INDEX fk_ForecastTransaction_SourceBudgetItem_idx ON forecast_transaction;
DROP INDEX idx_ft_sourceReference ON forecast_transaction;
ALTER TABLE forecast_transaction
  DROP COLUMN SourceTransaction_idTransaction,
  DROP COLUMN SourceBudgetItem_idBudgetItem,
  DROP COLUMN sourceReference,
  DROP COLUMN transferPairingUnknown;
