package com.hixon.financialApp.model.budget;

import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.utility.Utility;
import org.apache.commons.csv.CSVRecord;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.util.*;

public class BudgetItem extends Item {

   /*
    * Fields:
    */
   private static final String selectQuery = "select bin_to_uuid(idBudgetItem) as 'idBudgetItem', category, payee, period, " +
           "Budget_Item.amount, runningBalance, startDate, numberOfPayments, endDate, ItemType, howImportant, howOccurs, " +
           "howPaid, bin_to_uuid(Budget_idBudget) as 'idBudget' from ForecastDatabase.Budget_Item ";
   public static String getSelectQuery() {
      return selectQuery;
   }

   private static final String insertQuery = "insert into ForecastDatabase.Budget_Item (idBudgetItem, category, payee, " +
           "period, amount, runningBalance, startDate, numberOfPayments, endDate, itemType, howImportant, howOccurs, " +
           "howPaid, Budget_idBudget) values (";


   @Override
   public String getInsertQuery() throws BudgetException {

      return insertQuery + "uuid_to_bin('" + id + "'), \"" + category + "\", \"" + payee + "\", '" +
              generatePeriodType(period) + "', " + amount + ", " + runningBalance + ", " +
              Utility.calendarDateToSqlDateString(startDate) + ", " + numberOfPayments + ", " +
              Utility.calendarDateToSqlDateString(endDate) + ", '" + generateItemType(itemType) + "', '" +
              generateHowImportant(howImportant) + "', '" + generateHowOccurs(howOccurs) + "', '" +
              generateHowPaid(howPaid) + "', uuid_to_bin('" + idBudget + "'))";
   }

   private static final String updateQuery = "update ForecastDatabase.Budget_Item set ";

   private static final String deleteQuery = "delete from ForecastDatabase.Budget_Item where ";

   // Budget that this BudgetItem belongs to:
   protected UUID idBudget = null;

   // Column headers in an import file:
   public enum Headers {
      ID_BUDGET_ITEM, CATEGORY, PAYEE, PERIOD, AMOUNT, RUNNING_BALANCE, START_DATE, NUMBER_OF_PAYMENTS, END_DATE,
      ITEM_TYPE, HOW_IMPORTANT, HOW_OCCURS, HOW_PAID, ID_BUDGET
   }


   /*
    * Getters and setters:
    */
   public UUID getIdBudget() {
      return idBudget;
   }

   private void setIdBudget(UUID idBudget) {
      this.idBudget = idBudget;
      setDirty(true);
   }

   @Override
   public String getInsertOnDuplicateUpdateQuery() throws BudgetException {

      return insertQuery + "uuid_to_bin('" + id + "'), \"" + category + "\", \"" + payee + "\", '" +
              generatePeriodType(period) + "', " + amount + ", " + runningBalance + ", " +
              Utility.calendarDateToSqlDateString(startDate) + ", " + numberOfPayments + ", " +
              Utility.calendarDateToSqlDateString(endDate) + ", '" + generateItemType(itemType) + "', '" +
              generateHowImportant(howImportant) + "', '" + generateHowOccurs(howOccurs) + "', '" +
              generateHowPaid(howPaid) + "', uuid_to_bin('" + idBudget + "')) on duplicate key update " +
              "category = \"" + category + "\", payee = \"" + payee + "\", period = '" +
              generatePeriodType(period) + "', amount = " + amount + ", runningBalance = " + runningBalance +
              ", startDate = " + Utility.calendarDateToSqlDateString(startDate) + ", numberOfPayments = " +
              numberOfPayments + ", endDate = " + Utility.calendarDateToSqlDateString(endDate) + ", itemType = '" +
              generateItemType(itemType) + "', howImportant = '" + generateHowImportant(howImportant) + "', howOccurs = '"
              + generateHowOccurs(howOccurs) + "', howPaid = '" + generateHowPaid(howPaid) + "', Budget_idBudget = " +
              "uuid_to_bin('" + idBudget + "')";
   }

   @Override
   public String getUpdateByIdQuery() throws BudgetException {
      return updateQuery +  "category = '" + category + "', payee = \"" + payee + "\", period = '" +
              generatePeriodType(period) + "', amount = " + amount + ", runningBalance = " + runningBalance +
              ", startDate = " + Utility.calendarDateToSqlDateString(startDate) + ", numberOfPayments = " +
              numberOfPayments + ", endDate = " + Utility.calendarDateToSqlDateString(endDate) + ", itemType = '" +
              generateItemType(itemType) + "', howImportant = '" + generateHowImportant(howImportant) + "', howOccurs = '"
              + generateHowOccurs(howOccurs) + "', howPaid = '" + generateHowPaid(howPaid) + "', Budget_idBudget = " +
              "uuid_to_bin('" + idBudget + "') where idBudgetItem = uuid_to_bin('" + id + "')";
   }

   @Override
   public String getDeleteByIdQuery() {
      return deleteQuery;
   }

