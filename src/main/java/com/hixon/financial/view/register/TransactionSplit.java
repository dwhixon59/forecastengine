package com.hixon.financial.view.register;

import com.hixon.financial.Utility;
import com.hixon.financial.model.DependentEntity;
import com.hixon.financial.model.EntityException;
import com.hixon.financial.model.FinancialAppEntityInt;
import com.hixon.financial.model.budget.BudgetItemMerchant;
import com.hixon.financial.model.register.RegisterException;
import com.hixon.financial.model.register.Transaction;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TransactionSplit extends DependentEntity {

   /*
    * Fields in the Wells Fargo download file transaction classifier:
    */
   private static final String selectQuery = "select amount, BudgetItem_idBudgetItem as idBudgetItem, " +
           "Transaction_idTransaction as idTransaction from forecastdatabase.transaction_split ";

   private static final String insertQuery = "insert into ForecastDatabase.Transaction_Split (amount, " +
           "BudgetItem_idBudgetItem, Transaction_idTransaction) values (";

   private static final String deleteQuery = "delete from ForecastDatabase.Transaction_Split ";

   protected double amount = 0;
   UUID idBudgetItem = null;
   UUID idTransaction = null;

   public TransactionSplit(BudgetItemMerchant budgetItemMerchant, double splitAmount) {
      super();
   }


   /*
    * Getters and setters for BudgetItemMerchant:
    */
   public double getAmount() {
      return amount;
   }

   public void setAmount(double amount) {
      this.amount = amount;
   }

   public UUID getIdBudgetItem() {
      return idBudgetItem;
   }

   public void setIdBudgetItem(UUID idBudgetItem) {
      this.idBudgetItem = idBudgetItem;
   }

   public UUID getIdTransaction() {
      return idTransaction;
   }

   public void setIdTransaction(UUID idTransaction) {
      this.idTransaction = idTransaction;
   }


   /*
    * Constructors for BudgetItemMerchant:
    */
   public TransactionSplit(double amount, UUID idBudgetItem, UUID idTransaction) {
      super();
      this.amount = amount;
      this.idBudgetItem = idBudgetItem;
      this.idTransaction = idTransaction;
   }

   public TransactionSplit(ResultSet rs) throws RegisterException {
      try {

         if (rs == null) throw new RegisterException("Result set passed into TransactionSplit constructor must not be " +
                 "null.");
         this.amount = rs.getDouble("amount");
         this.idBudgetItem = UUID.fromString(rs.getString("idBudgetItem"));
         this.idTransaction = UUID.fromString(rs.getString("idTransaction"));
         dirty = false;

      } catch (SQLException e) {

         RegisterException re = new RegisterException("Error reading in the Merchant-Payee row for " + rs.toString());
         re.initCause(e);
         throw (re);
      }
   }


   /*
    * Load and save methods for BudgetItemMerchant:
    */
   public void save() throws RegisterException, EntityException {
      super.save(insertQuery + amount + ", uuid_to_bin('" + idBudgetItem + "'), " +
              "uuid_to_bin('" + idTransaction + "')", "Databsae error occurred inserting " +
              "a TransactionSplit into the database.");
   }


   /*
    * Main methods for TransactionSplit
    */
   // Get a list of transaction split items for a transaction:
   public static List<TransactionSplit> getSplitsForTransaction(Transaction transaction) throws SQLException, RegisterException {

      // Find out what budget items are associated with the transaction for this transaction:
      String query = selectQuery + "where Transaction_idTransaction = uuid_to_bin('" + transaction.getId() + "')";
      try {
         Statement statement = Utility.getDbConnection().createStatement();
         ResultSet rs = statement.executeQuery(query);
         List<TransactionSplit> transactionSplits = new ArrayList<TransactionSplit>();
         while (rs.next()) {
            transactionSplits.add(new TransactionSplit(rs));
         }
         return transactionSplits;

      } catch (SQLException e) {
         RegisterException re = new RegisterException("Database error occurred trying to get the transaction splits for " +
                 "transaction " + transaction.getPayee() + " for $" + transaction.getAmount());
         re.initCause(e);
         throw re;
      }
   }

   // Delete the splits for a transaction:
   public static void deleteSplitsForTransaction(UUID id) throws EntityException {
         FinancialAppEntityInt.deleteMultiple(deleteQuery + "where Transaction_idTransaction = uuid_to_bin('" + id + "')",
                 "Databsae error occurred deleting TransactionSplits from the database.");
      }
}
