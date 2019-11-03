package com.hixon.financial.model.forecast;

import com.hixon.financial.Utility;
import com.hixon.financial.model.EntityException;
import com.hixon.financial.model.EntityInt;
import com.hixon.financial.model.budget.BudgetException;
import com.hixon.financial.model.budget.BudgetItem;
import com.hixon.financial.model.budget.Item;
import com.sun.istack.internal.NotNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.UUID;

import static java.lang.Math.abs;

public class ForecastItem extends Item {

   /*
    * Fields:
    */
   // The forecast this item is a part of:
   protected Forecast forecast = null;

   protected UUID idForecast = null;

   // The BudgetItem this ForecastItem was created from:
   protected UUID idBudgetItem = null;

   // Last computed date this budget item will occur (used to find the next time it will occur):
   protected Calendar nextDate = new GregorianCalendar();

   // Next forecast item when included in a list of forecast items:
   protected ForecastItem nextForecastItem = null;


   /*
    * Getters and setters:
    */
   public Forecast getForecast() {
      return forecast;
   }

   public UUID getIdBudgetItem() {
      return idBudgetItem;
   }

   public void setIdBudgetItem(UUID idBudgetItem) {
      this.idBudgetItem = idBudgetItem;
      setDirty(true);
   }

   public Calendar getNextDate() {
      return nextDate;
   }

   public void setNextDate(Calendar date) {
      this.nextDate = date;
      setDirty(true);
   }

   public ForecastItem getNextForecastItem() {
      return nextForecastItem;
   }

   public void setNextForecastItem(ForecastItem nextForecastItem) {
      this.nextForecastItem = nextForecastItem;
      setDirty(true);
   }


   /*
    * Constructors:
    */
   // Constructor that builds a forecast item from a row in the forecast item table:
   ForecastItem(ResultSet rs) throws SQLException, BudgetException, ForecastException {
      super(false);
      loadFromResultSet(rs);
   }

   // Constructor that builds a forecast item from a row in the budget item table:
   ForecastItem(Forecast forecast, ResultSet budgetItemRS) throws ForecastException, SQLException, BudgetException {
      super(true);
      setDirty(true);
      this.forecast = forecast;
      this.idForecast = forecast.getId();
      loadFromBudgetItem(budgetItemRS);
   }

   // Constructor that builds a forecast item fram a budget item:
   public ForecastItem(Forecast forecast, BudgetItem budgetItem) {
      super(true);
      this.forecast = forecast;
      idForecast = forecast.getId();
      idBudgetItem = budgetItem.getId();
      category = budgetItem.getCategory();
      payee = budgetItem.getCategory();
      period = budgetItem.getPeriod();
      amount = budgetItem.getAmount();
      startDate = budgetItem.getStartDate();
      numberOfPayments = budgetItem.getNumberOfPayments();
      endDate = budgetItem.getEndDate();
      itemType = budgetItem.getItemType();
      howImportant = budgetItem.getHowImportant();
      howOccurs = budgetItem.getHowOccurs();
      howPaid = budgetItem.getHowPaid();
      setDirty(true);
   }

   public static ForecastItem getById(UUID idForecastItem) throws EntityException, SQLException, BudgetException,
           ForecastException {
      return new ForecastItem(EntityInt.getRSById(selectQuery + "where idForecastItem = ", idForecastItem,
              "attempting to retrieve a forecast item "));
   }


   /*
    * Load and save methods:
    */

   private static final String selectQuery = "select bin_to_uuid(idForecastItem) as 'idForecastItem', category, payee, " +
           "period, Forecast_Item.amount, startDate, numberOfPayments, endDate, ItemType, howImportant, howOccurs, " +
           "howPaid, bin_to_uuid(Forecast_idForecast) as 'idForecast', bin_to_uuid(BudgetItem_idBudgetItem) as " +
           "'idBudgetItem' from ForecastDatabase.Forecast_Item ";
   public String getSelectQuery() {
      return selectQuery;
   }

