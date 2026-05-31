-- Enhancement 2: Memo-to-Register Mapping Table
-- Persists resolved payee->register mappings so future import sessions can auto-resolve
-- the same transfer without prompting the user.
--
-- Run this script once against your financialApp database.
CREATE TABLE IF NOT EXISTS transfer_memo_mapping (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    payee_pattern VARCHAR(512) NOT NULL
                  COMMENT 'Normalized (upper-case, REF# stripped) payee string from QFX/CSV',
    id_register   BINARY(16)  NOT NULL
                  COMMENT 'FK -> register.idRegister',
    usage_count   INT         NOT NULL DEFAULT 1
                  COMMENT 'Number of times this mapping has been used',
    last_used     DATE        NOT NULL
                  COMMENT 'Date this mapping was last applied',
    UNIQUE KEY uq_pattern_register (payee_pattern(255), id_register),
    CONSTRAINT fk_tmm_register
        FOREIGN KEY (id_register) REFERENCES register (idRegister)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Enhancement 2: payee-pattern to register mapping for automated transfer resolution';
