package com.hixon.financial.model.register;

import com.hixon.financial.Utility;
import com.hixon.financial.model.EntityException;
import com.hixon.financial.model.budget.BudgetException;
import com.hixon.financial.model.budget.BudgetItem;
import com.hixon.financial.view.register.TransactionResolver;
import org.apache.commons.csv.CSVRecord;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;

public class GenericClassifer implements FinancialInstitution {

    protected BudgetItem[] budgetItems;

    // Constructors:
    public GenericClassifer(TransactionResolver resolver) throws SQLException, BudgetException, EntityException {

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
    public String getImportRecordBaseName(CSVRecord record) {
        return null;
    }

    @Override
    public Transaction loadFromCSV(CSVRecord record, String importRecordId) throws SQLException {
        return null;
    }

    @Override
    public String parseMerchantPayee(String payee) throws ParseException, RegisterException, SQLException {
        return null;
    }
}

