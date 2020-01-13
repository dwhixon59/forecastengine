package com.hixon.financialApp.utility;

import com.hixon.financialApp.controller.QuitException;
import com.hixon.financialApp.view.base.TransactionResolverInt;
import com.hixon.financialApp.view.base.UserResponse;

import java.sql.Connection;
import java.sql.Date;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;

import static java.util.Calendar.DATE;
import static java.util.Calendar.MONTH;

public class Utility {

   // Common database connection for the App:
   private static Connection dbConnection;
   private static TransactionResolverInt resolver;

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

   // Convert a Java Calendar date to YYYY-MM-DD format for inserting into the database:
   public static java.sql.Date calendarDateToSqlDate(Calendar calendar) {
      java.sql.Date sqlDate = null;
      if (calendar != null) {
         sqlDate = new Date(calendar.getTimeInMillis());
      }
      return sqlDate;
   }

   // Convert a Java Calendar date to YYYY-MM-DD format for inserting into the database:
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

   // Convert a Java SQL data to a Java Calendar date:
   public static Calendar SqlDateToCalendarDate(Date sqlDate) {
      Calendar calendarDate = null;
      if (sqlDate != null) {
         calendarDate = new GregorianCalendar();
         calendarDate.setTime(sqlDate);
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

   // Convert a Java String date in MM-DD-YY format to a Calendar object:
   public static Calendar stringDateDashToCalendarDate(String stringDate) throws ParseException {
      Calendar calendarDate = null;
      if (stringDate != null) {
         SimpleDateFormat sdf = new SimpleDateFormat("MM-dd-yy", Locale.ENGLISH);
         sdf.parse(stringDate);
         calendarDate = sdf.getCalendar();
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
            int year = now.get(Calendar.YEAR);
            stringDate = stringDate + "/" + String.valueOf(year);
            sdf = new SimpleDateFormat("MM/dd/yyyy", Locale.ENGLISH);
            sdf.parse(stringDate);
            calendarDate = sdf.getCalendar();
            if (now.compareTo(calendarDate) < 0) {
               calendarDate.set(Calendar.YEAR, year - 1);
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
         int year = now.get(Calendar.YEAR);
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

   public enum StartDateType {FIRST_OF_THIS_MONTH, TODAY, FIRST_OF_NEXT_MONTH, ONE_MONTH_FROM_TODAY, ARBITRARY_DATE}

   public static Calendar askStartDate() throws QuitException {
      // Get the starting date type:
      UserResponse response = getResolver().getForecastStartDate();

      // Compute the start date:
      Calendar startDate = Calendar.getInstance();
      switch (response.getStartDate()) {
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

}
