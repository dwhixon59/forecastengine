package com.hixon.financialApp.utility;

import com.hixon.financialApp.controller.QuitException;
import com.hixon.financialApp.view.base.*;

import java.sql.Connection;
import java.sql.Date;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static java.util.Calendar.*;

public class Utility {

   // Common database connection for the App:
   private static Connection dbConnection;

   // The configured transaction resolver:
   private static TransactionResolverInt resolver;

   // Configured views for interfacing with external agents:
   private static RegisterViewInt registerView;
   private static BudgetViewInt budgetView;
   private static ForecastViewInt forecastView;


   /*
    * Getters and setters:
    */
   public static Connection getDbConnection() {
      return dbConnection;
   }

   public static void setDbConnection(Connection dbConnection) {
      com.hixon.financialApp.utility.Utility.dbConnection = dbConnection;
   }

   public static TransactionResolverInt getResolver() {
      return resolver;
   }

   public static void setResolver(TransactionResolverInt resolver) {
      com.hixon.financialApp.utility.Utility.resolver = resolver;
   }

   public static RegisterViewInt getRegisterView() {
      return registerView;
   }

   public static void setRegisterView(RegisterViewInt registerView) {
      Utility.registerView = registerView;
   }

   public static BudgetViewInt getBudgetView() {
      return budgetView;
   }

   public static void setBudgetView(BudgetViewInt budgetView) {
      Utility.budgetView = budgetView;
   }

   public static ForecastViewInt getForecastView() {
      return forecastView;
   }

   public static void setForecastView(ForecastViewInt forecastView) {
      Utility.forecastView = forecastView;
   }


   /*
    * Helper methods:
    */

   // Copy one java Calendar object to another:
   public static void copyDate(Calendar fromDate, Calendar toDate) {
      toDate.set(fromDate.get(YEAR), fromDate.get(MONTH), fromDate.get(DATE));
   }

   // Print out a date in human readable format:
   public static String calendarDateToStringDate(Calendar calendar) {
      String dateFormatted;
      if (calendar != null) {
         SimpleDateFormat fmt = new SimpleDateFormat("MM-dd-yyyy");
         fmt.setCalendar(calendar);
         dateFormatted = fmt.format(calendar.getTime());
      } else {
         dateFormatted = "null";
      }
      return dateFormatted;
   }

   // Print out a date in human readable format:
   public static String calendarDateToLongStringDate(Calendar calendar) {
      String dateFormatted;
      if (calendar != null) {
         SimpleDateFormat fmt = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss");
         fmt.setCalendar(calendar);
         dateFormatted = fmt.format(calendar.getTime());
      } else {
         dateFormatted = "null";
      }
      return dateFormatted;
   }

   // Convert a Java Calendar date to YYYY-MM-DD format for inserting into the database:
   public static java.sql.Date calendarDateToSqlDate(Calendar calendar) {
      java.sql.Date sqlDate = null;
      if (calendar != null) {
         sqlDate = new Date(calendar.getTimeInMillis());
      }
      return sqlDate;
   }

   // Convert a Java Calendar date to YYYY-MM-DD format:
   public static String calendarDateToSqlDateString(Calendar calendar) {
      String dateFormatted;
      if (calendar != null) {
         SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
         fmt.setCalendar(calendar);
         dateFormatted = "'" + fmt.format(calendar.getTime()) + "'";
      } else {
         dateFormatted = "null";
      }
      return dateFormatted;
   }

   // Convert a Java String date in MM-DD-YY format to a Calendar object:
   public static Calendar sqlDateStringToCalendarDate(String stringDate) throws ParseException {
      Calendar calendarDate = null;
      if (stringDate != null) {
         SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
         sdf.parse(stringDate);
         calendarDate = sdf.getCalendar();
      }
      return calendarDate;
   }