   private static final String insertQuery = "insert into ForecastDatabase.Forecast_Item (idForecastItem, category," +
           " payee, period, amount, startDate, numberOfPayments, endDate, itemType, howImportant, howOccurs, " +
           "howPaid, Forecast_idForecast, BudgetItem_idBudgetItem) values (";
   @Override
   public String getInsertQuery() throws BudgetException {
      return insertQuery + "uuid_to_bin('" + id + "'), \"" + category + "\", \"" + payee + "\", '" +
              Item.generatePeriodType(period) + "', " + amount + ", " + Utility.calendarDateToSqlDateString(startDate) +
              ", " + numberOfPayments + ", " + Utility.calendarDateToSqlDateString(endDate) + ", '" +
              Item.generateItemType(itemType) + "', '" + Item.generateHowImportant(howImportant) + "', '" +
              Item.generateHowOccurs(howOccurs) + "', '" + Item.generateHowPaid(howPaid) + "', uuid_to_bin('" +
              idForecast + "'), uuid_to_bin('" + idBudgetItem + "'))";
   }

   @Override
   public String getInsertOnDuplicateUpdateQuery() throws BudgetException {
      return null;
   }

   private static final String updateQuery = "update ForecastDatabase.Forecast_Item set ";
   @Override
   public String getUpdateQuery() throws BudgetException {
      return updateQuery + " category = '" + category + "', payee = '" +
              payee + "', period = '" + Item.generatePeriodType(period) + "', amount = " + amount + ", startDate = "
              + Utility.calendarDateToSqlDateString(startDate) + ", numberOfPayments = " + numberOfPayments + ", " +
              "endDate = " + Utility.calendarDateToSqlDateString(endDate) + ", itemtype = '" +
              Item.generateItemType(itemType) + "', howImportant = '" + Item.generateHowImportant(howImportant) +
              "', howOccurs = '" + Item.generateHowOccurs(howOccurs) + "', howPaid = '" + Item.generateHowPaid(howPaid)
              + "', Forecast_idForecast = uuid_to_bin('" + idForecast + "'), BudgetItem_idBudgetItem = uuid_to_bin('" +
              idBudgetItem + "') where idForecastItem = uuid_to_bin('" + id + "')";
   }

   private static final String deleteQuery = "delete from ForecastDatabase.Forecast_Item where ";
   @Override
   public String getDeleteQuery() {
      return deleteQuery + "idForecastItem = uuid_to_bin('" + id + "')";
   }

   @Override
   public String getEntityTypeName() {
      return "forecast item";
   }

   // Create a forecast item from a row in the forecast item table:
   private void loadFromResultSet(ResultSet rs) throws BudgetException, ForecastException {
      try {
         id = UUID.fromString(rs.getString("idForecastItem"));
         category = rs.getString("category");
         payee = rs.getString("payee");
         period = Item.parsePeriodType(rs.getString("period"));
         amount = rs.getDouble("amount");
         startDate = Utility.SqlDateToCalendarDate(rs.getDate("startDate"));
         numberOfPayments = rs.getInt("numberOfPayments");
         endDate = Utility.SqlDateToCalendarDate(rs.getDate("endDate"));
         itemType = parseItemType(rs.getString("ItemType"));
         howImportant = parseHowImportant(rs.getString("howImportant"));
         howOccurs = parseHowOccurs(rs.getString("howOccurs"));
         howPaid = parseHowPaid(rs.getString("howPaid"));
         idForecast = UUID.fromString(rs.getString("idForecast"));
         idBudgetItem = UUID.fromString(rs.getString("idBudgetItem"));
         setDirty(false);
      } catch (SQLException e) {
         ForecastException fe = new ForecastException("Error reading in the Budget Item row.\n" + this.toString());
         fe.initCause(e);
         throw (fe);
      }
      return;
   }

   // Create a forecast item from a row in the budget item table:
   private void loadFromBudgetItem(@NotNull ResultSet rs) throws SQLException, ForecastException, BudgetException {
      try {
         if (rs == null) throw new ForecastException("Result set to load from must not be null.");

         category = rs.getString("category");
         payee = rs.getString("payee");
         period = parsePeriodType(rs.getString("period"));
         amount = rs.getDouble("amount");
         startDate.setTime(rs.getDate("startDate"));
         endDate = Utility.SqlDateToCalendarDate(rs.getDate("endDate"));
         numberOfPayments = rs.getInt("numberOfPayments");
         itemType = parseItemType(rs.getString("ItemType"));
         howImportant = parseHowImportant(rs.getString("howImportant"));
         howOccurs = parseHowOccurs(rs.getString("howOccurs"));
         howPaid = parseHowPaid(rs.getString("howPaid"));
         idBudgetItem = UUID.fromString(rs.getString(1));

      } catch (SQLException | BudgetException e) {
         System.out.println("Error reading in the Budget Item row.");
         e.printStackTrace();
         throw e;
      }
   }  // End loadFromBudgetItem().


