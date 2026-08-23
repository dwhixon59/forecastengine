-- ============================================================================
-- Linked Account Transfers, step 2:  remember which budget item on the far side
-- of a transfer corresponds to the budget item on this side.
--
-- A transfer's counterpart needs a budget item in the counterparty's budget, so
-- something has to know that Danni's 'Danni's contribution' corresponds to
-- Dave's 'Room rental and utilities'.  The pairing is stable, so it is asked
-- once -- in the place the application already asks, during the far import --
-- and recorded here.
--
-- The key is deliberately (source budget item, target budget) rather than a
-- payee string.  The TransferMemoMapping removed in e6253c8 was keyed on a
-- payee such as 'HIXON D', which is ambiguous across accounts; a budget item
-- plus a target budget already carries the semantics.
--
-- The rollback is in rollback_transfer_budget_item_pair.sql.
-- ============================================================================

CREATE TABLE transfer_budget_item_pair (
  idTransferBudgetItemPair BINARY(16) NOT NULL,
  sourceBudgetItem         BINARY(16) NOT NULL,
  targetBudget             BINARY(16) NOT NULL,
  targetBudgetItem         BINARY(16) NOT NULL,
  PRIMARY KEY (idTransferBudgetItemPair),
  UNIQUE KEY uk_source_target (sourceBudgetItem, targetBudget),
  KEY fk_TransferPair_TargetBudgetItem_idx (targetBudgetItem),
  KEY fk_TransferPair_TargetBudget_idx (targetBudget),
  CONSTRAINT fk_TransferPair_SourceBudgetItem
    FOREIGN KEY (sourceBudgetItem) REFERENCES budget_item (idBudgetItem)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_TransferPair_TargetBudgetItem
    FOREIGN KEY (targetBudgetItem) REFERENCES budget_item (idBudgetItem)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_TransferPair_TargetBudget
    FOREIGN KEY (targetBudget) REFERENCES budget (idBudget)
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
