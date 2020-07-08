package com.hixon.financialApp.model.forecast;

import com.hixon.financialApp.controller.QuitException;
import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.budget.Item;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.IndependentEntity;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.utility.Utility;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Objects;
import java.util.UUID;

import static com.hixon.financialApp.model.entity.EntityInt.SaveMethod.INSERT;
import static com.hixon.financialApp.model.entity.EntityInt.*;
import static java.util.Calendar.MONTH;

public class Forecast extends IndependentEntity {

   /*
    * Forecast class fields:
    */
   protected String description;
   protected Calendar startDate;
   protected Calendar dateGenerated;
   protected double startingBalance;
   protected Calendar endDate;
   protected double minimumBalance;
   protected double endingBalance;
   protected int numberOfMonths;
   protected String budgetName;
   protected UUID idBudget;
   protected ForecastTransaction[] transactions = null;
   private ForecastItem firstForecastItem = null;
   private ForecastItem lastForecastItem = null;
   private ForecastTransaction firstSignificantEvent = null;
   private ForecastTransaction lastSignificantEvent = null;
   private boolean inSync = true;
   private static final String selectQuery = "select bin_to_uuid(idForecast) as idForecast, description, " +
           "dateGenerated, startDate, startingBalance, endDate, endingBalance, numberOfMonths, " +
           "bin_to_uuid(Budget_idBudget) as idBudget from forecastdatabase.forecast ";

   public static String getSelectQuery() {
      return selectQuery;
   }

   private static final String insertQuery = "insert into forecastdatabase.forecast (idForecast, description, " +
           "dateGenerated, startDate, startingBalance, endDate, endingBalance, numberOfMonths, Budget_idBudget) " +
           "values (";
   private static final String updateQuery = "update ForecastDatabase.Forecast set ";
   private static final String deleteQuery = "delete from ForecastDatabase.Forecast where ";

   // The types of significant events that can be generated:
   public enum SignificantEvents {daysBelowMinimumBalance}


   /*
    * Forecast class getters and setters:
    */
   public String getDescription() {
      return description; }

   public Calendar getStartDate() {
      return startDate;
   }

   public Calendar getEndDate() {
      return endDate;
   }

   public double getStartingBalance() {
      return startingBalance;
   }

   public double getMinimumBalance() {
      return minimumBalance;
   }

   public double getEndingBalance() {
      return endingBalance;
   }

   public int getNumberOfMonths() {
      return numberOfMonths;
   }

   ForecastTransaction[] getTransactions() {
      return transactions;
   }

   public ForecastTransaction getFirstSignificantEvent() {
      return firstSignificantEvent;
   }

   public boolean getInSync() {
      return inSync;
   }

   public void setInSync(boolean inSync) {
      this.inSync = inSync;
      setDirty(true);
   }

   @Override
   public String getInsertQuery() {
      return insertQuery + "uuid_to_bin('" + id + ", " + description + ", " +
              Utility.calendarDateToSqlDateString(dateGenerated) + ", " +
              Utility.calendarDateToSqlDateString(startDate) + ", " + startingBalance +
              Utility.calendarDateToSqlDateString(endDate) + ", " + endingBalance + ", " + numberOfMonths + ", " +
              " uuid_to_bin('" + idBudget + "')";
   }

   @Override
   public String getInsertOnDuplicateUpdateQuery() {
      return null;
   }

   @Override
   public String getUpdateByIdQuery() {
      return updateQuery + "description = '" + description + "', " +
              "dateGenerated = " + Utility.calendarDateToSqlDateString(dateGenerated) + ", startDate = " +
              Utility.calendarDateToSqlDateString(startDate) + ", startingBalance = " + startingBalance + ", " +
              "endDate = " + Utility.calendarDateToSqlDateString(endDate) + ", endingBalance = " + endingBalance +
              ", numberOfMonths = " + numberOfMonths + ", Budget_idBudget = uuid_to_bin('" + idBudget + "') " +
              "where idForecast = uuid_to_bin('" + id + "')";
   }

   @Override
   public String getDeleteByIdQuery() {
      return deleteQuery + "id = uuid_to_bin('" + id + "')";
   }

   @Override
   public String getPrintableEntityTypeName() {
      return "forecast";
   }


