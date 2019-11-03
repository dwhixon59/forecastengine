package com.hixon.financial.model.forecast;

import com.hixon.financial.model.DependentEntity;
import com.hixon.financial.model.EntityException;
import com.hixon.financial.model.EntityInt;
import com.hixon.financial.model.register.TransactionSplit;

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
           "'idForecastTransaction', bin_to_uuid(Transaction_Split_idBudgetItem) as idBudgetItem, " +
           "bin_to_uuid(Transaction_Split_idTransaction) as 'idTransaction', disposition from " +
           "ForecastDatabase.Forecast_Transaction_Split ";

   // The insert query:
   public static final String insertQuery = "insert into ForecastDatabase.Forecast_Transaction_Split " +
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
   private static final String updateQuery = "update ForecastDatabase.Forecast_Transaction set ";
   @Override
   public String getUpdateQuery() {
      return updateQuery + "ForecastTransaction_idForecastTransaction = uuid_to_bin('" + idForecastTransaction + "'), " +
              "ForecastItem_idForecastItem = uuid_to_bin('" + idBudgetItem + "')Transaction_Split_idBudgetItem = " +
              "uuid_to_bin('" + idTransaction + "'), disposition = '" + disposition.name() + ")";
   }

   // The delete query:
   private static final String deleteQuery = "delete from ForecastDatabase.Forecast_Transaction where ";
   @Override
   public String getDeleteQuery() {
      return deleteQuery + "ForecastTransaction_idForecastTransaction = uuid_to_bin('" + idForecastTransaction + "'), " +
              "and ForecastItem_idForecastItem = uuid_to_bin('" + idBudgetItem + "')" +
              "and Transaction_Split_idBudgetItem = uuid_to_bin('" + idTransaction + "'))";
   }

   // The entity type attributes:
   @Override
   public String getEntityTypeName() {
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
   ForecastTransactionSplit(ForecastTransaction forecastTransaction, TransactionSplit split) {
      super();
      this.idForecastTransaction = forecastTransaction.getId();
      this.idBudgetItem = split.getIdBudgetItem();
      this.idTransaction = split.getIdTransaction();
      this.disposition = split.getDisposition();
      setDirty(true);
   }

   public ForecastTransactionSplit(ResultSet rs) throws SQLException {
      super();
      this.idForecastTransaction = UUID.fromString(rs.getString("idForecastTransaction"));
      this.idBudgetItem = UUID.fromString(rs.getString("idBudgetItem"));
      this.idTransaction = UUID.fromString(rs.getString("idTransaction"));
      this.disposition = SplitDisposition.valueOf(rs.getString("disposition"));
      setDirty(false);
   }


   /*
    * Helper methods:
    */


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

   // Get the count of Forecast Transaction Splits within the current Forecast for a Transaction Split:
   static int getForecastTransactionSplitsCount(TransactionSplit split, Forecast forecast) throws EntityException,
           SQLException, ForecastException {
      String query = "select count(*) from transaction_split a inner join forecast_transaction_split b on " +
              "a.BudgetItem_idBudgetItem = b.Transaction_Split_idBudgetItem and a. Transaction_idTransaction = " +
              "b.Transaction_Split_idTransaction inner join forecast_transaction c on " +
              "b.ForecastTransaction_idForecastTransaction = c.idForecastTransaction where a.BudgetItem_idBudgetItem =" +
              " uuid_to_bin('" + split.getIdBudgetItem() + "') and a.Transaction_idTransaction = uuid_to_bin('" +
              split.getIdTransaction() + "')";
      ResultSet rs = EntityInt.getSingletonRS(query, "trying to get the count of forecast transaction " +
              "splits");
      if (rs == null) throw new ForecastException("Null result on attempt to get count of forecast transaction splits");
      int i = rs.getInt(1);
      return rs.getInt(1);
   }

}