   @Override
   public String getPrintableEntityTypeName() {return "budget item";}


   // Constructors:
   public BudgetItem() {
      super(false);
      setDirty(false);
   }

   public BudgetItem(ResultSet rs) throws BudgetException {
      super(false);
      loadFromResultSet(rs);
      setDirty(false);
   }


   /*
    *  Load and save methods:
    */

   public static BudgetItem getById(UUID idBudgetItem) throws EntityException, BudgetException {
      return new BudgetItem(EntityInt.getRSById(selectQuery + "where idBudgetItem = ", idBudgetItem,
              "Database error encountered trying to retrieve a budget item."));
   }

   // Load up a budget item from a budget item database table row:
   public BudgetItem loadFromResultSet(ResultSet rs) throws BudgetException {
      try {
         if (rs == null) throw new BudgetException("Result set to loadFromResultSet from must not be null.");

         id = UUID.fromString(rs.getString("idBudgetItem"));
         category = rs.getString("category");
         payee = rs.getString("payee");
         period = parsePeriodType(rs.getString("period"));
         amount = rs.getDouble("amount");
         runningBalance = rs.getDouble("runningBalance");
         startDate.setTime(rs.getDate("startDate"));
         Date tempDate = rs.getDate("endDate");
         if (tempDate != null) {
            endDate = new GregorianCalendar();
            endDate.setTime(tempDate);
         }
         numberOfPayments = rs.getInt("numberOfPayments");
         itemType = parseItemType(rs.getString("ItemType"));
         howImportant = parseHowImportant(rs.getString("howImportant"));
         howOccurs = parseHowOccurs(rs.getString("howOccurs"));
         howPaid = parseHowPaid(rs.getString("howPaid"));
         idBudget = UUID.fromString(rs.getString("idBudget"));
         setDirty(false);

      } catch (SQLException e) {

         BudgetException be = new BudgetException("Error reading in the Budget Item row.\n" + this.toString());
         be.initCause(e);
         throw (be);
      }
      return this;
   }  // End loadFromResultSet().


   public static BudgetItem getByPayee(String payee) throws BudgetException {
      String query = selectQuery + "where payee = \"" + payee + "\"";
      try {
         Statement statement = Utility.getDbConnection().createStatement();
         ResultSet rs = statement.executeQuery(query);
         BudgetItem budgetItem = null;
         if (rs.next()) {
            budgetItem = new BudgetItem(rs);
         }
         return budgetItem;

      } catch (SQLException e) {
         BudgetException be = new BudgetException("Database error occurred trying to get the budget item for " +
                 "payee " + payee);
         be.initCause(e);
         throw be;
      }
   }

   // Get the payee for a budget item using it's arbitrary ID:
   public static String getPayeeById(UUID idBudgetItem) throws BudgetException {

      if (idBudgetItem == null) {
         throw new BudgetException("Budget item ID may not be null in call to getPayeeById(idBudgetItem).");
      }

      String query = "select payee from ForecastDatabase.Budget_Item where idBudgetItem = uuid_to_bin('" +
              idBudgetItem + "')";
      try {
         Statement statement = Utility.getDbConnection().createStatement();
         ResultSet rs = statement.executeQuery(query);
         if (rs.next()) {
            return rs.getString("payee");
         }
         return null;

      } catch (SQLException e) {
         BudgetException be = new BudgetException("Database error occurred trying to get the budget item for " +
                 "id " + idBudgetItem);
         be.initCause(e);
         throw be;
      }
   }


   // Load a budget item from a comma separated values string:
   public void loadFromCsvRecord(CSVRecord record) throws BudgetException, ParseException {

      if (record.size() < 14) throw new BudgetException("Less than 14 values submitted for new budget item");
      setId(UUID.fromString(record.get(Headers.ID_BUDGET_ITEM)));
      setCategory(record.get(Headers.CATEGORY));
      setPayee(record.get(Headers.PAYEE));
      setPeriod(parsePeriodType(record.get(Headers.PERIOD)));
      setAmount(Double.parseDouble(record.get(Headers.AMOUNT)));
      setRunningBalance(Double.parseDouble(record.get(Headers.RUNNING_BALANCE)));
      Calendar tempDate = Calendar.getInstance();
      tempDate.setTime(sdfMDY.parse(record.get(Headers.START_DATE)));
      setStartDate(tempDate);
      setNumberOfPayments(Integer.parseInt(record.get(Headers.NUMBER_OF_PAYMENTS)));
      if (record.get(Headers.END_DATE) != null &&
              !record.get(Headers.END_DATE).isEmpty() &&
              record.get(Headers.END_DATE).equalsIgnoreCase("null")) {
         tempDate.setTime(sdfMDY.parse(record.get(Headers.END_DATE)));
      } else {
         tempDate = null;
      }
      setEndDate(tempDate);
      setItemType(parseItemType(record.get(Headers.ITEM_TYPE)));
      setHowImportant(parseHowImportant(record.get(Headers.HOW_IMPORTANT)));
      setHowOccurs(parseHowOccurs(record.get(Headers.HOW_OCCURS)));
      setHowPaid(parseHowPaid(record.get(Headers.HOW_PAID)));
      setIdBudget(UUID.fromString(record.get(Headers.ID_BUDGET)));

      System.out.println("Created new budget item " + toString());
      setDirty(true);
   }


