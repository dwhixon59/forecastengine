package com.hixon.financialApp.utility;

import com.hixon.financialApp.controller.QuitException;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.view.base.*;
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

    // Common database connection for the App:
    private static User user;

    // Common database connection for the App:
    private static Connection dbConnection;

    // The configured transaction resolver:
    public static TransactionResolverInt resolver;

    // Configured views for interfacing with external agents:
    private static RegisterViewInt registerView;
    private static BudgetViewInt budgetView;
    private static ForecastViewInt forecastView;
    private static NotificationServiceInt notificationService;


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
        com.hixon.financialApp.utility.Utility.dbConnection = dbConnection;
    }

    public static TransactionResolverInt getResolver() {
        return resolver;
    }

    public static void setResolver(TransactionResolverInt resolver) {
        com.hixon.financialApp.utility.Utility.resolver = resolver;
    }

    public static NotificationServiceInt getNotificationService() {
        return notificationService;
    }

    public static void setNotificationService(NotificationServiceInt notificationService) {
        Utility.notificationService = notificationService;
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

    // Print out a date in human readable format with dashes:
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

    // Clear the time fields of a calendar date:
    private static void clearTime(Calendar calendarDate) {
        calendarDate.set(HOUR_OF_DAY, 0);
        calendarDate.set(MINUTE, 0);
        calendarDate.set(SECOND, 0);
        calendarDate.set(MILLISECOND, 0);
    }

    // Parse a dollar AMOUNT from a string:
    public static Double parseDollarAmount(String stringAmount) {
        if (stringAmount.length() > 0) {
            stringAmount = stringAmount.replace("$", "");
            stringAmount = stringAmount.replace(",", "");
            return Double.parseDouble(stringAmount);
        } else {
            return new Double(0);
        }
    }

    // Print out a dollar AMOUNT in human readable format:
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
        if (stringDate != null) {
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
        FileUtils.copyFile(sourceFilePath, new File(usersPersonalFileSystem + "\\" + destinationFilename));
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
                getResolver().say("\n" + fileType + " " + fileName + " does not exist.");
                if (!getResolver().getYesOrNo("Do you want to try again?")) {
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
     * @return The modified string.
     */
    public static String doubleBackSlashes(String string) {
        return string.replace("\\", "\\\\");
    }

    public enum StartDateType {
        FIRST_OF_LAST_MONTH, FIRST_OF_THIS_MONTH, TODAY, FIRST_OF_NEXT_MONTH, ONE_MONTH_FROM_TODAY,
        ARBITRARY_DATE
    }

    /**
     * Ask the user for the starting date for rendering a forecast.  Getting a start date is not strictly necessary.  Most
     * of the time the user wants to render the entire forecast, not just the transactions on or after a certain date.
     *
     * @return The date that the forecast rendering should begin on.
     * @throws QuitException
     */
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

    public static int businessDaysBeteween(Calendar firstDate, Calendar secondDate) {
        Calendar firstDateCopy = (Calendar) firstDate.clone();
        int diffDays = 0;
        while (firstDateCopy.compareTo(secondDate) > 0) {
            if (!isaBankHoliday(Utility.calendarDateToStringDate(firstDateCopy))) diffDays++;
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
     *  US Bank holidays for 2021:
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
        String holidays[] = {"01-01-2021", "01-18-2021", "02-15-2021", "04-02-2021", "05-31-2021", "07-05-2021", "09-06-2021",
                "11-11-2021", "11-25-2021", "12-24-2021", "12-31-2021"};
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
        } catch (IOException e) {
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

        Boolean result = false;
        boolean done;

        // Delete the previous save file:
        String saveFileName = appendToFilename(currentFilename, oldFilenameExtension);
        File saveFile = new File(saveFileName);
        if (!saveFile.exists()) {
            getResolver().say("Old file " + saveFileName + " was not deleted because it does not exist.");
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
                            getResolver().say("Waiting for " + saveFileName + " to be deleted...");
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                            }
                        }
                    }
                    // Rename the current file to the save file name:
                    Files.move(currentFile.toPath(), saveFile.toPath(), StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                    done = true;
                    result = true;
                } catch (IOException e) {
                    getResolver().say("\nUnable to rename the file " + currentFilename + " to " + saveFileName);
                    getResolver().say("Error message: " + e.getMessage());
                    done = !getResolver().getYesOrNo("Would you like to try again?");
                    if (done) {
                        getResolver().say("Failed to rename the file " + currentFilename + " to " +
                                saveFileName);
                        result = false;
                    }
                }
            }
        } else {
            getResolver().say("Current file " + currentFilename + " was not renamed because it does not exist.");
            result = false;
        }

        return result;
}

}