   // Convert a Timestamp string to a Calendar object:
   public static Calendar stringTimeStampToCalendarDate(String timeStamp) throws ParseException {
      Calendar calendarDate = null;
      if (timeStamp != null && timeStamp.length() > 0) {
         SimpleDateFormat sdf = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss", Locale.ENGLISH);
         sdf.parse(timeStamp);
         calendarDate = sdf.getCalendar();
      }
      return calendarDate;
   }

   // Convert a Java SQL data to a Java Calendar date:
   public static Calendar SqlDateToCalendarDate(Date sqlDate) {
      Calendar calendarDate = null;
      if (sqlDate != null) {
         calendarDate = new GregorianCalendar();
         calendarDate.setTime(sqlDate);
      }
      return calendarDate;
   }

   // Convert a Java SQL timestamp to a Java Calendar date:
   public static Calendar SqlTimestampToCalendarDate(Timestamp timestamp) {
      Calendar calendarDate = null;
      if (timestamp != null) {
         calendarDate = new GregorianCalendar();
         calendarDate.setTime(timestamp);
      }
      return calendarDate;
   }

   // Convert a Java SQL date to a Java String date in MM-DD-YY format:
   public static String SqlDateToStringDate(Date sqlDate) {
      String stringDate = null;
      if (sqlDate != null) {
         SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
         stringDate = sdf.format(sqlDate);
      }
      return stringDate;
   }

   // Convert a Java String date in MM-DD-YY, or MM-DD format to a Calendar object:
   public static Calendar stringDateDashToCalendarDate(String stringDate) throws ParseException {
      Calendar calendarDate = null;
      SimpleDateFormat sdf;
      if (stringDate != null) {
         if (stringDate.length() > 5) {
            sdf = new SimpleDateFormat("MM-dd-yy", Locale.ENGLISH);
            sdf.parse(stringDate);
            calendarDate = sdf.getCalendar();
         } else {
            Calendar now = Calendar.getInstance();
            int year = now.get(YEAR);
            stringDate = stringDate + "-" + year;
            sdf = new SimpleDateFormat("MM-dd-yyyy", Locale.ENGLISH);
            sdf.parse(stringDate);
            calendarDate = sdf.getCalendar();
            if (now.compareTo(calendarDate) < 0) {
               calendarDate.set(YEAR, year - 1);
            }
         }
      }
      return calendarDate;
   }

   // Convert a Java String date in MM/DD/YY, or MM/DD format to a Calendar object:
   public static Calendar stringDateSlashToCalendarDate(String stringDate) throws ParseException {
      Calendar calendarDate = null;
      SimpleDateFormat sdf = null;
      if (stringDate != null) {
         if (stringDate.length() > 5) {

            sdf = new SimpleDateFormat("MM/dd/yy", Locale.ENGLISH);
            sdf.parse(stringDate);
            calendarDate = sdf.getCalendar();

         } else {

            Calendar now = Calendar.getInstance();
            int year = now.get(YEAR);
            stringDate = stringDate + "/" + String.valueOf(year);
            sdf = new SimpleDateFormat("MM/dd/yyyy", Locale.ENGLISH);
            sdf.parse(stringDate);
            calendarDate = sdf.getCalendar();
            if (now.compareTo(calendarDate) < 0) {
               calendarDate.set(YEAR, year - 1);
            }
         }
      }
      return calendarDate;
   }

   // Convert a Java String date in MM-DD-YY format to a Java SQL date:
   public static Date stringDateToSqlDate(String stringDate) throws ParseException {
      Date sqlDate = null;
      if (stringDate != null) {
         SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy", Locale.ENGLISH);
         sqlDate = (Date) sdf.parse(stringDate);
      }
      return sqlDate;
   }

   // Convert a Calendar date to a long month - year format:
   public static String calendarDateToMonthYearLongDate(Calendar calendar) {
      String dateFormatted;
      if (calendar != null) {
         SimpleDateFormat fmt = new SimpleDateFormat("MMMM - yyyy");
         fmt.setCalendar(calendar);
         dateFormatted = fmt.format(calendar.getTime());
      } else {
         dateFormatted = "null";
      }
      return dateFormatted;
   }

