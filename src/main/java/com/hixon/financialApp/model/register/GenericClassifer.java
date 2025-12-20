package com.hixon.financialApp.model.register;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.financialinstitution.FinancialInstitutionInt;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.base.ViewInt;
import org.apache.commons.csv.CSVRecord;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;

public class GenericClassifer implements FinancialInstitutionInt, Iterator<Transaction> {

    protected BudgetItem[] budgetItems;

    // Constructors:
    public GenericClassifer(ViewInt resolver) throws SQLException, BudgetException, EntityException {

        // Create a prepared statement for using with the database:
        Statement stmt = null;
        try {
            stmt = Utility.getDbConnection().createStatement();
        } catch (SQLException e) {
            System.out.println("[SEVERE]  dbConnection.createStatement() threw exception");
            if (stmt != null) stmt.close();
            throw e;
        }

        // Create an arrary to hold them:
        budgetItems = new BudgetItem[BudgetItem.getItemCount()];

        // Create a result set containing all the budget items:
        ResultSet rs = null;
        try {
            rs = stmt.executeQuery(BudgetItem.getSelectQuery() + "order by searchString desc");
        } catch (SQLException e) {
            System.out.println("[SEVERE]  SQL Error attempting to retrieve a list of items in the budget.");
            stmt.close();
            if (rs != null) rs.close();
            throw e;
        }

        // Read all the items in the budget into an array for matching:
        int i = 0;
        BudgetItem budgetItem = null;
        while (rs.next()) {

            // Add the next item from the budget to the array of budget items:
            budgetItems[i] = new BudgetItem().loadFromResultSet(rs);
            //if (budgetItems[i].getSearchString() != null && budgetItems[i].getSearchString().length() > 0) {
            //    budgetItems[i].setPattern(Pattern.compile(budgetItems[i].getSearchString(), Pattern.CASE_INSENSITIVE));
            //}
            i++;

        } // End for each item in the budget.
    } // Classifer(Connection dbConnection).


    public BudgetItem classify(Transaction transaction) {

        // Apply the search strings one at a time to the transaction payee until there is a match or none remain:
//        Matcher matcher = null;
//        int i = 0;
//        boolean found = false;
//        for (BudgetItem budgetItem : budgetItems) {
//
//            // Apply the regular expression for this budget item to the transaction payee:
//            if (budgetItem.getPattern() != null) {
//
//                matcher = budgetItem.getPattern().matcher(transaction.getPayee());
//
//                // If we find a matching transaction:
//                if (
//                        matcher.find() && (
//                                budgetItem.getEndDate() == null ||
//                                        budgetItem.getEndDate().compareTo(transaction.getPostDate()) >= 0
//                        )
//                ) {
//                    // then we found the first match, so stop looking:
//                    found = true;
////                    System.out.println("Matched search string " + budgetItems[i].getSearchString() + " from item " +
////                            budgetItem.getPayee() + " to transaction " + transaction.getPayee());
//                    break;
//                }
//            }
//            i++;
//        }
//
//        // Return the first matching transaction:
//        return (found) ? budgetItems[i] : null;
        return null;
    }

    @Override
    public Class<? extends Enum<?>> getCsvHeadersClass() {
        // Generic classifier doesn't have specific CSV headers
        return null;
    }

    @Override
    public String getRegisterImportRecordBaseName(CSVRecord record) {
        return null;
    }

    @Override
    public Transaction createFromCSVRecord(CSVRecord record, String importRecordId) throws SQLException {
        return null;
    }

    @Override
    public String parseMerchantPayee(Calendar date, double amount, String payee) throws ParseException, RegisterException, SQLException {
        return null;
    }

    @Override
    public Transaction loadProvisionalTransactionFromCSV(String line, Register register) throws ParseException, SQLException {
        return null;
    }

    @Override
    public Transaction getMatchingProvisionalTransaction(Transaction clearedTransaction)
            throws RegisterException, SQLException, EntityException {
        return null;
    }

    @Override
    public boolean reconcileProvisionalTransaction(Transaction clearedTransaction,
                                                   Transaction provisionalTransaction,
                                                   Register register,
                                                   List<com.hixon.financialApp.model.budget.TransactionSplit> splits)
            throws Exception {
        // Generic classifier doesn't support provisional transactions
        return false;
    }

    @Override
    public String extractUserDescription(String payee) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<User> extractUsers(String payee) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String extractAccountType(String payee) {return "";}

    // Iterator methods - not supported by GenericClassifier
    @Override
    public boolean hasNext() {
        throw new UnsupportedOperationException("GenericClassifier does not support iterator pattern");
    }

    @Override
    public Transaction next() {
        throw new UnsupportedOperationException("GenericClassifier does not support iterator pattern");
    }

    @Override
    public void close() throws Exception {
        // No-op for GenericClassifier
    }
}