   /*
    * Constructors:
    */
   public Forecast(String budgetName, Calendar startDate, int numberOfMonths, double startingBalance,
                   double minimumBalance) throws ForecastException, SQLException {

      super(true);
      if (startDate == null) {
         this.startDate = Calendar.getInstance();
         this.startDate.set(Calendar.DAY_OF_MONTH, 1);
      } else {
         this.startDate = startDate;
      }
      this.startingBalance = startingBalance;
      this.endDate = (Calendar) Objects.requireNonNull(startDate).clone();
      this.endDate.add(Calendar.MONTH, numberOfMonths);
      // Subtract off one day because n months after June 1st is June 1st, but we only want to go to May 31st, etc.:
      this.endDate.add(Calendar.DATE, -1);
      this.minimumBalance = minimumBalance;
      this.endingBalance = 0;
      this.numberOfMonths = numberOfMonths;
      this.budgetName = budgetName;

      // Find the ID of the named budget:
      PreparedStatement preparedStmt = null;
      ResultSet rs = null;
      try {
         String query = "select bin_to_uuid(idBudget) from ForecastDatabase.Budget where name = ?";
         preparedStmt = Utility.getDbConnection().prepareStatement(query);
         preparedStmt.setString(1, budgetName);
         rs = preparedStmt.executeQuery();
         if (rs != null && rs.next()) {
            this.idBudget = UUID.fromString(rs.getString(1));
         } else {
            throw new ForecastException("Budget named " + budgetName + " not found in the database.");
         }
      } catch (SQLException e) {
         System.out.println("[SEVERE]  SQL error encountered trying to retrieve the budget ID.");
         if (preparedStmt != null) preparedStmt.close();
         if (rs != null) rs.close();
         throw e;
      }
   }

   // Create a forecast from the database:
   Forecast(ResultSet rs) throws SQLException {
      super(false);
      this.id = UUID.fromString(rs.getString("idForecast"));
      this.description = rs.getString("description");
      this.dateGenerated = Utility.SqlDateToCalendarDate(rs.getDate("dateGenerated"));
      this.startDate = Utility.SqlDateToCalendarDate(rs.getDate("startDate"));
      this.startingBalance = rs.getDouble("startingBalance");
      this.endDate = Utility.SqlDateToCalendarDate(rs.getDate("endDate"));
      this.endingBalance = rs.getDouble("endingBalance");
      this.numberOfMonths = rs.getInt("numberOfMonths");
      this.idBudget = UUID.fromString(rs.getString("idBudget"));
   }

   /*
    * CRUD methods:
    */
   public static Forecast getMostRecent() throws EntityException, SQLException {
      String selectMostRecentQuery = selectQuery + "order by dateGenerated desc";
      ResultSet rs = getSingletonRS(selectMostRecentQuery, "Database error occurred " +
              "trying to retrieve the most recent forecast.");
      Forecast forecast = null;
      if (rs != null) {
         forecast = new Forecast(rs);
      }
      return forecast;
   }

   //  Save the forecast object:
   public void save() throws SQLException {
      Connection dbConnection = Utility.getDbConnection();
      PreparedStatement preparedStmt = null;
      String errorMessage = null;
      try {
         // Insert the forecast tuple:
         errorMessage = "SQL error attempting to insert the Forecast object into the database.";
         String query = "insert into ForecastDatabase.Forecast (idForecast, description, dateGenerated, " +
                 "startDate, startingBalance, endDate, endingBalance, numberOfMonths, inSync, Budget_idBudget) " +
                 "values(UUID_TO_BIN(?), ?, ?, ?, ?, ?, ?, ?, ?, UUID_TO_BIN(?)) on duplicate key update " +
                 "description = ?, dateGenerated = ?, startDate =?, startingBalance = ?, endDate = ?, " +
                 "endingBalance = ?, numberOfMonths = ?, inSync = ?";
         preparedStmt = dbConnection.prepareStatement(query);
         preparedStmt.setString(1, id.toString());
         preparedStmt.setString(2, "Test Forecast for Bill Pay Account");
         preparedStmt.setObject(3, new java.sql.Timestamp(System.currentTimeMillis()));
         preparedStmt.setDate(4, new java.sql.Date(startDate.getTimeInMillis()));
         preparedStmt.setDouble(5, startingBalance);
         preparedStmt.setDate(6, new java.sql.Date(endDate.getTimeInMillis()));
         preparedStmt.setDouble(7, endingBalance);
         preparedStmt.setInt(8, numberOfMonths);
         preparedStmt.setBoolean(9, true);
         preparedStmt.setString(10, idBudget.toString());
         preparedStmt.setString(11, "Test Forecast for Bill Pay Account");
         preparedStmt.setObject(12, new java.sql.Timestamp(System.currentTimeMillis()));
         preparedStmt.setDate(13, new java.sql.Date(startDate.getTimeInMillis()));
         preparedStmt.setDouble(14, startingBalance);
         preparedStmt.setDate(15, new java.sql.Date(endDate.getTimeInMillis()));
         preparedStmt.setDouble(16, endingBalance);
         preparedStmt.setInt(17, numberOfMonths);
         preparedStmt.setBoolean(18, true);
         preparedStmt.execute();

      } catch (SQLException e) {
         System.out.println(errorMessage);
         if (preparedStmt != null) preparedStmt.close();
         throw e;
      }
   } // End save().

