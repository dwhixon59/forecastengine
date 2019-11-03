package com.hixon.financial.model.register;

import com.hixon.financial.view.ViewException;
import org.apache.commons.csv.CSVRecord;

import java.sql.SQLException;
import java.text.ParseException;

public interface FinancialInstitution {

   // Get the base name that will be used in constructing the import record ID:
   String getImportRecordBaseName(CSVRecord record) throws ParseException;

      // Load a single record from a CSV file into a single transaction instance with associated merchant:
      Transaction loadFromCSV(CSVRecord record, String importRecordId) throws ParseException, RegisterException,
           ViewException, SQLException;

   // Go from raw data to something usable:

   // Remove corrupt data:

   // Remove invalid data combinations:

   // Keep only what you may need:

   // Convert the data to a usable format that can be processed by our analysis software or code (CSV, JSON, XML, SQL, etc.)

   // Parse a payee string from a particular bank into a Merchant payee:
   String parseMerchantPayee(String payee) throws ParseException, RegisterException, SQLException;
}
