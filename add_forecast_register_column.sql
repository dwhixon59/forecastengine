-- ============================================================================
-- Linked Account Transfers, step 1:  a forecast belongs to one and only one
-- register.
--
-- Until now a forecast belonged to a budget, and six registers shared the
-- Bill Pay Dave budget.  That made "this register has no forecast" impossible
-- to say, which is the statement the transfer feature's feed convention rests
-- on (see LINKED_ACCOUNT_TRANSFERS_DESIGN.md).
--
-- The budget association stays -- forecast items still reference budget items.
-- This only adds the register the forecast actually belongs to.
--
-- The rollback is in rollback_forecast_register_column.sql.  Write it down
-- before running this:  there is no remote for this repository, so the
-- database is the one thing `git checkout` cannot recover.
-- ============================================================================

ALTER TABLE forecast ADD COLUMN Register_idRegister BINARY(16) NULL;

-- Each of the four surviving forecasts belongs to the register that carries the
-- money, so the assignment is deterministic and can be written by name.
UPDATE forecast f
  JOIN register r ON r.Name = CASE f.description
        WHEN 'Bill Pay Account - Danni Forecast'   THEN 'Bill Pay Danni'
        WHEN 'Bill Pay Account - Dave Forecast'    THEN 'Bill Pay Dave'
        WHEN 'Bill Pay Envelopes Forecast'         THEN 'Bill Pay Envelopes'
        WHEN 'Citi AAdvantage Mastercard Forecast' THEN 'Citi AAdvantage Mastercard'
      END
  SET f.Register_idRegister = r.idRegister;

-- The column stays nullable so a forecast with no register is representable
-- rather than an error, but every forecast has one after this migration.
ALTER TABLE forecast
  ADD CONSTRAINT fk_Forecast_Register1
  FOREIGN KEY (Register_idRegister) REFERENCES register (idRegister)
  ON DELETE SET NULL ON UPDATE CASCADE;

CREATE INDEX fk_Forecast_Register1_idx ON forecast (Register_idRegister);

-- Verification:  every forecast should now name exactly one register, and the
-- seven feedless registers should name none.
SELECT f.description AS forecast, r.Name AS register
  FROM forecast f LEFT JOIN register r ON r.idRegister = f.Register_idRegister;
