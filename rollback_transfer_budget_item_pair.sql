-- Rollback for add_transfer_budget_item_pair.sql.  The learned pairings are
-- lost; they are relearned one question at a time by the far imports.
DROP TABLE IF EXISTS transfer_budget_item_pair;