   // Save the forecast items to the database:
   public void saveForecastItems() throws SQLException, BudgetException {
      Connection dbConnection = Utility.getDbConnection();
      PreparedStatement preparedStmt = null;
      String errorMessage = null;
      try {
          // Insert the forecast item tuples:
         errorMessage = "SQL error attempting to insert a forecast item into the database.";
         String query = "insert into ForecastDatabase.Forecast_Item (idForecastItem, category, payee, period, amount, " +
                 "startDate, numberOfPayments, endDate, itemType, howImportant, howOccurs, howPaid," +
                 "Forecast_idForecast, BudgetItem_idBudgetItem) values (UUID_TO_BIN(?), ?, ?, ?, ?, ?, ?, ?, ?, ?," +
                 " ?, ?, UUID_TO_BIN(?), UUID_TO_BIN(?))";
         preparedStmt = dbConnection.prepareStatement(query);
         ForecastItem forecastItem = firstForecastItem;
         while (forecastItem != null) {
            preparedStmt.setString(1, forecastItem.getId().toString());
            preparedStmt.setString(2, forecastItem.getCategory());
            preparedStmt.setString(3, forecastItem.getPayee());
            preparedStmt.setString(4, Item.generatePeriodType(forecastItem.getPeriod()));
            preparedStmt.setDouble(5, forecastItem.getAmount());
            preparedStmt.setDate(6, Utility.calendarDateToSqlDate(forecastItem.getStartDate()));
            preparedStmt.setInt(7, forecastItem.getNumberOfPayments());
            preparedStmt.setDate(8, Utility.calendarDateToSqlDate(forecastItem.getEndDate()));
            preparedStmt.setString(9, Item.generateItemType(forecastItem.getItemType()));
            preparedStmt.setString(10, Item.generateHowImportant(forecastItem.getHowImportant()));
            preparedStmt.setString(11, Item.generateHowOccurs(forecastItem.getHowOccurs()));
            preparedStmt.setString(12, Item.generateHowPaid(forecastItem.getHowPaid()));
            preparedStmt.setString(13, id.toString());
            preparedStmt.setString(14, forecastItem.getIdBudgetItem().toString());
            preparedStmt.execute();
            forecastItem = forecastItem.getNextForecastItem();
         }

      } catch (SQLException | BudgetException e) {
         System.out.println(errorMessage);
         if (preparedStmt != null) preparedStmt.close();
         throw e;
      }
   } // End saveForecastItems().