   // Load a budget item from a comma separated values string entered by a user:
   public static BudgetItem loadFromUserCSV(String csvLine) throws BudgetException, ParseException, SQLException, EntityException {

      String[] values = csvLine.split(",");
      BudgetItem budgetItem = new BudgetItem();
      if (values.length < 13) throw new BudgetException("Less than 13 values submitted for new budget item");
      budgetItem.setId(UUID.randomUUID());
      budgetItem.setCategory(values[0]);
      budgetItem.setPayee(values[1]);
      budgetItem.setPeriod(parsePeriodType(values[2]));
      budgetItem.setAmount(Double.parseDouble(values[3]));
      budgetItem.setRunningBalance(Double.parseDouble(values[4]));
      Calendar tempDate = Calendar.getInstance();
      tempDate.setTime(sdfMDY.parse(values[5]));
      budgetItem.setStartDate(tempDate);
      budgetItem.setNumberOfPayments(Integer.parseInt(values[6]));
      if (values[7] != null && !values[7].isEmpty() && !values[7].equalsIgnoreCase("null")) {
         tempDate.setTime(sdfMDY.parse(values[7]));
      } else {
         tempDate = null;
      }
      budgetItem.setEndDate(tempDate);
      budgetItem.setItemType(parseItemType(values[8]));
      budgetItem.setHowImportant(parseHowImportant(values[9]));
      budgetItem.setHowOccurs(parseHowOccurs(values[10]));
      budgetItem.setHowPaid(parseHowPaid(values[11]));
      Budget budget = Budget.getByName(values[12]);
      budgetItem.setIdBudget(budget.getId());

      System.out.println("Created new budget item " + budgetItem.toString());
      budgetItem.setDirty(true);
      return budgetItem;
   }


   // Save this budget item:
   @Override
   public void save(SaveMethod method) throws BudgetException, SQLException, EntityException, RegisterException, ForecastException {

      // Save this budget item:
      super.save(method);

      // Mark all the forecasts that use the budget this item belongs to as out of sync with the budget:
      String updateInSyncQuery = "update forecastdatabase.forecast set inSync = 0 where Budget_idBudget = " +
              "uuid_to_bin('" + idBudget + "')";
      EntityInt.executeUpdate(updateInSyncQuery, "Database error attempting to set the " +
              "inSync flag on the forecast.");
    }

   public void update() throws BudgetException, SQLException {

      String query = updateQuery + "category = '" + category + "', payee = \"" + payee + "\", period = '" +
              generatePeriodType(period) + "', amount = " + amount + "', runningBalance = " + runningBalance +
              ", startDate = " + Utility.calendarDateToSqlDateString(startDate) + ", numberOfPayments = " +
              numberOfPayments + ", endDate = " + Utility.calendarDateToSqlDateString(endDate) + ", itemType = '" +
              generateItemType(itemType) + "', howImportant = '" + generateHowImportant(howImportant) + " howOccurs = '"
              + generateHowOccurs(howOccurs) + " howPaid = '" + generateHowPaid(howPaid) + ", Budget_idbudget = " +
              "uuid_to_bin('" + idBudget + "') where idBudgetItem = uuid_to_bin('" + id + "')";

      System.out.println(query);

      Statement statement = null;
      try {
         statement = Utility.getDbConnection().createStatement();
         int rowCount = statement.executeUpdate(query);
         if (rowCount == 0) {
            throw new BudgetException("Update of budget item couldn't find the item to update.");
         }
         setDirty(false);
      } catch (SQLException e) {
         System.out.println();
         if (statement != null) statement.close();
         BudgetException be = new BudgetException("Database error attempting to update a budget item.");
         be.initCause(e);
         throw be;
      }
   }

   // Get an alphabetical list of all the budget items:
   public static ResultSet getAllBudgetItems() throws EntityException {

      String query = getSelectQuery() + " order by category, payee";
      return EntityInt.getRS(query, "getting the budget items for a MTD spending report");
   }

   // Get a list of the items of interest for a specific user:
   public static List<BudgetItem> getItemsOfInterest(User user) throws EntityException, SQLException, BudgetException {
      List<BudgetItem> items = new ArrayList<>();

      String query = getSelectQuery() + "where user = '" + user + "' order by category asc, payee asc";
      ResultSet rs = EntityInt.getRS(query, " while retrieving a list of items of interest for the user "
              + user + ".");
      while (rs.next()) {
         items.add(new BudgetItem(rs));
      }

      return items;
   }
}