   /*
    * Helper methods:
    */
   // A convenience method to print out a ForecastItem object:
   @Override
   public String toString() {
      return "Forecast  " + super.toString() + ", BudgetItem ID = " + idBudgetItem;
      //idForecast + id;
   }


   /*
    * Main methods:
    */
   // Compute the first occurrence of this forecast item after an arbitrary date:
   Calendar getFirstDateOnOrAfter(Calendar forecastStartDate) throws ForecastException {

      Calendar tempDate = new GregorianCalendar();

      // Check pre-conditions:
      if (forecastStartDate == null) throw new ForecastException("Date to supersede cannot be null");

      // If the forecast window isn't after this budget item's end date:
      if (this.endDate == null || (this.endDate != null && forecastStartDate.compareTo(this.endDate) <= 0)) {

         // To begin, set the next date to the first date of the forecast:
         nextDate.set(forecastStartDate.get(Calendar.YEAR), forecastStartDate.get(Calendar.MONTH),
                 forecastStartDate.get(Calendar.DATE));

         // Get the day of the week of the first occurrence of this budget item:
         int budgetItemStartDateDayOfWeek = startDate.get(Calendar.DAY_OF_WEEK);

         // Get the day of the week of the forecast start date:
         int forecastStartDayOfWeek = forecastStartDate.get(Calendar.DAY_OF_WEEK);

         // Set firstTime to the first occurrence of the budget item on or after the forecast date:
         switch (period) {

            case DAILY:
               // Next date is already set to the first date of the forecast
               break;

            case WEEKLY:
               // Set the date of the first occurrence to the same day of the week as the budget item start date:
               if (budgetItemStartDateDayOfWeek >= forecastStartDayOfWeek) {
                  nextDate.add(Calendar.DATE, budgetItemStartDateDayOfWeek - forecastStartDayOfWeek);
               } else {
                  nextDate.add(Calendar.DATE, 7 - (forecastStartDayOfWeek - budgetItemStartDateDayOfWeek));
               }
               break;

            case BIWEEKLY:
               // If the day of the month of the start date is on or after the day of the month of the forecast start date:
               if (startDate.get(Calendar.DATE) >= forecastStartDate.get(Calendar.DATE)) {

                  // Then start this transaction on that date in the year and month of the forecast start date:
                  nextDate.set(forecastStartDate.get(Calendar.YEAR), forecastStartDate.get(Calendar.MONTH),
                          startDate.get(Calendar.DATE));

               } else {

                  // if the forecast start date day of the month is less than or equal to two weeks after the item
                  // start date:
                  if (forecastStartDate.get(Calendar.DATE) <= (startDate.get(Calendar.DATE) + 14)) {
                     // then make the next date two weeks after the start date day of the month:
                     nextDate.set(forecastStartDate.get(Calendar.YEAR), forecastStartDate.get(Calendar.MONTH),
                             startDate.get(Calendar.DATE) + 14);

                  } else {
                     // the forecast start date is more than two weeks after the item start date so add 4 weeks:
                     nextDate.set(forecastStartDate.get(Calendar.YEAR), forecastStartDate.get(Calendar.MONTH),
                             startDate.get(Calendar.DATE) + 28);
                  }
               }
               break;

            case SEMIMONTHLY:
               // At the moment semi-monthly means the 1st or the 15th, so pick the first one to occur on or after the
               // forecast start date:
               if (forecastStartDate.get(Calendar.DATE) > 1 && forecastStartDate.get(Calendar.DATE) <= 15) {
                  nextDate.set(forecastStartDate.get(Calendar.YEAR), forecastStartDate.get(Calendar.MONTH), 15);
               } else {
                  nextDate.set(forecastStartDate.get(Calendar.YEAR), forecastStartDate.get(Calendar.MONTH), 1);
               }
               break;

            case SCHOOLYEARSEMIMONTHLY:
               // At the moment semi-monthly means the 1st or the 15th, so pick the first one to occur on or after the
               // forecast start date:
               if (forecastStartDate.get(Calendar.DATE) > 1 && forecastStartDate.get(Calendar.DATE) <= 15) {
                  nextDate.set(forecastStartDate.get(Calendar.YEAR), forecastStartDate.get(Calendar.MONTH), 15);
               } else {
                  nextDate.set(forecastStartDate.get(Calendar.YEAR), forecastStartDate.get(Calendar.MONTH), 1);
               }
               int month = nextDate.get(Calendar.MONTH);
               if (month >= 6 && month <= 8) {
                  nextDate.set(Calendar.MONTH, Calendar.SEPTEMBER);
               }
               break;

            case MONTHLY:
               // If the item start date is after the first month of the forecast:
               tempDate.set(forecastStartDate.get(Calendar.YEAR), forecastStartDate.get(Calendar.MONTH), 1);
               tempDate.add(Calendar.MONTH, 1);
               if (startDate.compareTo(tempDate) >= 0) {

                  // then it's next date is it's start date:
                  nextDate.set(startDate.get(Calendar.YEAR), startDate.get(Calendar.MONTH),
                          startDate.get(Calendar.DATE));
               } else {

                  // Make the item start on it's day of the month, this month:
                  nextDate.set(forecastStartDate.get(Calendar.YEAR), forecastStartDate.get(Calendar.MONTH),
                          startDate.get(Calendar.DATE));

                  // If the item start date day-of-the-month occurs before the forecast start date day-of-the-month:
                  if (startDate.get(Calendar.DATE) < forecastStartDate.get(Calendar.DATE)) {

                     // then make it the start in the second month in the forecast window:
                     nextDate.add(Calendar.MONTH, 1);
                  }
               }
               break;

            case SIXWEEKS:
               // Compute the number of six-week increments occur between the item start date and the forecast start
               // date.
               long sixWeekUnits = abs(ChronoUnit.DAYS.between(startDate.toInstant(), forecastStartDate.toInstant()) / (7 * 6));

               // Set next date to the item start data + that many six-week increments:
               nextDate.set(startDate.get(Calendar.YEAR), startDate.get(Calendar.MONTH), startDate.get(Calendar.DATE));
               nextDate.add(Calendar.DATE, (int) sixWeekUnits * 42);

               // We used an integer value for the division, so we should be on the forecast start date, or less than
               // six weeks before or after it.  If we are before it, then increment by six weeks to get into the
               // forecast:
               if (nextDate.before(forecastStartDate)) nextDate.add(Calendar.DATE, 42);
               break;

            case BIMONTHLY:
               // If one of the start months is odd and the other is even:
               if ((forecastStartDate.get(Calendar.MONTH) & 1) != (startDate.get(Calendar.MONTH) & 1)) {

                  // then the first occurrence is in the month after the forecast start month:
                  nextDate.set(forecastStartDate.get(Calendar.YEAR), forecastStartDate.get(Calendar.MONTH) + 1,
                          startDate.get(Calendar.DATE));
               } else {
                  // if the start date day of the month is on or after the forecast start date day of the month:
                  if (startDate.get(Calendar.DATE) >= forecastStartDate.get(Calendar.DATE)) {

                     // then the first occurrence in the forecast window is in the forecast start month:
                     nextDate.set(forecastStartDate.get(Calendar.YEAR), forecastStartDate.get(Calendar.MONTH),
                             startDate.get(Calendar.DATE));
                  } else {
                     // else it occurs for the first time two months after the forecast start month:
                     nextDate.set(forecastStartDate.get(Calendar.YEAR), forecastStartDate.get(Calendar.MONTH) + 2,
                             startDate.get(Calendar.DATE));
                  }
               }
               break;

            case QUARTERLY:
               // Quarterly dates occur on the same date each year, so set the next date year to be the same as the
               // start date of the forecast:
               nextDate.set(forecastStartDate.get(Calendar.YEAR), startDate.get(Calendar.MONTH), startDate.get(Calendar.DATE));

               // Increment by quarters till the nextDate is on or after the forecast start date:
               while (nextDate.before(forecastStartDate)) nextDate.add(Calendar.MONTH, 3);

               // Decrement by quarters till the nextDate is less than 3 months ahead of the forecast start date:
               while (nextDate.get(Calendar.MONTH) >= forecastStartDate.get(Calendar.MONTH) + 3)
                  nextDate.add(Calendar.MONTH, -3);
               break;

            case SEMIANNUALLY:
               // Semi-annual dates occur on the same date each year, so set the next date year to be the same as the
               // start date of the forecast:
               nextDate.set(forecastStartDate.get(Calendar.YEAR), startDate.get(Calendar.MONTH), startDate.get(Calendar.DATE));

               // Increment by half-years till the nextDate is on or after the forecast start date:
               while (nextDate.before(forecastStartDate)) nextDate.add(Calendar.MONTH, 6);

               // Decrement by half-years till the nextDate is less than 6 months ahead of the forecast start date:
               while (nextDate.get(Calendar.MONTH) >= forecastStartDate.get(Calendar.MONTH) + 3)
                  nextDate.add(Calendar.MONTH, -6);
               break;

            case ANNUALLY:
               // Annual dates occur on the same date each year, so set the next-date-year to be the same as the
               // forecast-start-date year:
               nextDate.set(forecastStartDate.get(Calendar.YEAR), startDate.get(Calendar.MONTH), startDate.get(Calendar.DATE));

               // If the next date this year is before the forecast start date, the move it to next year:
               if (nextDate.before(forecastStartDate)) nextDate.add(Calendar.YEAR, 1);
               break;

            default:
               throw new ForecastException("Unrecognized period type " + period + " in the " + payee + "forecast item.");
         }

         // Check post-conditions:
         System.out.println("First date of this budget item in the forecast window is " + Utility.calendarDateToStringDate(nextDate));
         if (nextDate.compareTo(startDate) < 0) {
            throw new ForecastException("Next date must be on or after forecast start date.");
         }

      } // if the end date of the budget item isn't before the start of the forecast window.

      else {

         // else there is no next date inside the forecast window:
         nextDate = null;

      } // End no next date.

      return nextDate;
   }

