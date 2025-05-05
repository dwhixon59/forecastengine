package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.model.user.User;
import org.apache.commons.csv.CSVRecord;

import java.sql.SQLException;
import java.text.ParseException;
import java.util.Calendar;
import java.util.List;

public interface FinancialInstitutionInt {

    // Get the base name that will be used in constructing the import record ID:
    String getRegisterImportRecordBaseName(CSVRecord record) throws ParseException;

    // Load a single record from a CSV file into a single transaction instance with associated merchant:
    Transaction createFromCSVRecord(CSVRecord record, String importRecordId) throws Exception;

    // Parse a payee string from a particular bank into a Merchant payee:
    String parseMerchantPayee(Calendar date, double amount, String payee) throws Exception;

    // Create a transaction and load it from a provisional CSV record:
    Transaction loadProvisionalTransactionFromCSV(String line, Register register) throws Exception;

    // Get a provisional transaction from an import record:
    Transaction getMatchingProvisionalTransaction(CSVRecord record, Merchant transaction) throws RegisterException, SQLException, EntityException;

    // Extract the memo or user description from the raw text of a register entry:
    String extractUserDescription(String payee);

    /**
     * Extracts the user identification information from the raw text of a register entry.  Then looks up the user and
     * returns the user object if found.
     *
     * @param payee the raw text of the register entry containing user information
     * @return a User object containing the extracted user data
     */
    List<User> extractUsers(String payee);

    /**
     * Extracts the account type information from the given payee string.
     *
     * @param payee the raw text of the payee containing account type information
     * @return a string representing the extracted account type
     */
    String extractAccountType(String payee);
}
