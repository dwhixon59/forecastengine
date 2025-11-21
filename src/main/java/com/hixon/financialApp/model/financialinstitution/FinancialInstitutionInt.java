package com.hixon.financialApp.model.financialinstitution;

import com.hixon.financialApp.model.entity.EntityException;
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

    /**
     * Returns the enum class representing CSV column headers for this financial institution.
     * This is used by CSV parsers to map column names to enum values.
     *
     * @return the Class object for the CSV headers enum
     */
    Class<? extends Enum<?>> getCsvHeadersClass();

    // Get the base name that will be used in constructing the import record ID:
    String getRegisterImportRecordBaseName(CSVRecord record) throws ParseException;

    // Load a single record from a CSV file into a single transaction instance with associated merchant:
    Transaction createFromCSVRecord(CSVRecord record, String importRecordId) throws Exception;

    // Parse a payee string from a particular bank into a Merchant payee:
    String parseMerchantPayee(Calendar date, double amount, String payee) throws Exception;

    // Create a transaction and load it from a provisional CSV record:
    Transaction loadProvisionalTransactionFromCSV(String line, Register register) throws Exception;

    // Get a provisional transaction from an import record:
    Transaction getMatchingProvisionalTransaction(Transaction clearedTransaction)
            throws RegisterException, SQLException, EntityException, java.text.ParseException, Exception;

    /**
     * Reconciles a cleared transaction with its matching provisional transaction (if one exists).
     * This method handles:
     * - Finding the matching provisional transaction
     * - Detecting and handling tips (amount differences)
     * - Adjusting register balance for tips
     * - Updating transaction splits for tips
     * - Transferring provisional transaction properties to the cleared transaction
     *
     * @param clearedTransaction The cleared transaction from the CSV import
     * @param provisionalTransaction The matching provisional transaction (or null if none found)
     * @param register The register to update
     * @param splits The splits for the provisional transaction (will be updated if tip detected)
     * @return true if a provisional transaction was found and reconciled, false otherwise
     * @throws Exception if an error occurs during reconciliation
     */
    boolean reconcileProvisionalTransaction(Transaction clearedTransaction,
                                           Transaction provisionalTransaction,
                                           Register register,
                                           List<com.hixon.financialApp.model.budget.TransactionSplit> splits)
            throws Exception;

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