   // Get the date of the next occurrence of this forecast item:
   Calendar getNextDateOfOccurrence() throws ForecastException {

      Calendar previousDate = (Calendar) nextDate.clone();
      if (nextDate != null) {
         switch (period) {

            case DAILY:
               // Increment the date by the length of a week, e.g. 7 days:
               nextDate.add(Calendar.DATE, 1);
               break;

            case WEEKLY:
               // Increment the date by the length of a week, e.g. 7 days:
               nextDate.add(Calendar.DATE, 7);
               break;

            case BIWEEKLY:
               // Increment the date by the length of two weeks, e.g. 14 days:
               nextDate.add(Calendar.DATE, 14);
               break;

            case SEMIMONTHLY:
               // For now, semi-monthly items always occur on the 1st and the 15th:
               if (nextDate.get(Calendar.DATE) == 1) {
                  nextDate.set(Calendar.DATE, 15);
               } else {
                  nextDate.add(Calendar.MONTH, 1);
                  nextDate.set(Calendar.DATE, 1);
               }
               break;

            case SCHOOLYEARSEMIMONTHLY:
               // For now, semi-monthly items always occur on the 1st and the 15th:
               if (nextDate.get(Calendar.DATE) == 1) {
                  nextDate.set(Calendar.DATE, 15);
               } else {
                  nextDate.add(Calendar.MONTH, 1);
                  nextDate.set(Calendar.DATE, 1);
               }
               int month = nextDate.get(Calendar.MONTH);
               if (month >= 6 && month <= 8) {
                  nextDate.set(Calendar.MONTH, Calendar.SEPTEMBER);
               }
               break;

            case MONTHLY:
               // Increment the date by one month:
               nextDate.add(Calendar.MONTH, 1);
               break;

            case SIXWEEKS:
               // Increment the date by the length of six weeks, e.g. 42 days:
               nextDate.add(Calendar.DATE, 42);
               break;

            case BIMONTHLY:
               // Increment the date by three months:
               nextDate.add(Calendar.MONTH, 2);
               break;

            case QUARTERLY:
               // Increment the date by three months:
               nextDate.add(Calendar.MONTH, 3);
               break;

            case SEMIANNUALLY:
               // Increment the date by six months:
               nextDate.add(Calendar.MONTH, 6);
               break;

            case ANNUALLY:
               // Increment the date by one year:
               nextDate.add(Calendar.YEAR, 1);
               break;

            default:
               throw new ForecastException("Can't get the next date because period " + period + " is unrecognized.");
         }
      } else {
         throw new ForecastException("Can't get the next date before calling get the first date.");
      }

      // Post-conditions:
      if (nextDate != null) {
         if (nextDate.compareTo(previousDate) <= 0) {
            throw new ForecastException("Next date is the same as, or prior to, the previous date.");
         }

         // If the next date is after the end date of this budget item, then return no next date?
         if (endDate != null && nextDate.compareTo(endDate) > 0) nextDate = null;
      }

      // TODO:  Make into a logging statement:
      //  System.out.println("The next date of this budget item is " + Utility.calendarDateToStringDate(nextDate));

      return nextDate;
   }

