package com.hixon.financialApp.model.register;

import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.model.entity.DependentEntity;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.budget.BudgetItemMerchant;
import com.hixon.financialApp.model.forecast.ForecastException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import static com.hixon.financialApp.model.forecast.ForecastTransactionSplit.SplitDisposition;
import static java.util.Calendar.DATE;

public class TransactionSplit extends DependentEntity {

   /*
    * Fields in the Wells Fargo download file transaction classifier:
    */

   protected double amount = 0;
   UUID idBudgetItem = null;
   BudgetItem budgetItem = null;
   UUID idTransaction = null;
   Transaction transaction = null;

   // Disposition of this split in the current forecast while it is being reconciled.  Since it only applies to the
   // current forecast during reconciliation, it is not persisted in the database.
   SplitDisposition disposition = SplitDisposition.ASSIGN;


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

   public BudgetItem getBudgetItem() throws EntityException, BudgetException, SQLException {
      if (budgetItem == null) {
         budgetItem = BudgetItem.getById(idBudgetItem);
      }
      return budgetItem;
   }

   public void setBudgetItem(BudgetItem budgetItem) {
      this.budgetItem = budgetItem;
   }

   public UUID getIdTransaction() {
      return idTransaction;
   }

   public void setIdTransaction(UUID idTransaction) {
      this.idTransaction = idTransaction;
   }

   public Transaction getTransaction() throws EntityException, SQLException {
      if (transaction == null) {
         transaction = Transaction.getById(idTransaction);
      }
      return transaction;
   }

   public void setTransaction(Transaction transaction) {
      this.transaction = transaction;
   }

   public void setDisposition(SplitDisposition disposition) {
      this.disposition = disposition;
   }

   public SplitDisposition getDisposition() {
      return disposition;
   }


   /*
    * Constructors for BudgetItemMerchant:
    */
   public TransactionSplit(double splitAmount, BudgetItemMerchant budgetItemMerchant, Transaction transaction) {
      super();
      this.amount = splitAmount;
      this.idBudgetItem = budgetItemMerchant.getIdBudgetItem();
      this.budgetItem = budgetItemMerchant.getBudgetItem();
      this.idTransaction = transaction.getId();
      this.transaction = transaction;
      setDirty(true);
   }

   public TransactionSplit(double amount, UUID idBudgetItem, UUID idTransaction) {
      super();
      this.amount = amount;
      this.idBudgetItem = idBudgetItem;
      this.idTransaction = idTransaction;
      setDirty(true);
   }

   public TransactionSplit(ResultSet rs) throws RegisterException {
      try {

         if (rs == null) throw new RegisterException("Result set passed into TransactionSplit constructor must not be " +
                 "null.");
         this.amount = rs.getDouble("amount");
         this.idBudgetItem = UUID.fromString(rs.getString("idBudgetItem"));
         this.idTransaction = UUID.fromString(rs.getString("idTransaction"));
         setDirty(false);

      } catch (SQLException e) {

         RegisterException re = new RegisterException("Error reading in the Merchant-Payee row for " + rs.toString());
         re.initCause(e);
         throw (re);
      }
   }


   /*
    * Load and save methods for BudgetItemMerchant:
    */
   private static final String selectQuery = "select amount, bin_to_uuid(BudgetItem_idBudgetItem) as idBudgetItem, " +
           "bin_to_uuid(Transaction_idTransaction) as idTransaction from forecastdatabase.transaction_split ";
   public static String getSelectQuery() {
      return selectQuery;
   }

   private static final String insertQuery = "insert into ForecastDatabase.Transaction_Split (amount, " +
           "BudgetItem_idBudgetItem, Transaction_idTransaction) values (";
   @Override
   public String getInsertQuery() throws BudgetException, ForecastException {
      return null;
   }

   @Override
   public String getInsertOnDuplicateUpdateQuery() throws BudgetException {
      return null;
   }

   @Override
   public String getUpdateByIdQuery() throws BudgetException {
      return null;
   }

   private static final String deleteQuery = "delete from ForecastDatabase.Transaction_Split ";
   @Override
   public String getDeleteByIdQuery() {
      return null;
   }

   @Override
   public String getPrintableEntityTypeName() {
      return "transaction split";
   }

   public static TransactionSplit getbyId(UUID idBudgetItem, UUID idTransaction) throws EntityException, RegisterException {
      ResultSet rs = EntityInt.getSingletonRS(selectQuery + "where Budget_Item_idbBudgetItem = " +
          "uuid_to_bin('" + idBudgetItem + "') and Transaction_idTransaction = uuid_to_bin('" + idTransaction + "'))",
         "trying to retrieve a transaction split");
      return (rs != null) ? new TransactionSplit(rs) : null;
   }

