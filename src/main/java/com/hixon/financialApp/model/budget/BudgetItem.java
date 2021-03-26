package com.hixon.financialApp.model.budget;

import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.register.TransactionSplit;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.utility.Utility;
import org.apache.commons.csv.CSVRecord;
import org.jetbrains.annotations.Contract;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

public class BudgetItem extends Item {

   /*
    * Fields:
    */
   private static final String selectColumns = "bin_to_uuid(idBudgetItem) as 'idBudgetItem', category, payee, period, " +
           "budget_item.amount, runningBalance, startDate, numberOfPayments, endDate, ItemType, howImportant, howOccurs, " +
           "howPaid, bin_to_uuid(Budget_idBudget) as 'idBudget' ";
   public static String getSelectColumns() {
      return selectColumns;
   }

   private static final String selectQuery = "select " + getSelectColumns() + "from budget_item ";
   public static String getSelectQuery() {
      return selectQuery;
   }

   private static final String insertQuery = "insert into budget_item (idBudgetItem, category, payee, " +
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

   private static final String updateQuery = "update budget_item set ";

   private static final String deleteQuery = "delete from budget_item where ";

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
         startDate = Utility.localDateToCalendarDate(rs.getObject("startDate", LocalDate.class));
         endDate = Utility.localDateToCalendarDate(rs.getObject("endDate", LocalDate.class));
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

      String query = "select payee from budget_item where idBudgetItem = uuid_to_bin('" +
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
      String updateInSyncQuery = "update forecast set inSync = 0 where Budget_idBudget = " +
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

   /*
    * Main methods:
    */
   // Get an alphabetical list of all the budget items:
   public static ResultSet getAllBudgetItems() throws EntityException {

      String query = getSelectQuery() + " order by category, payee";
      return EntityInt.getRS(query, "getting the budget items for a MTD spending report");
   }

   /**
    * Get a result set consisting of all the budget items that have not expired as of the specified date.  This
    * method is useful to get a filtered list of budget items that does not include any of the ones that are no
    * longer in use.
    *
    * @param date The for which the budget items must be valid (unexpired).
    * @return A result set of budget items that does not include any expired budget items.
    * @throws EntityException
    */
   public static ResultSet getAllUnexpiredBudgetItems(Calendar date) throws EntityException {

      String query = getSelectQuery() + " where endDate is null or endDate >= " +
              Utility.calendarDateToSqlDateString(date) + "order by category, payee";
      return EntityInt.getRS(query, "getting the budget items for a MTD spending report");

   }

   /**
    * Get a list of budget items joined with their splits and transactions that are instances of them.
    *
    * @param startDate The result set will contain only the items that have not expired as of this date and only
    *                  splits associated with transactions that occurred on or after this date.
    * @return ResultSet containing the joined items and splits.
    */
   public static ResultSet getBudgetItemsWithSplits(Calendar startDate) throws EntityException {

      ResultSet rs = null;

      String query = "select " + getSelectColumns() + ", " + TransactionSplit.getSelectColumns() + " " +
              "from budget_item " +
              "right outer join transaction_split on bi.idBudgetItem = ts.BudgetItem_idBudgetItem " +
              "right outer join transaction on ts.Transaction_idTransaction = tr.idTransaction" +
              "where bi.endDate = null or bi.endDate >= " + Utility.calendarDateToSqlDateString(startDate) +
              "order by bi.category + bi.payee";
      EntityInt.getRS(query, "retrieve a list of budget items joined with their splits and transactions");

      return rs;
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


   /**
    * Get the amount of money budgeted for this budget item in the current month.
    *
    * @return The amount of money budgeted for this item in the current month.
    */
   public double getBudgetedAmountForCurrentMonth() throws ForecastException {
      Calendar month = Calendar.getInstance();
      return getBudgetedAmountForMonth(month);
   }


   /**
    * Get the amount of money budgeted for this budget item in a given month.
    *
    * @param month The month to compute the budgeted amount for.  It does not matter what the date of the month is
    *              set to.
    * @return The amount of money budgeted for this budget item in a given month.
    */
   @Contract(pure = true)
   public double getBudgetedAmountForMonth(Calendar month) throws ForecastException {

      // Set the start date for the period to the first day of the month passed in:
      Calendar monthStartDate = (Calendar) month.clone();
      monthStartDate.set(Calendar.DATE, 1);

      // Set the end date for the period to the last day of the month passed in:
      Calendar monthEndDate = (Calendar) month.clone();
      int lastDayOfMonth = monthEndDate.getActualMaximum(Calendar.DATE);
      monthEndDate.set(Calendar.DATE, lastDayOfMonth);

      // Get the budgeted amount for the date range matching the specified month:
      return getBudgetedAmountInPeriod(monthStartDate, monthEndDate);
   }

   /**
    * Get the amount of money budgeted for this budget item in a period (date range).
    *
    * @param periodStartDate Staring date of the period to get the total amount for.
    * @param periodEndDate Ending date of the period to get the total amount for.
    * @return
    */
   public double getBudgetedAmountInPeriod(Calendar periodStartDate, Calendar periodEndDate) throws ForecastException {

      // Get the date of the first time this budget item would occur in the given period:
      Calendar nextDate = getFirstDateInWindow(periodStartDate, periodEndDate);

      // While there would be more occurrences of the budget item in the period, total them up:
      double total = 0;
      while (nextDate != null && periodEndDate.after(nextDate)){
         total += getAmount();
         nextDate = getNextDateOnOrBefore(nextDate, periodEndDate);
      }

      return total;
   }

   /**
    * Get the total amount spent on a budget item month-to-date:
    */
   public double getAmountSpentMTD() throws EntityException, SQLException,
           RegisterException {

      // Get a list of the splits for this budget item month-to-date:
      List<TransactionSplit> splits = TransactionSplit.getSplitsListForBudgetItemMTD(this);

      // Total the amounts of the splits:
      double total = 0;
      for (TransactionSplit split: splits
           ) {
         total += split.getAmount();
      }

      return total;
   }

}