   // Save the forecast transactions to the database:, including all the forecast items and forecast transactions:
   public void saveForecastTransactions() throws SQLException, BudgetException, EntityException, ForecastException {
      Connection dbConnection = Utility.getDbConnection();
      PreparedStatement preparedStmt = null;
      String errorMessage = null;
      try {
         // Insert the forecast transaction tuples:
         errorMessage = "SQL error attempting to insert a forecast transaction into the database.";
         String query = "insert into ForecastDatabase.Forecast_Transaction (idForecastTransaction, remainingAmount, " +
                 "plannedDate, firstOccurrence, ForecastItem_idForecastItem) values (UUID_TO_BIN(?), ?, ?, ?, " +
                 "UUID_TO_BIN(?))";
         preparedStmt = dbConnection.prepareStatement(query);
         for (ForecastTransaction transaction : this.transactions) {
            if (transaction != null) {
               ForecastTransaction forecastTransaction = transaction;
               while (forecastTransaction != null) {
                  preparedStmt.setString(1, forecastTransaction.getId().toString());
                  preparedStmt.setDouble(2, forecastTransaction.getRemainingAmount());
                  preparedStmt.setDate(3, new java.sql.Date(forecastTransaction.getPlannedDate().getTimeInMillis()));
                  preparedStmt.setBoolean(4, forecastTransaction.isFirstOccurrence());
                  preparedStmt.setString(5, forecastTransaction.getForecastItem().getId().toString());
                  preparedStmt.execute();
                  forecastTransaction = forecastTransaction.getNextTransaction();
               }
            }
         }

      } catch (SQLException | BudgetException | EntityException | ForecastException e) {
         System.out.println(errorMessage);
         if (preparedStmt != null) preparedStmt.close();
         throw e;
      }
   } // End saveForecastTransactions().

   // Save the entire forecast to the database, including all the forecast items and forecast transactions:
   public void saveAll() throws SQLException, BudgetException, EntityException, ForecastException {

      // Save the forecast:
      save();
      saveForecastItems();
      saveForecastTransactions();

   } // End saveAll().

   /*
    * Update the long term forecast, which means regenerate the portion of the forecast from the update start date
    * (usually the first day of the next month) to the end of the forecast window (defaults to 12 months), which
    * likely results in extending the forecast:
   */
   public void updateForecast() throws Exception, EntityException, BudgetException, QuitException, RegisterException {

      // Get the starting date of the forecast to update:
      Calendar startDate = Utility.askStartDate();

      /*
       Update up the end date so that the forecast window will be the same number of months as it was originally
       set to be:
      */
      endDate = (Calendar) startDate.clone();
      endDate.add(MONTH, numberOfMonths);

      // Update all the forecast items in the forecast from the current budget items:
      String query = "update ForecastDatabase.Forecast_Item fi inner join ForecastDatabase.Budget_Item bi on " +
              "fi.BudgetItem_idBudgetItem = bi.idBudgetItem set fi.category = bi.category, fi.payee = bi.payee, " +
              "fi.period = bi.period, fi.amount = bi.amount, fi.startDate = bi.startDate, fi.numberOfPayments = " +
              "bi.numberOfPayments, fi.endDate = bi.endDate, fi.itemType = bi.itemType, fi.howImportant =" +
              " bi.howImportant, fi.howOccurs = bi.howOccurs, fi.howPaid = bi.howPaid where fi.Forecast_idForecast =" +
              " uuid_to_bin('" + id + "')";
      executeUpdate(query, "updating the forecast items from the budget items");

      // Get a list of budget items that weren't included in the forecast because they didn't exist when the forecast
      // was created:
      query = BudgetItem.getSelectQuery() + "where idBudgetItem not in (select distinct BudgetItem_idBudgetItem from " +
              "ForecastDatabase.Forecast_Item)";
      ResultSet rs = getRS(query, "retrieving the budget items not included in the forecast");

      // Insert any new forecast items that weren't originally included:
      ForecastItem forecastItem;
      while (rs.next()) {
         forecastItem = new ForecastItem(this, rs);
         forecastItem.save(INSERT);
      }

      // First clean up the old transactions (prior to the month before the current month:
      // TODO: clean up old transactions.

      // Now set the first occurrence of every forecast transaction to "first occurrence":
      // TODO:  set all the forecast transactions to "not the first occurrence".
      // TODO:  set the first occurrence of every forecast transaction to "first occurence".

      // We don't have to delete any forecast items that reference budget items that no longer exist, because the ones
      // after the update start date will be deleted with all the other forecast items and not regenerated because
      // they don't have budget items to regenerate them from.  If they occur before the update start date, then we
      // shouldn't mess with them:

      // Delete all of the forecast transactions that occur after the update start date:
      query = ForecastTransaction.getDeleteQuery() + "where plannedDate >= " +
              Utility.calendarDateToSqlDateString(startDate);
      executeUpdate(query, "deleting all the forecast transactions after " +
              Utility.calendarDateToStringDate(startDate));

      // Generate the updated portion of the forecast starting on start date:
      this.transactions = new ForecastTransaction[numberOfMonths * 31];
      ForecastEngine forecastEngine = new ForecastEngine();
      forecastEngine.generateForecastTransactions(this, startDate);

      // Save the updated portion of the forecast:
      save();
      saveForecastTransactions();

   } // End Forecast.update().


