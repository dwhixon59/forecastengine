package com.hixon.financialApp.model.forecast;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.budget.TransactionSplit;
import com.hixon.financialApp.model.entity.DependentEntity;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.register.Transaction;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class ForecastTransactionSplit extends DependentEntity {

   /*
    * Fields:
    */
   private UUID idForecastTransaction, idBudgetItem, idTransaction;

   // What to do with a split when attempting to match it to a forecast transaction:
   public enum SplitDisposition {ADJUST, ASSIGN, IGNORE, DISPUTE, ROLL_FORWARD, ZERO_OUT}
   SplitDisposition disposition;

   // The select query:
   public static final String selectQuery = "select bin_to_uuid(ForecastTransaction_idForecastTransaction) as " +
           "'fts.idForecastTransaction', bin_to_uuid(Transaction_Split_idBudgetItem) as 'fts.idBudgetItem', " +
           "bin_to_uuid(Transaction_Split_idTransaction) as 'fts.idTransaction', disposition as 'fts.disposition' " +
           "from forecast_transaction_split fts";

   public static String getSelectQuery() {
      return selectQuery;
   }

   // The insert query:
   public static final String insertQuery = "insert into forecast_transaction_split " +
           "(ForecastTransaction_idForecastTransaction, Transaction_Split_idBudgetItem, " +
           "Transaction_Split_idTransaction, disposition) values (";

   @Override
   public String getInsertQuery() {
      return insertQuery + "uuid_to_bin('" + idForecastTransaction + "'), uuid_to_bin('" + idBudgetItem + "'), " +
              "uuid_to_bin('" + idTransaction + "'), '" + disposition.name() + "')";
   }

   // The insert or update query:
   @Override
   public String getInsertOnDuplicateUpdateQuery() {
      return null;
   }

   // The update query:
   private static final String updateQuery = "update forecast_transaction set ";
   @Override
   public String getUpdateByIdQuery() {
      return updateQuery + "ForecastTransaction_idForecastTransaction = uuid_to_bin('" + idForecastTransaction + "'), " +
              "ForecastItem_idForecastItem = uuid_to_bin('" + idBudgetItem + "')Transaction_Split_idBudgetItem = " +
              "uuid_to_bin('" + idTransaction + "'), disposition = '" + disposition.name() + ")";
   }

   // The delete query:
   private static final String deleteQuery = "delete from forecast_transaction where ";
   @Override
   public String getDeleteByIdQuery() {
      return deleteQuery + "ForecastTransaction_idForecastTransaction = uuid_to_bin('" + idForecastTransaction + "'), " +
              "and ForecastItem_idForecastItem = uuid_to_bin('" + idBudgetItem + "')" +
              "and Transaction_Split_idBudgetItem = uuid_to_bin('" + idTransaction + "'))";
   }

   // The entity type attributes:
   @Override
   public String getPrintableTypeName() {
      return getPrintableTypeName_static();
   }

   public static String getPrintableTypeName_static() {
      return "forecast transaction split";
   }


   /*
    * Getters and Setters:
    */
   public UUID getIdForecastTransaction() {
      return idForecastTransaction;
   }
   public void setIdForecastTransaction(UUID idForecastTransaction) {
      this.idForecastTransaction = idForecastTransaction; setDirty(true);
   }
   public UUID getIdBudgetItem() {
      return idBudgetItem;
   }
   public void setIdBudgetItem(UUID idBudgetItem) {
      this.idBudgetItem = idBudgetItem; setDirty(true);
   }
   public UUID getIdTransaction() {
      return idTransaction;
   }
   public void setIdTransaction(UUID idTransaction) {
      this.idTransaction = idTransaction; setDirty(true);
   }
   public SplitDisposition getDisposition() {return disposition;}
   public void setDisposition(SplitDisposition disposition) {this.disposition = disposition; setDirty(true);}


   /*
    * Constructors:
    */
   public ForecastTransactionSplit(ForecastTransaction forecastTransaction, TransactionSplit split) {
      super();
      this.idForecastTransaction = forecastTransaction.getId();
      this.idBudgetItem = split.getIdBudgetItem();
      this.idTransaction = split.getIdTransaction();
      this.disposition = split.getDisposition();
      setDirty(true);
   }

   public ForecastTransactionSplit(ResultSet rs) throws SQLException {
      super();
      this.idForecastTransaction = UUID.fromString(rs.getString("fts.idForecastTransaction"));
      this.idBudgetItem = UUID.fromString(rs.getString("fts.idBudgetItem"));
      this.idTransaction = UUID.fromString(rs.getString("fts.idTransaction"));
      this.disposition = SplitDisposition.valueOf(rs.getString("fts.disposition"));
      setDirty(false);
   }


   /*
    * Helper methods:
    */
   /**
    * Validate the fields of an object.  Every entity is required to provide a method that validates the contents of
    * the entity.
    *
    * @return true if the object is valid
    */
   @Override
   public boolean isValid() { return true; }

   public ForecastTransaction getForecastTransaction() throws SQLException, EntityException, ForecastException {
      return ForecastTransaction.getById(idForecastTransaction);
   }
   public BudgetItem getBudgetItem() throws BudgetException, EntityException {
      return BudgetItem.getById(idBudgetItem);

   }
      public Transaction getTransaction() throws SQLException, EntityException {
      return Transaction.getById(idTransaction);

   }
    public TransactionSplit getTransactionSplit() throws SQLException, EntityException, RegisterException {
        return TransactionSplit.getbyId(idBudgetItem, idTransaction);
    }

   /*
    * Load and save methods:
    */
   public static ForecastTransactionSplit getById(UUID id, UUID idBudgetItem, UUID idTransaction) throws EntityException,
           SQLException {
         ResultSet rs = EntityInt.getSingletonRS(selectQuery + "where ForecastTransaction_idForecastTransaction " +
             "= uuid_to_bin('" + id + "') and Transaction_Split_idBudgetItem = uuid_to_bin('" + idBudgetItem + "') and " +
             "Transaction_Split_idTransaction = uuid_to_bin('" + idTransaction + "')", "trying to " +
             "retrieve a transaction split");
         return (rs != null) ? new ForecastTransactionSplit(rs) : null;
   }

   // Get the Forecast Transaction Split for a Transaction Split within the specified Forecast:
   public static ForecastTransactionSplit getForecastTransactionSplit(Forecast forecast, TransactionSplit split)
           throws EntityException, SQLException {
      String query = getSelectQuery() + " " +
              "inner join forecast_transaction ft on fts.ForecastTransaction_idForecastTransaction = ft.idForecastTransaction " +
              "inner join forecast_item fi on ft.ForecastItem_idForecastItem = fi.idForecastItem " +
              "where " +
                 "fts.Transaction_Split_idBudgetItem = uuid_to_bin('" + split.getIdBudgetItem() + "') and " +
                 "fts.Transaction_Split_idTransaction = uuid_to_bin('" + split.getIdTransaction() + "') and " +
                 "fi.Forecast_idForecast = uuid_to_bin('" + forecast.getId() + "')";
      ResultSet rs = EntityInt.getSingletonRS(query, "trying to get the forecast transaction split for a " +
              "split");
      ForecastTransactionSplit forecastTransactionSplit = null;
      if (rs != null) {
         forecastTransactionSplit = new ForecastTransactionSplit(rs);
      }
      return forecastTransactionSplit;
   }

}