   // Convert a long "month - year" date string to a Calendar object:
   public static Calendar MonthYearLongDateToCalendarDate(String stringDate) throws ParseException {
      Calendar calendarDate = Calendar.getInstance();
      clearTime(calendarDate);
      if (stringDate != null) {
         String[] tokens = stringDate.split(" ");
         if (tokens.length != 3) {
            throw new ParseException("Wrong number of tokens in the string.", 0);
         }
         if (tokens[0].equalsIgnoreCase("January")) {
            calendarDate.set(MONTH, Calendar.JANUARY);
         } else if (tokens[0].equalsIgnoreCase("February")) {
            calendarDate.set(MONTH, FEBRUARY);
         } else if (tokens[0].equalsIgnoreCase("March")) {
            calendarDate.set(MONTH, MARCH);
         } else if (tokens[0].equalsIgnoreCase("April")) {
            calendarDate.set(MONTH, APRIL);
         } else if (tokens[0].equalsIgnoreCase("May")) {
            calendarDate.set(MONTH, MAY);
         } else if (tokens[0].equalsIgnoreCase("June")) {
            calendarDate.set(MONTH, JUNE);
         } else if (tokens[0].equalsIgnoreCase("July")) {
            calendarDate.set(MONTH, JULY);
         } else if (tokens[0].equalsIgnoreCase("August")) {
            calendarDate.set(MONTH, AUGUST);
         } else if (tokens[0].equalsIgnoreCase("September")) {
            calendarDate.set(MONTH, SEPTEMBER);
         } else if (tokens[0].equalsIgnoreCase("October")) {
            calendarDate.set(MONTH, OCTOBER);
         } else if (tokens[0].equalsIgnoreCase("November")) {
            calendarDate.set(MONTH, NOVEMBER);
         } else if (tokens[0].equalsIgnoreCase("December")) {
            calendarDate.set(MONTH, DECEMBER);
         } else {
            throw new ParseException("Month token is not a month name", 0);
         }
         calendarDate.set(DATE, 1);
         try {
            calendarDate.set(YEAR, Integer.parseInt(tokens[2]));
         } catch (NumberFormatException ne) {
            ParseException pe = new ParseException("Year token is not an integer.", 0);
            pe.initCause(ne);
            throw pe;
         }
      } else {
         throw new ParseException("The date string cannot be null.", 0);
      }
      return calendarDate;
   }

   // Clear the time fields of a calendar date:
   private static void clearTime(Calendar calendarDate) {
      calendarDate.set(HOUR_OF_DAY, 0);
      calendarDate.set(MINUTE, 0);
      calendarDate.set(SECOND, 0);
      calendarDate.set(MILLISECOND, 0);
   }

   // Parse a dollar AMOUNT from a string:
   public static Double parseDollarAmount(String stringAmount) throws ParseException {
      stringAmount = stringAmount.replace("$", "");
      stringAmount = stringAmount.replace(",", "");
      return Double.parseDouble(stringAmount);
   }

   // Print out a dollar AMOUNT in human readable format:
   public static String formatDollarAmount(double amount) {
      DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");
      String numberAsString = "$" + decimalFormat.format(amount);
      return numberAsString;
   }

   public static int doubleToInt(double value) {
      return (int) Math.round(value);
   }

   // Convert a string date in month/day format to a Java SQL date:
   public static Date stringMonthDayToSqlDate(String monthDay) throws ParseException {
      Date sqlDate = null;
      if (monthDay != null) {
         SimpleDateFormat sdf = new SimpleDateFormat("MM-dd", Locale.ENGLISH);
         sqlDate = (Date) sdf.parse(monthDay);
      }
      return sqlDate;
   }