   public void save() throws RegisterException, EntityException {
      super.executeQueryForThis(insertQuery + amount + ", uuid_to_bin('" + idBudgetItem + "'), " +
              "uuid_to_bin('" + idTransaction + "'))", "Databsae error occurred inserting " +
              "a TransactionSplit into the database.");
   }


   /*
    * Helper methods:
    */
   @Override
   public String toString() {
      String s = null;
      try {
         s = "Split: amount of " + Utility.formatDollarAmount(amount) + " on " +
                 Utility.calendarDateToStringDate(getTransaction().getDate()) + " to " +
                 getTransaction().getMerchant().getName() + " applied to budget item " + getBudgetItem().getPayee();
      } catch (Exception | EntityException | BudgetException | RegisterException e) {
         e.printStackTrace();
      }
      return s;
   }


   /*
    * Main methods for TransactionSplit
    */
   // Get a list of transaction split items for a transaction:
   public static List<TransactionSplit> getSplitsForTransaction(Transaction transaction) throws RegisterException {

      // Find out what budget items are associated with the transaction for this transaction:
      String query = selectQuery + "where Transaction_idTransaction = uuid_to_bin('" + transaction.getId() + "')";
      try {
         Statement statement = Utility.getDbConnection().createStatement();
         ResultSet rs = statement.executeQuery(query);
         List<TransactionSplit> transactionSplits = new ArrayList<TransactionSplit>();
         while (rs.next()) {
            transactionSplits.add(new TransactionSplit(rs));
         }
         return (transactionSplits.size() > 0) ? transactionSplits : null;

      } catch (SQLException e) {
         RegisterException re = new RegisterException("Database error occurred trying to get the transaction splits for " +
                 "transaction " + transaction.getPayee() + " for $" + transaction.getAmount());
         re.initCause(e);
         throw re;
      }
   }

   // Delete the splits for a transaction:
   public static void deleteSplitsForTransaction(UUID id) throws EntityException {
         EntityInt.deleteMultiple(deleteQuery + "where Transaction_idTransaction = uuid_to_bin('" + id + "')",
                 "Databsae error occurred deleting TransactionSplits from the database.");
      }

   // Get the splits associated with a budget item in a period:
   public static ResultSet getSplitsForBudgetItemInPeriod(BudgetItem budgetItem, Calendar startDate, Calendar endDate)
           throws EntityException {
      String selectQuery = "select ts.amount, bin_to_uuid(ts.BudgetItem_idBudgetItem) as idBudgetItem, " +
              "bin_to_uuid(ts.Transaction_idTransaction) as idTransaction, ";
      String query = selectQuery + "t.authorizationDate as 'date' from forecastdatabase.transaction_split ts " +
              "inner join ForecastDatabase.Transaction t on ts.Transaction_idTransaction = " +
              "t.idTransaction where ts.BudgetItem_idBudgetItem = uuid_to_bin('" + budgetItem.getId() +"') and " +
              "t.authorizationDate is not null and t.authorizationDate >= " + Utility.calendarDateToSqlDateString(startDate) +
              " and t.authorizationDate <= " + Utility.calendarDateToSqlDateString(endDate);
      query += " union ";
      query += selectQuery + " t.postDate as 'date' from forecastdatabase.transaction_split ts inner join " +
              "ForecastDatabase.Transaction t on ts.Transaction_idTransaction = " +
              "t.idTransaction where ts.BudgetItem_idBudgetItem = uuid_to_bin('" + budgetItem.getId() +"') and " +
              "t.authorizationDate is null and t.postDate >= " + Utility.calendarDateToSqlDateString(startDate) +
              " and t.postDate <= " + Utility.calendarDateToSqlDateString(endDate);
      query += " order by 4 asc";
      ResultSet rs = EntityInt.getRS(query, "while trying to get the splits for a budget item");
      return rs;
   }

   // Get a list of splits for a budget item in a period:
   public static  List<TransactionSplit> getSplitsListForBudgetItemInPeriod(BudgetItem budgetItem, Calendar startDate,
          Calendar endDate) throws RegisterException, SQLException, EntityException {
      List<TransactionSplit> splits = new ArrayList<>();
      ResultSet rs = getSplitsForBudgetItemInPeriod(budgetItem, startDate, endDate);
      while (rs.next()) {
         splits.add(new TransactionSplit(rs));
      }
      return splits;
   }

   // Get the splits associated with a budget item in the current month:
   public static  List<TransactionSplit> getSplitsListForBudgetItemMTD(BudgetItem budgetItem) throws SQLException,
           RegisterException, EntityException {
      Calendar startDate = Calendar.getInstance();
      startDate.set(DATE, 1);
      startDate.add(DATE, -3);
      Calendar endDate = Calendar.getInstance();
      return getSplitsListForBudgetItemInPeriod(budgetItem, startDate, endDate);
   }

}
