package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.view.ViewException;
import org.apache.commons.csv.CSVRecord;

import java.sql.SQLException;
import java.text.ParseException;
import java.util.Calendar;

public interface FinancialInstitutionInt {

    // Get the base name that will be used in constructing the import record ID:
    String getRegisterImportRecordBaseName(CSVRecord record) throws ParseException;

    // Load a single record from a CSV file into a single transaction instance with associated merchant:
    Transaction createFromCSVRecord(CSVRecord record, String importRecordId) throws ParseException, RegisterException,
            ViewException, SQLException, SkipException, QuitException, CancelException;

    // Parse a payee string from a particular bank into a Merchant payee:
    String parseMerchantPayee(Calendar date, double amount, String payee) throws ParseException, RegisterException, SQLException, SkipException, QuitException, CancelException;

    // Create a transaction and load it from a provisional CSV record:
    Transaction loadProvisionalTransactionFromCSV(String line, Register register) throws ParseException, SQLException, RegisterException, SkipException, QuitException, CancelException;

    // Get a provisional transaction from an import record:
    Transaction getMatchingProvisionalTransaction(CSVRecord record, Merchant transaction) throws RegisterException, SQLException, EntityException;
}