   // Convert a string date in month/day format to a Java Calendar date:
   public static Calendar stringMonthDayToCalendar(String stringDate) throws ParseException {
      Calendar calendarDate = null;
      if (stringDate != null) {
         Calendar now = Calendar.getInstance();
         int year = now.get(YEAR);
         stringDate = stringDate + "/" + String.valueOf(year);
         SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy", Locale.ENGLISH);
         sdf.parse(stringDate);
         calendarDate = sdf.getCalendar();
      }
      return calendarDate;
   }

   public static int daysBeteween(Calendar firstDate, Calendar secondDate) {
      int oneDay = 24 * 60 * 60 * 1000; // hours*minutes*seconds*milliseconds
      Calendar firstDate2 = Calendar.getInstance();
      firstDate2.setTimeInMillis(firstDate.getTimeInMillis());
      firstDate2.clear(Calendar.HOUR);
      firstDate2.clear(Calendar.MINUTE);
      firstDate2.clear(Calendar.SECOND);
      Calendar secondDate2 = Calendar.getInstance();
      secondDate2.setTimeInMillis(secondDate.getTimeInMillis());
      secondDate2.clear(Calendar.HOUR);
      secondDate2.clear(Calendar.MINUTE);
      secondDate2.clear(Calendar.SECOND);
      int diffDays = Math.round((secondDate2.getTimeInMillis() - firstDate2.getTimeInMillis()) / (oneDay));
      return diffDays;
   }

   public enum StartDateType {
      FIRST_OF_LAST_MONTH, FIRST_OF_THIS_MONTH, TODAY, FIRST_OF_NEXT_MONTH, ONE_MONTH_FROM_TODAY,
      ARBITRARY_DATE
   }

   public static Calendar askStartDate() throws QuitException {
      // Get the starting date type:
      UserResponse response = getResolver().getForecastStartDate();

      // Compute the start date:
      Calendar startDate = Calendar.getInstance();
      switch (response.getStartDate()) {
         case FIRST_OF_LAST_MONTH:
            startDate.add(MONTH, -1);
            startDate.set(DATE, 1);
            break;

         case FIRST_OF_THIS_MONTH:
            startDate.set(DATE, 1);
            break;

         case TODAY:
            break;

         case FIRST_OF_NEXT_MONTH:
            startDate.add(MONTH, 1);
            startDate.set(DATE, 1);
            break;

         case ONE_MONTH_FROM_TODAY:
            startDate.add(MONTH, 1);
            break;

         case ARBITRARY_DATE:
            startDate = response.getDate();
            break;
      }
      return startDate;
   }


   // Modifies date to the last business day before it:
   public static int setToLastBusinessDayBefore(Calendar date) {
      date.add(DATE, -1);
      if (date.get(DAY_OF_WEEK) == SATURDAY || date.get(DAY_OF_WEEK) == SUNDAY || isaBankHoliday(Utility.calendarDateToStringDate(date))) {
         return setToLastBusinessDayBefore(date);
      } else {
         return date.get(DATE);
      }
   }

   /*
    *  US Bank holidays for 2020:
    * New Year's Day - Wednesday, January 1
    * Martin Luther King, Jr. Day - Monday, January 20
    * Presidents' Day - Monday, February 17
    * Memorial Day - Monday, May 25
    * Independence Day - Saturday, July 4
    * Labor Day - Monday, September 7
    * Veterans' Day - Wednesday, November 11
    * Thanksgiving Day Thursday, November 26
    * Christmas Day Friday, December 25
    */
   public static boolean isaBankHoliday(String date) {
      String holidays[] = {"01-01-2020", "01-20-2020", "02-17-2020", "05-25-2020", "07-04-2020", "09-07-2020",
              "11-11-2020", "11-26-2020", "12-25-2020"};
      for (int i = 0; i < holidays.length; i++) {
         if (date.equalsIgnoreCase(holidays[i])) {
            return true;
         }
      }
      return false;
   }
}
