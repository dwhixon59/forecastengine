package com.hixon.financialApp.utility;

import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.view.base.ViewInt;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.Date;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;

import static java.util.Calendar.*;

public class Utility {

    // Threshold for comparing currency amounts using doubles or floats.  Consider equal if the difference is less than
    // 1/2 of a cent:
    public static final double CURRENCY_COMPARISON_THRESHOLD = 0.005;

    // Common user for the App:
    private static User user;

    // Common database connection for the App:
    private static Connection dbConnection;

    // The configured view resolver:
    public static ViewInt resolver;


    /*
     * Getters and setters:
     */
    public static User getUser() {
        return user;
    }

    public static void setUser(User user) {
        Utility.user = user;
    }

    public static Connection getDbConnection() {
        return dbConnection;
    }

    public static void setDbConnection(Connection dbConnection) {
        Utility.dbConnection = dbConnection;
    }

    public static ViewInt getView() {
        return resolver;
    }

    public static void setView(ViewInt resolverParam) {
        Utility.resolver = resolverParam;
    }



    /*
     * Helper methods:
     */

    /**
     * Escapes single quotes in a string for use in SQL statements.
     * This prevents SQL injection by doubling any single quotes in the input.
     *
     * @param input The string to escape
     * @return The escaped string safe for use in SQL, or null if input is null
     */
    public static String escapeSqlString(String input) {
        if (input == null) {
            return null;
        }
        return input.replace("'", "''");
    }

    // Copy one java Calendar object to another:
    public static void copyDate(Calendar fromDate, Calendar toDate) {
        toDate.set(fromDate.get(YEAR), fromDate.get(MONTH), fromDate.get(DATE));
    }

    // Print out a date in human-readable format with dashes:
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

    // Format a date in human readable timestamp format:
    public static String calendarDateToStringTimeStamp(Calendar calendar) {
        String dateFormatted;
        if (calendar != null) {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
            fmt.setCalendar(calendar);
            dateFormatted = fmt.format(calendar.getTime()) + "T00:00:00.000";
        } else {
            dateFormatted = "null";
        }
        return dateFormatted;
    }

    /**
     * A date only comparison for two Calendar objects.  It compares the date portion of two {@link Calendar} objects
     * and ignores the time portion completely.
     *
     * @param c1 The first date.  Returned value is relative to this date
     * @param c2 The date to compare to c1.
     * @return <0 if c1 is before c2, 0 if c1 and c2 are the same date and > 0 if c1 is after c2
     */
    public static int dateOnlyCompare(Calendar c1, Calendar c2) {
        if (c1.get(Calendar.YEAR) != c2.get(Calendar.YEAR))
            return c1.get(Calendar.YEAR) - c2.get(Calendar.YEAR);
        if (c1.get(Calendar.MONTH) != c2.get(Calendar.MONTH))
            return c1.get(Calendar.MONTH) - c2.get(Calendar.MONTH);
        return c1.get(Calendar.DAY_OF_MONTH) - c2.get(Calendar.DAY_OF_MONTH);
    }

    // Print out a date in human readable format with slashes:
    public static String calendarDateToStringSlashDate(Calendar calendar) {
        String dateFormatted;
        if (calendar != null) {
            SimpleDateFormat fmt = new SimpleDateFormat("MM/dd/yyyy");
            fmt.setCalendar(calendar);
            dateFormatted = fmt.format(calendar.getTime());
        } else {
            dateFormatted = "null";
        }
        return dateFormatted;
    }

    // Print out a date in human-readable format:
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
            // Parse the timestamp string which is in the format 9/8/2024 6:57:00
            //SimpleDateFormat sdf = new SimpleDateFormat("M/d/yyyy h:mm", Locale.ENGLISH);
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

    public static String toTitleCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        StringBuilder titleCase = new StringBuilder();
        boolean nextTitleCase = true;

        for (char c : input.toCharArray()) {
            if (Character.isSpaceChar(c)) {
                nextTitleCase = true;
            } else if (nextTitleCase) {
                c = Character.toTitleCase(c);
                nextTitleCase = false;
            } else {
                c = Character.toLowerCase(c);
            }
            titleCase.append(c);
        }