   /*
    *  Helper methods:
    */
   public void createTransactionsArray() {
      this.transactions = new ForecastTransaction[numberOfMonths * 31];
   }


   /*
    * Forecast class main methods:
    */
   // Determine if a date falls within the forecast window of this forecast object:
   public boolean fallsWithinForecastWindow(Calendar date) {
      boolean decision = false;

      if (date != null) {
         decision = date.compareTo(startDate) >= 0 && date.compareTo(endDate) <= 0;
      }

      return decision;
   }

   // Add a forecast transaction to the transaction array on the date that it is expected to occur:
   public void addTransactionOnDate(ForecastItem forecastItem, Calendar startDate, Calendar nextDate,
                                    boolean firstOccurrence) throws Exception {

      // Calculate the index to assign this transaction by calculating the number of days between
      // the start date of the forecast and the day this transaction occurs:
      int index = (int) ChronoUnit.DAYS.between(startDate.toInstant(), nextDate.toInstant());

      if (index < 0 || index > this.transactions.length) {
         throw new ForecastException("Invalid index into the forecast transactions array:  " + index);
      }

      if (index < this.transactions.length) {

         // Create a new forecast transaction (occurrence of a forecast item);
         ForecastTransaction forecastTransaction = new ForecastTransaction(forecastItem, nextDate, firstOccurrence);

         // If this is the first transaction on that date:
         if (this.transactions[index] == null) {
            this.transactions[index] = forecastTransaction;
         } else {
            // else add this transaction to the end of the list of transactions for this day:
            ForecastTransaction linkedTransaction = this.transactions[index];
            while (linkedTransaction.getNextTransaction() != null)
               linkedTransaction = linkedTransaction.getNextTransaction();
            linkedTransaction.setNextTransaction(forecastTransaction);
         }
         System.out.println("Adding transaction " + forecastItem.getPayee() + " to the forecast at index " + index);
      } else {
         throw new Exception("Forecast.addTransaction:  date not in range of forecast.");
      }
   }

   // Generate the summary and significant events list:
   public void summarize() throws EntityException, SQLException, ForecastException, BudgetException {

      // Traverse the forecast sequentially from the first day to the last day:
      for (ForecastTransaction transaction : this.transactions) {
         if (transaction != null) {
            ForecastTransaction forecastTransaction = transaction;
            while (forecastTransaction != null) {
               // Update the ending balance in the forecast and the running balance in the transaction:
               endingBalance += forecastTransaction.getForecastItem().getAmount();
               forecastTransaction.setRunningBalance(endingBalance);
               if (forecastTransaction.getNextTransaction() == null)
                  System.out.println(forecastTransaction.toString());

               // If this is the last transaction on the current day, and the balance dips below the specified
               // minimum balance, then add this transaction to the significant events list:
               if (forecastTransaction.getNextTransaction() == null && endingBalance < minimumBalance) {
                  addSignificantEvent(forecastTransaction);
               }
               forecastTransaction = forecastTransaction.getNextTransaction();
            }
         }
      }
   }

   // Add a transaction to the significant events list:
   public void addSignificantEvent(ForecastTransaction forecastTransaction) {
      if (firstSignificantEvent == null) {
         firstSignificantEvent = forecastTransaction;
      } else {
         lastSignificantEvent.setNextSignificantEvent(forecastTransaction);
      }
      lastSignificantEvent = forecastTransaction;
   }

   // Add an item to the linked list of items in the forecast:
   public void addForecastItem(ForecastItem forecastItem) {
      if (firstForecastItem == null) {
         firstForecastItem = forecastItem;
      } else {
         lastForecastItem.setNextForecastItem(forecastItem);
      }
      lastForecastItem = forecastItem;
   }


   /*
    *  Main methods:
    */

}