   // Calculate the previous date of occurrence of this forecast item given the date of occurrence of this item:
   public Calendar getPreviousDateOfOccurrence(Calendar dateOfItemOccurrence) throws ForecastException {
      Calendar previousDateOfItemOccurrence = (Calendar) dateOfItemOccurrence.clone();
      if (dateOfItemOccurrence != null) {
         switch (period) {

            case DAILY:
               // Decrement the date by one day:
               previousDateOfItemOccurrence.add(Calendar.DATE, -1);
               break;

            case WEEKLY:
               // Decrement the date by the length of a week, e.g. 7 days:
               previousDateOfItemOccurrence.add(Calendar.DATE, -7);
               break;

            case BIWEEKLY:
               // Decrement the date by the length of two weeks, e.g. 14 days:
               previousDateOfItemOccurrence.add(Calendar.DATE, -14);
               break;

            case SEMIMONTHLY:
               // For now, semi-monthly items always occur on the 1st and the 15th:
               if (previousDateOfItemOccurrence.get(Calendar.DATE) == 1) {
                  previousDateOfItemOccurrence.add(Calendar.MONTH, -1);
                  previousDateOfItemOccurrence.set(Calendar.DATE, 15);
               } else {
                  previousDateOfItemOccurrence.set(Calendar.DATE, 1);
               }
               break;

            case SCHOOLYEARSEMIMONTHLY:
               // For now, semi-monthly items always occur on the 1st and the 15th:
               if (previousDateOfItemOccurrence.get(Calendar.DATE) == 1) {
                  previousDateOfItemOccurrence.add(Calendar.MONTH, -1);
                  previousDateOfItemOccurrence.set(Calendar.DATE, 15);
               } else {
                  previousDateOfItemOccurrence.set(Calendar.DATE, 1);
               }
               int month = previousDateOfItemOccurrence.get(Calendar.MONTH);
               if (month >= 6 && month <= 8) {
                  previousDateOfItemOccurrence.set(Calendar.MONTH, Calendar.MAY);
               }
               break;

            case MONTHLY:
               // Decrement the date by one month:
               previousDateOfItemOccurrence.add(Calendar.MONTH, -1);
               break;

            case SIXWEEKS:
               // Decrement the date by the length of six weeks, e.g. 42 days:
               previousDateOfItemOccurrence.add(Calendar.DATE, -42);
               break;

            case BIMONTHLY:
               // Decrement the date by two months:
               previousDateOfItemOccurrence.add(Calendar.MONTH, -2);
               break;

            case QUARTERLY:
               // Decrement the date by three months:
               previousDateOfItemOccurrence.add(Calendar.MONTH, -3);
               break;

            case SEMIANNUALLY:
               // Decrement the date by six months:
               previousDateOfItemOccurrence.add(Calendar.MONTH, -6);
               break;

            case ANNUALLY:
               // Decrement the date by one year:
               previousDateOfItemOccurrence.add(Calendar.YEAR, -1);
               break;

            default:
               throw new ForecastException("Can't get the next date because period unrecognized.");
         }
      } else {
         throw new ForecastException("Can't get the next date before calling get the first date.");
      }

      // Post-conditions:
      if (previousDateOfItemOccurrence != null) {
         if (previousDateOfItemOccurrence.compareTo(dateOfItemOccurrence) >= 0) {
            throw new ForecastException("Previous date is the same as, or after, the passed in date.");
         }

         // If the next date is before the first date of this budget item, then return no previous date:
         if (startDate != null && previousDateOfItemOccurrence.compareTo(startDate) < 0)
            previousDateOfItemOccurrence = null;
      }

      // TODO:  Make into a logging statement:
      //  System.out.println("The next date of this budget item is " + Utility.calendarDateToStringDate(previousDateOfItemOccurrence));

      return previousDateOfItemOccurrence;
   }
}
