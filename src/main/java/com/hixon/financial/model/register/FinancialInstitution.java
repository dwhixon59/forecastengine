package com.hixon.financial.model.register;

import com.hixon.financial.model.budget.BudgetException;
import com.hixon.financial.model.budget.BudgetItem;
import com.hixon.financial.view.ViewException;
import org.apache.commons.csv.CSVRecord;

import java.sql.SQLException;
import java.text.ParseException;

public interface FinancialInstitution {

   // Find the budget item that goes with this transaction:
   BudgetItem classify(Transaction transaction) throws SQLException, BudgetException, ParseException, RegisterException;

   // Load a single record from a CSV file into a single transaction instance with associated merchant:
   public Transaction loadFromCSV(CSVRecord record) throws ParseException, RegisterException, ViewException, SQLException;

      // Parse a payee string from a particular bank into a Merchant payee:
   String parseMerchantPayee() throws ParseException, RegisterException, SQLException;
}
