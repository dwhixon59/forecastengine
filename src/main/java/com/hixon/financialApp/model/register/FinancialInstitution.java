package com.hixon.financialApp.model.register;

import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.view.ViewException;
import org.apache.commons.csv.CSVRecord;

import java.sql.SQLException;
import java.text.ParseException;

public interface FinancialInstitution {

   // Get the base name that will be used in constructing the import record ID:
   String getRegisterImportRecordBaseName(CSVRecord record) throws ParseException;

      // Load a single record from a CSV file into a single transaction instance with associated merchant:
      Transaction createFromCSVRecord(CSVRecord record, String importRecordId) throws ParseException, RegisterException,
           ViewException, SQLException;

   // Go from raw data to something usable:

   // Remove corrupt data:

   // Remove invalid data combinations:

   // Keep only what you may need:

   // Convert the data to a usable format that can be processed by our analysis software or code (CSV, JSON, XML, SQL, etc.)

   // Parse a payee string from a particular bank into a Merchant payee:
   String parseMerchantPayee(String payee) throws ParseException, RegisterException, SQLException;

   // Create a transaction and load it from a provisional CSV record:
   Transaction loadProvisionalTransactionFromCSV(String line, Register register) throws ParseException, SQLException, RegisterException;

   // Get a provisional transaction from an import record:
   Transaction getMatchingProvisionalTransaction(CSVRecord record) throws RegisterException, SQLException, EntityException;

   // Update a provisional transaction from a posted transaction CSV record:
   void updateFromCSVRecord(Transaction transaction, CSVRecord record, String importRecordId) throws ParseException, RegisterException, SQLException;
}
