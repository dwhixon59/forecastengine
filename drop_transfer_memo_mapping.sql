-- Removes the transfer_memo_mapping table used by the (now removed) transfer memo
-- mapping feature. That feature fixed a payee (e.g. "HIXON D") to exactly one
-- register, which is incorrect because the same payee is ambiguous and can refer
-- to different accounts. Run this once to clean up the schema.
DROP TABLE IF EXISTS transfer_memo_mapping;