        return titleCase.toString();
    }

    /**
     * Formats an enum name or similar string to a human-readable format.
     * Converts underscores and dashes to spaces, and capitalizes the first letter of each word.
     * All other letters are lowercase.
     *
     * Examples:
     * "ON_DEMAND" -> "On Demand"
     * "MONTHLY_PAYMENT" -> "Monthly Payment"
     * "some-hyphenated-value" -> "Some Hyphenated Value"
     *
     * @param input The string to format (typically an enum name)
     * @return The formatted string with proper capitalization and spaces
     */
    public static String formatEnumName(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // Replace underscores and dashes with spaces
        String processed = input.replace('_', ' ').replace('-', ' ');

        // Apply title case formatting
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : processed.toCharArray()) {
            if (Character.isSpaceChar(c)) {
                result.append(c);
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(Character.toLowerCase(c));
            }
        }

        return result.toString();
    }

    // Convert a Java LocalDate to a Java Calendar date:
    public static Calendar localDateToCalendarDate(LocalDate sqlLocalDate) {
        Calendar calendarDate = null;
        if (sqlLocalDate != null) {
            calendarDate = GregorianCalendar.from(sqlLocalDate.atStartOfDay(ZoneId.systemDefault()));
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
        SimpleDateFormat sdf;
        if (stringDate != null) {
            if (stringDate.length() > 5) {

                sdf = new SimpleDateFormat("MM/dd/yy", Locale.ENGLISH);
                sdf.parse(stringDate);
                calendarDate = sdf.getCalendar();

            } else {

                Calendar now = Calendar.getInstance();
                int year = now.get(YEAR);
                stringDate = stringDate + "/" + year;
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

    // Convert a Calendar date to a long month - year format:
    public static String calendarDateToMonthYearDate(Calendar calendar) {
        String dateFormatted;
        if (calendar != null) {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM");
            fmt.setCalendar(calendar);
            dateFormatted = fmt.format(calendar.getTime());
        } else {
            dateFormatted = "null";
        }
        return dateFormatted;
    }

    // Convert a Calendar date to a short month - day format:
    public static String calendarDateToMonthDayStringDate(Calendar calendar) {
        String dateFormatted;
        if (calendar != null) {
            SimpleDateFormat fmt = new SimpleDateFormat("MM-dd");
            fmt.setCalendar(calendar);
            dateFormatted = fmt.format(calendar.getTime());
        } else {
            dateFormatted = "null";
        }
        return dateFormatted;
    }

    // Convert a Calendar date to a short month - day format:
    public static String calendarDateToMonthStringDate(Calendar calendar) {
        String dateFormatted;
        if (calendar != null) {
            SimpleDateFormat fmt = new SimpleDateFormat("MM");
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

    // Return a new Calendar object set to the date of the next first of the month.
    public static Calendar getNextFirstOfMonth(Calendar date) {

        // Clone the input date to avoid modifying the original
        Calendar newDate = (Calendar) date.clone();

        // Move to the next month and set the day to the first:
        newDate.add(Calendar.MONTH, 1);
        newDate.set(Calendar.DAY_OF_MONTH, 1);

        return newDate;
    }


    // Clear the time fields of a calendar date:
    private static void clearTime(Calendar calendarDate) {
        calendarDate.set(HOUR_OF_DAY, 0);
        calendarDate.set(MINUTE, 0);
        calendarDate.set(SECOND, 0);
        calendarDate.set(MILLISECOND, 0);
    }

    // Parse a dollar AMOUNT from a string:
    public static Double parseDollarAmount(String stringAmount) {
        if (!stringAmount.isEmpty()) {
            stringAmount = stringAmount.replace("$", "");
            stringAmount = stringAmount.replace(",", "");
            return Double.parseDouble(stringAmount);
        } else {
            return (double) 0;
        }
    }

    // Print out a dollar AMOUNT in human readable format:1

    public static String formatDollarAmount(double amount) {
        DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");
        String numberAsString = "$" + decimalFormat.format(amount);

        return numberAsString;
    }

    public static String formatRoundedDollarAmount(double amount) {
        long roundedAmount = Math.round(amount);
        String formattedAmount = (amount ==
                0) ? "$0.00" : formatDollarAmount(roundedAmount);
        formattedAmount = formattedAmount.substring(0, formattedAmount.length() - 3);
        return formattedAmount;
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
        if (stringDate.isEmpty()) {
            Calendar now = Calendar.getInstance();
            int year = now.get(YEAR);
            stringDate = stringDate + "/" + year;
            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy", Locale.ENGLISH);
            sdf.parse(stringDate);
            calendarDate = sdf.getCalendar();
        }
        return calendarDate;
    }

    public static boolean isEqualCurrency(double d1, double d2) {
        return Math.abs(d1 - d2) < CURRENCY_COMPARISON_THRESHOLD;
    }

    /**
     * This function rationalizes the concept of difference between two amounts to always return a positive value if
     * the magnitude of amount1 is greater than the magnitude of amount2.  So the difference between -10 and -5 is 5,
     * and the difference between 10 and 5 is also 5.
     *
     * @param amount1
     * @param amount2
     * @return
     */
    public static double currencyDifference(double amount1, double amount2) {

        double difference = amount1 - amount2;

        // If the values are nearly equal, return zero:
        if (Utility.isEqualCurrency(amount1, amount2)) {

            difference = 0.00;

        } else if (amount1 >= Utility.CURRENCY_COMPARISON_THRESHOLD) {

            // We are dealing with a credit, so subtraction yields the opposite of what were are looking for; invert it:
            difference = -difference;
        }

        return difference;
    }

    /**
     * This function determines if a string is null, empty (zero characters), or equal to the string 'null' via a
     * case insensitive comparison.
     *
     * @param string The string to analyze.
     * @return true if the string is null or empty.
     */
    public static boolean isNullOrEmpty(String string) {
        return string == null || string.isEmpty() || string.equalsIgnoreCase("null");
    }

    /**
     * This function ensures that a string is not null, empty (zero characters), or equal to the string 'null' via a
     * case insensitive comparison.
     *
     * @param string The string to analyze.
     * @return true if the string is not null or empty.
     */
    public static boolean isNotNullOrEmpty(String string) {
        return !isNullOrEmpty(string);
    }

    /**
     * This function ensures that a string is not null, empty (zero characters), or equal to the string 'null' via a
     * case-insensitive comparison.
     *
     * @param string The string to analyze.
     * @return The string if the string is not null or empty or equal to "null".  Otherwise, a new empty string.
     */
    public static String emptyStringIfNull(String string) {
        if (string == null) {
            return new String("");
        } else {
            if (string.equalsIgnoreCase("null")) {
                string = "";
                return string;
            } else {
                return string;
            }
        }
    }

    /**
     * Copy a file to the user's personal file system.
     *
     * @param user                The user who's personal file system is the target path of the copy operation.
     * @param sourceFilePath      Path of the source file.
     * @param destinationFilename The desired filename.  No path as that is the user's personal file system.
     */
    public static void copyToUsersFileSystem(User user, File sourceFilePath, String destinationFilename) throws IOException {

        // Get the user's personal file system:
        String usersPersonalFileSystem = user.getPersonalFileSystem();

        // Copy the source file to the user's file system:
        FileUtils.copyFile(sourceFilePath, new File(usersPersonalFileSystem + "\\" + destinationFilename), false);
    }


    /**
     * This utility method appends the second file (file2) to the first file (file1).
     *
     * @param file1 The file to be appended to.
     * @param file2 The file to append to file1
     */
    public static void appendToFile(File file1, File file2) {
        FileReader fr = null;
        FileWriter fw = null;
        try {
            fr = new FileReader(file2);

            fw = new FileWriter(file1, true);

            int c = fr.read();
            fw.write("\n");
            while (c != -1) {
                fw.write(c);
                c = fr.read();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            closeFile(fr);
            closeFile(fw);
        }
    }

    /**
     * This method opens a file reader on the file with the name matching the passed in filename.  If the file is
     * not found, then it will ask the user if they want to try again and if so it will try again.
     *
     * @param fileName The name of the file to be opened.
     * @param fileType A description of the file to be opened that is appropriate for error messages.
     * @return A {@link FileReader} object on the specified file
     * @throws FileNotFoundException Thrown if the file does not exist.
     */
    public static BufferedReader openBufferedFileReader(String fileType, String fileName) throws FileNotFoundException {

        BufferedReader bufferedReader = null;
        boolean done = false;
        while (!done) {
            try {
                bufferedReader = new BufferedReader(new FileReader(fileName));
                done = true;
            } catch (FileNotFoundException fe) {
                getView().say("\n" + fileType + " " + fileName + " does not exist.");
                if (!getView().getYesOrNo("Do you want to try again?")) {
                    throw (fe);
                }
            }
        }
        return bufferedReader;
    }

    /**
     * This method doubles the backslashes in a string.  This is necessary when passing a string to a database query.
     *
     * @param string The string to be modified.
     * @return The modified string, or null if the input string is null.
     */
    public static String doubleBackSlashes(String string) {
        if (string == null) {
            return null;
        }
        return string.replace("\\", "\\\\");
    }

    public enum StartDateType {
        FIRST_OF_LAST_MONTH, FIRST_OF_THIS_MONTH, TODAY, FIRST_OF_NEXT_MONTH, ONE_MONTH_FROM_TODAY,
        ARBITRARY_DATE
    }

    /**
     * Get the number of days between two Calendar dates.  The algorithm is to clear the hours, minutes and seconds
     * values and then get the time difference in milliseconds between the two dates.  Then divide by the number of
     * milliseconds in a day and then round to an integer.
     *
     * @param firstDate  The first date, presumably the earliest date, though that is not required.
     * @param secondDate The second date, presumably the later date, though that is not required.
     * @return The number of days between the two dates disregarding the time portion.  Positive if second date is after
     * the first date.  Negative if second date is earlier than the first date.
     */
    public static int daysBetween(Calendar firstDate, Calendar secondDate) {
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
        return Math.round((secondDate2.getTimeInMillis() - firstDate2.getTimeInMillis()) / (oneDay));
    }
    /**
     * Get the number of months between two Calendar dates.
     *
     * @param startDate The first date, presumably the earliest date, though that is not required.
     * @param endDate   The second date, presumably the later date, though that is not required.
     * @return The number of months between the two dates inclusive.  Negative if the second date is earlier than the
     * first date.
     */
    public static int monthsBetween(Calendar startDate, Calendar endDate) {
        int diffYear = endDate.get(Calendar.YEAR) - startDate.get(Calendar.YEAR);
        return diffYear * 12 + endDate.get(Calendar.MONTH) - startDate.get(Calendar.MONTH);
    }

    /**
     * Get the number of months between two Calendar dates including the starting month.
     *
     * @param startDate The first date, presumably the earliest date, though that is not required.
     * @param endDate   The second date, presumably the later date, though that is not required.
     * @return The number of months between the two dates inclusive.  Negative if the second date is earlier than the
     * first date.
     */
    public static int monthsBetweenDatesInclusive(Calendar startDate, Calendar endDate) {
         return monthsBetween(startDate, endDate) + 1;
    }


    public static Calendar getLastDayOfMonth(Calendar date) {
        // Create a clone of the provided calendar to avoid modifying the original one
        Calendar lastDayOfMonth = (Calendar) date.clone();

        // Set the calendar to the first day of the next month and then subtract one day
        lastDayOfMonth.add(Calendar.MONTH, 1);
        lastDayOfMonth.set(Calendar.DAY_OF_MONTH, 1);
        lastDayOfMonth.add(Calendar.DATE, -1);

        return lastDayOfMonth;
    }


    /**
     * Calculates the number of business days between two dates.
     * Business days are defined as Monday through Friday, excluding bank holidays.
     *
     * @param firstDate The first date (later date for positive result)
     * @param secondDate The second date (earlier date)
     * @return The number of business days between the dates (positive if firstDate is after secondDate)
     */
    public static int businessDaysBeteween(Calendar firstDate, Calendar secondDate) {
        Calendar firstDateCopy = (Calendar) firstDate.clone();
        int diffDays = 0;
        while (firstDateCopy.compareTo(secondDate) > 0) {
            int dayOfWeek = firstDateCopy.get(DAY_OF_WEEK);
            // Only count weekdays (Monday-Friday) that are not bank holidays
            if (dayOfWeek != SATURDAY && dayOfWeek != SUNDAY &&
                !isaBankHoliday(Utility.calendarDateToStringDate(firstDateCopy))) {
                diffDays++;
            }
            firstDateCopy.add(DATE, -1);
        }
        return diffDays;
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
     *  US bank holidays for 2025:
     *  New Year’s Day – Wednesday, January 1, 2025
     *  Martin Luther King Jr. Day – Monday, January 20, 2025
     *  Presidents Day (Washington’s Birthday) – Monday, February 17, 2025
     *  Memorial Day – Monday, May 26, 2025
     *  Juneteenth National Independence Day – Thursday, June 19, 2025
     *  Independence Day – Friday, July 4, 2025
     *  Labor Day – Monday, September 1, 2025
     *  Columbus Day / Indigenous Peoples’ Day – Monday, October 13, 2025
     *  Veterans Day – Tuesday, November 11, 2025
     *  Thanksgiving Day – Thursday, November 27, 2025
     *  Christmas Day – Thursday, December 25, 2025
     */
    public static boolean isaBankHoliday(String date) {
        String holidays[] = {"01-01-2025", "01-20-2025", "02-17-2025", "05-26-2025", "06-19-2025", "07-04-2025",
                "09-01-2025", "10-13-2025", "11-11-2025", "11-27-2025", "12-25-2025"};
        for (int i = 0; i < holidays.length; i++) {
            if (date.equalsIgnoreCase(holidays[i])) {
                return true;
            }
        }
        return false;
    }

    // Create a file:
    public static Boolean createFile(String currentFilename) throws IOException {
        File file = new File(currentFilename);
        return file.createNewFile();
    }

    /**
     * This utility method closes a file and handles any exceptions.
     *
     * @param stream The stream to be closed.
     */
    public static void closeFile(Closeable stream) {
        try {
            if (stream != null) {
                stream.close();
            }
        } catch (IOException ignored) {
        }
    }


    /**
     * Insert some text at the end of a filename, but before the file extension.  So a.b becomes ac.b  The algorithm
     * used is to find the first occurence of a dat (period) and then insert the new text just before that dot.
     * Therefore this function likely won't work on filenames that have more than one dot.
     *
     * @param filename   The filename to insert the next text into.
     * @param appendText The text to be inserted between the filename and file extension.
     * @return The new filename with the text inserted.
     */
    public static String appendToFilename(@NotNull String filename, @NotNull String appendText) {
        return filename.substring(0, filename.indexOf(".")) + appendText + filename.substring(filename.indexOf("."));
    }


    // Create a previous version of a file and clear the current version:
    public static Boolean versionFileAndClear(String currentFilename) throws IOException {
        boolean retVal = false;
        if (versionFile(currentFilename)) {
            retVal = createFile(currentFilename);
        }
        return retVal;
    }

    /**
     * Version the file whose name matches "filename" in a particular user's personal file system.
     *
     * @param user     The user whose persoanl file system contains the file to be versioned.
     * @param filename The name of the file to be versioned.
     */
    public static void versionUserFile(User user, String filename) {

        // Get the root of the user's personal file system:
        String userFileSystem = user.getPersonalFileSystem();

        // Version that file:
        versionFile(userFileSystem + "\\" + filename);
    }

    /**
     * Create a previous version of a file.  Delete any previous ".old" version of the file and rename the current version
     * to ".old".
     *
     * @param currentFilename Name of the file to create a ".old" version of.
     * @return True if the file versioning succeeded.  False if file does not exist, or unable to delete the file, etc.
     */
    public static Boolean versionFile(String currentFilename) {
        return versionFile(currentFilename, "_old");
    }


    /**
     * Create a previous version of a file by appending the filename extension passed in to the current filename just
     * before the file extension. If a file with that name already exists, delete it.  The filename extension is appended
     * as is, so if you want a dot before the extension, then pass it in, e.g. ".old" not "old".
     *
     * @param currentFilename      The name of the file to be versioned.
     * @param oldFilenameExtension The extension to be appended to the current filename to effectively version it.
     * @return True if the file versions succeeded.  False if file does not exist, or unable to delete the file, etc.
     */
    public static Boolean versionFile(String currentFilename, String oldFilenameExtension) {

        boolean result = false;
        boolean done;

        // Delete the previous save file:
        String saveFileName = appendToFilename(currentFilename, oldFilenameExtension);
        File saveFile = new File(saveFileName);
        if (!saveFile.exists()) {
            getView().say("Old file " + saveFileName + " was not deleted because it does not exist.");
        }

        // Rename the current file to the save file name:
        File currentFile = new File(currentFilename);
        if (currentFile.exists()) {
            done = false;
            while (!done) {
                try {
                    // If the save file already exists, delete it:
                    if (Files.exists(saveFile.toPath())) {
                        Files.delete(saveFile.toPath());

                        // Wait for the file to be deleted:
                        while (Files.exists(saveFile.toPath())) {
                            getView().say("Waiting for " + saveFileName + " to be deleted...");
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException ignored) {
                            }
                        }
                    }
                    // Rename the current file to the save file name:
                    Files.move(currentFile.toPath(), saveFile.toPath(), StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                    done = true;
                    result = true;
                } catch (IOException e) {
                    getView().say("\nUnable to rename the file " + currentFilename + " to " + saveFileName);
                    getView().say("Error message: " + e.getMessage());
                    done = !getView().getYesOrNo("Would you like to try again?");
                    if (done) {
                        getView().say("Failed to rename the file " + currentFilename + " to " +
                                saveFileName);
                        result = false;
                    }
                }
            }
        } else {
            getView().say("Current file " + currentFilename + " was not renamed because it does not exist.");
            result = false;
        }

        return result;
    }

    /**
     * Parses a flexible date range string and returns an array of two Calendar objects [startDate, endDate].
     * Supports multiple date formats:
     * - YYYY-MM-DD to YYYY-MM-DD (full dates)
     * - MM-DD to MM-DD (defaults to current year for start, next occurrence for end)
     * - DD to DD (defaults to current month/year for start, next occurrence for end)
     *
     * @param dateRangeStr The date range string (e.g., "2024-01-01 to 2024-12-31", "01-15 to 03-20", "15 to 20")
     * @return Array of two Calendar objects [startDate, endDate], or null if parsing fails
     */
    public static Calendar[] parseFlexibleDateRange(String dateRangeStr) {
        if (dateRangeStr == null || dateRangeStr.trim().isEmpty()) {
            return null;
        }

        String[] parts = dateRangeStr.trim().split("\\s+to\\s+");
        if (parts.length != 2) {
            return null;
        }

        String startStr = parts[0].trim();
        String endStr = parts[1].trim();

        try {
            Calendar startDate;
            Calendar endDate;
            Calendar now = Calendar.getInstance();

            // Check format by counting dashes
            int startDashes = startStr.length() - startStr.replace("-", "").length();
            int endDashes = endStr.length() - endStr.replace("-", "").length();

            if (startDashes == 2 && endDashes == 2) {
                // Full format: YYYY-MM-DD to YYYY-MM-DD
                startDate = sqlDateStringToCalendarDate(startStr);
                endDate = sqlDateStringToCalendarDate(endStr);
            } else if (startDashes == 1 && endDashes == 1) {
                // MM-DD to MM-DD format
                startDate = parseMonthDay(startStr, now.get(YEAR));
                endDate = parseMonthDay(endStr, now.get(YEAR));

                // If endDate is before startDate, it's in the next year
                if (endDate.before(startDate)) {
                    endDate.add(YEAR, 1);
                }
            } else if (startDashes == 0 && endDashes == 0) {
                // DD to DD format (day only)
                int startDay = Integer.parseInt(startStr);
                int endDay = Integer.parseInt(endStr);

                startDate = (Calendar) now.clone();
                startDate.set(DATE, startDay);

                endDate = (Calendar) now.clone();
                endDate.set(DATE, endDay);

                // If endDate is before startDate in the same month, move to next month
                if (endDate.before(startDate)) {
                    endDate.add(MONTH, 1);
                }
            } else {
                // Mixed formats not supported
                return null;
            }

            return new Calendar[]{startDate, endDate};

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parses a MM-DD string into a Calendar with the given year.
     *
     * @param monthDayStr String in MM-DD format
     * @param year The year to use
     * @return Calendar object set to the specified date
     * @throws ParseException if parsing fails
     */
    private static Calendar parseMonthDay(String monthDayStr, int year) throws ParseException {
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd", Locale.ENGLISH);
        sdf.parse(monthDayStr);
        Calendar cal = sdf.getCalendar();
        cal.set(YEAR, year);
        return cal;
    }

}


