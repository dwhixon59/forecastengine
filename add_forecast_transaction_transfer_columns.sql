-- ============================================================================
-- Linked Account Transfers, step 3:  mark the forecast transactions that are
-- the expected other side of a transfer already processed elsewhere.
--
--   SourceTransaction_idTransaction
--       the transaction this counterpart was created from.  It is the "already
--       created" check that keeps Phase 5.5 idempotent, the link that lets the
--       far import learn the budget item pairing, the audit trail, and what
--       lets Phase 2.5 say "Taken from the corresponding transfer in ..."
--       instead of its usual auto-match message.
--
--   SourceBudgetItem_idBudgetItem
--       the budget item of the SPLIT the counterpart was created from.  A transfer with several
--       ad-hoc splits produces one counterpart each, and the pairing that gets learned is keyed on
--       the source budget item -- so without this the far import could only guess which split a
--       counterpart came from.  With one split (the overwhelming case) it is redundant; with
--       several it is what makes learning exact instead of a heuristic on the amount.
--
--   sourceReference
--       the bank's own reference for the transfer (Wells Fargo writes the same
--       REF # into both sides).  It CONFIRMS a match, and never gates one --
--       only 43% of transfers carry one, so nothing may be conditional on its
--       presence.  It also survives a source row being deleted and re-imported
--       with a new UUID, which the source transaction id does not.
--
--   transferPairingUnknown
--       set when the counterpart had to be created before we knew which budget
--       item the far side belongs to.  Its budget item is a placeholder, so
--       Phase 2.5 must NOT assign it as a split -- it reports the transfer and
--       falls through to the questions it would otherwise have asked.
--
-- ON DELETE SET NULL rather than CASCADE:  deleting the source transaction is
-- handled deliberately in TransferCounterpartController.deleteCounterpartsFor,
-- which removes the whole counterpart rather than orphaning its date/amount.
--
-- The rollback is in rollback_forecast_transaction_transfer_columns.sql.
-- ============================================================================

ALTER TABLE forecast_transaction
  ADD COLUMN SourceTransaction_idTransaction BINARY(16) NULL,
  ADD COLUMN SourceBudgetItem_idBudgetItem BINARY(16) NULL,
  ADD COLUMN sourceReference VARCHAR(32) NULL,
  ADD COLUMN transferPairingUnknown TINYINT NOT NULL DEFAULT 0;

CREATE INDEX fk_ForecastTransaction_SourceTransaction_idx
  ON forecast_transaction (SourceTransaction_idTransaction);

CREATE INDEX idx_ft_sourceReference ON forecast_transaction (sourceReference);

CREATE INDEX fk_ForecastTransaction_SourceBudgetItem_idx
  ON forecast_transaction (SourceBudgetItem_idBudgetItem);

ALTER TABLE forecast_transaction
  ADD CONSTRAINT fk_ForecastTransaction_SourceTransaction
  FOREIGN KEY (SourceTransaction_idTransaction) REFERENCES transaction (idTransaction)
  ON DELETE SET NULL ON UPDATE CASCADE,
  ADD CONSTRAINT fk_ForecastTransaction_SourceBudgetItem
  FOREIGN KEY (SourceBudgetItem_idBudgetItem) REFERENCES budget_item (idBudgetItem)
  ON DELETE SET NULL ON UPDATE CASCADE;
