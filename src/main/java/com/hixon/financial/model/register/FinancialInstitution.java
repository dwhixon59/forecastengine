package com.hixon.financial.model.register;

import com.hixon.financial.view.ViewException;
import org.apache.commons.csv.CSVRecord;

import java.sql.SQLException;
import java.text.ParseException;

public interface FinancialInstitution {

   // Load a single record from a CSV file into a single transaction instance with associated merchant:
   public Transaction loadFromCSV(CSVRecord record) throws ParseException, RegisterException, ViewException, SQLException;

   // Go from raw data to something usable:

   // Remove corrupt data:

   // Remove invalid data combinations:

   // Keep only what you may need:

   // Convert the data to a usable format that can be processed by our analysis software or code (CSV, JSON, XML, SQL, etc.)

   // Parse a payee string from a particular bank into a Merchant payee:
   String parseMerchantPayee() throws ParseException, RegisterException, SQLException;
}
