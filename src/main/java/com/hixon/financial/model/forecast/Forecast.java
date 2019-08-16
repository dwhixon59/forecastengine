package com.hixon.financial.model.forecast;

import com.hixon.financial.Utility;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.UUID;

public class Forecast {

    /*
     * Forecast class fields:
     */
    protected Calendar startDate;
    protected double startingBalance;
    protected Calendar endDate;
    protected double minimumBalance;
    protected double endingBalance;
    protected int numberOfMonths;
    protected String budgetname;
    protected UUID idBudget;
    protected ForecastTransaction[] transactions = null;
    private UUID idForecast = UUID.randomUUID();
    private ForecastItem firstForecastItem = null;
    private ForecastItem lastForecastItem = null;
    private ForecastTransaction firstSignificantEvent = null;
    private ForecastTransaction lastSignificantEvent = null;


    /*
     * Forecast class constructors:
     */
    public Forecast(String budgetName, Calendar startDate, double startingBalance, double minimumBalance,
                    int numberOfMonths) throws ForecastException, SQLException {

        if (startDate == null) {
            this.startDate = Calendar.getInstance();
            this.startDate.set(Calendar.DAY_OF_MONTH, 1);
        } else {
            this.startDate = startDate;
        }
        this.startingBalance = startingBalance;
        this.endDate = (Calendar) startDate.clone();
        this.endDate.add(Calendar.MONTH, numberOfMonths);
        // Subtract off one day because n months after June 1st is June 1st, but we only want to go to May 31st, etc.:
        this.endDate.add(Calendar.DATE, -1);
        this.minimumBalance = minimumBalance;
        this.endingBalance = 0;
        this.numberOfMonths = numberOfMonths;
        this.budgetname = budgetName;
        this.transactions = new ForecastTransaction[numberOfMonths * 31];

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


    /*
     * Forecast class getters and setters:
     */
    public Calendar getStartDate() { return startDate; }
    public Calendar getEndDate() { return endDate; }
    public double getStartingBalance() { return startingBalance; }
    public double getMinimumBalance() { return minimumBalance; }
    public double getEndingBalance() { return endingBalance; }
    ForecastTransaction[] getTransactions() { return transactions; }
    public ForecastTransaction getFirstSignificantEvent() { return firstSignificantEvent; }
    public ForecastTransaction getLastSignificantEvent() { return lastSignificantEvent; }


    /*
     * Forecast class main methods:
     */

    // Determine if a date falls within the forecast window of this forecast object:
    public boolean fallsWithinForecastWindow(Calendar date) {
        boolean decision = false;

        if (date != null) {
            if (date.compareTo(startDate) >= 0 && date.compareTo(endDate) <= 0) {
                decision = true;
            } else {
                decision = false;
            }
        }

        return decision;
    }

    public ForecastTransaction getTransactionsOnDate(Calendar date) {

        int index = (int) ChronoUnit.DAYS.between(startDate.toInstant(), date.toInstant());
        return transactions[index];
    }

    // Add a forecast transaction to the transaction array on the date that it is expected to occur:
    public void addTransactionOnDate(ForecastItem forecastItem, Calendar nextDate) throws Exception {

        // Calculate the index to assign this transaction by calculating the number of days between
        // the start date of the forecast and the day this transaction occurs:
        int index = (int) ChronoUnit.DAYS.between(startDate.toInstant(), nextDate.toInstant());

        if (index < this.transactions.length) {

            // If this is the first transaction on that date:
            ForecastTransaction forecastTransaction = new ForecastTransaction(forecastItem, nextDate);
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
    public void summarize(SignificantEvents[] events) {

        // Traverse the forecast sequentially from the first day to the last day:
        for (int i = 0; i < this.transactions.length; i++) {
            if (this.transactions[i] != null) {
                ForecastTransaction forecastTransaction = this.transactions[i];
                while (forecastTransaction != null)
                {
                    // Update the ending balance in the forecast and the running balance in the transaction:
                    endingBalance += forecastTransaction.getForecastItem().getAmount();
                    forecastTransaction.setRunningBalance(endingBalance);
                    if (forecastTransaction.getNextTransaction() == null)
                        System.out.println(forecastTransaction.toString());

                    // If this is the last transaction on the current day, and the balance dips below the specified
                    // minimum balance, then add this transaction to the significant events list:
                    if (forecastTransaction.getNextTransaction() == null && endingBalance < minimumBalance) {
                        if (firstSignificantEvent == null) {
                            firstSignificantEvent = forecastTransaction;
                            lastSignificantEvent = forecastTransaction;
                        } else {
                            lastSignificantEvent.setNextSignificantEvent(forecastTransaction);
                            lastSignificantEvent = forecastTransaction;
                        }
                    }
                    forecastTransaction = forecastTransaction.getNextTransaction();
                }
            }
        }


    }

    // Save the forecast to the database:
    public void save(Connection dbConnection) throws SQLException {
        PreparedStatement preparedStmt = null;
        String errorMessage = null;
        try {
            // Insert the forecast tuple:
            errorMessage = "SQL error attempting to insert the Forecast object into the database.";
            String query = "insert into ForecastDatabase.Forecast (idForecast, description, dateGenerated, " +
                    "startDate, startingBalance, endDate, endingBalance, numberOfMonths, Budget_idBudget) " +
                    "values(UUID_TO_BIN(?), ?, ?, ?, ?, ?, ?, ?, UUID_TO_BIN(?))";
            preparedStmt = dbConnection.prepareStatement(query);
            preparedStmt.setString(1, idForecast.toString());
            preparedStmt.setString(2, "Test Forecast for Bill Pay Account");
            preparedStmt.setObject(3, new java.sql.Timestamp(System.currentTimeMillis()));
            preparedStmt.setDate(4, new java.sql.Date(startDate.getTimeInMillis()));
            preparedStmt.setDouble(5, startingBalance);
            preparedStmt.setDate(6, new java.sql.Date(endDate.getTimeInMillis()));
            preparedStmt.setDouble(7, endingBalance);
            preparedStmt.setInt(8, numberOfMonths);
            preparedStmt.setString(9, idBudget.toString());
            preparedStmt.execute();

            // Insert the forecast item tuples:
            errorMessage = "SQL error attempting to insert a forecast item into the database.";
            query = "insert into forecastdatabase.forecastitem (idForecastItem, category, payee, period, amount, " +
                    "startDate, numberOfPayments, endDate, ItemType, howPaid, searchString, Forecast_idForecast, " +
                    "BudgetItem_idBudgetItem) values (UUID_TO_BIN(?), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, UUID_TO_BIN(?), " +
                    "UUID_TO_BIN(?))";
            preparedStmt = dbConnection.prepareStatement(query);
            ForecastItem forecastItem = firstForecastItem;
            while (forecastItem != null) {
                preparedStmt.setString(1, forecastItem.getId().toString());
                preparedStmt.setString(2, forecastItem.getCategory());
                preparedStmt.setString(3, forecastItem.getPayee());
                preparedStmt.setString(4, forecastItem.getPeriod().toString());
                preparedStmt.setDouble(5, forecastItem.getAmount());
                preparedStmt.setDate(6,  Utility.calendarDateToSqlDate(forecastItem.getStartDate()));
                preparedStmt.setInt(7,forecastItem.getNumberOfPayments());
                preparedStmt.setDate(8, Utility.calendarDateToSqlDate(forecastItem.getEndDate()));
                preparedStmt.setString(9, forecastItem.getItemType());
                preparedStmt.setString(10, forecastItem.getHowPaid());
                preparedStmt.setString(12, idForecast.toString());
                preparedStmt.setString(13, forecastItem.getIdBudgetItem().toString());
                preparedStmt.execute();
                forecastItem = forecastItem.getNextItem();
            }

            // Insert the forecast transaction tuples:
            errorMessage = "SQL error attempting to insert a forecast transaction into the database.";
            query = "insert into forecastdatabase.forecasttransaction (idForecastTransaction, plannedDate, " +
                    "ForecastItem_idForecastItem, Transaction_idTransaction) values (UUID_TO_BIN(?), " +
                    "?, UUID_TO_BIN(?), UUID_TO_BIN(?))";
            preparedStmt = dbConnection.prepareStatement(query);
            for (int i = 0; i < this.transactions.length; i++) {
                if (this.transactions[i] != null) {
                    ForecastTransaction forecastTransaction = this.transactions[i];
                    while (forecastTransaction != null)
                    {
                        preparedStmt.setString(1, forecastTransaction.getId().toString());
                        preparedStmt.setDate(2, new java.sql.Date(forecastTransaction.getPlannedDate().getTimeInMillis()));
                        preparedStmt.setString(3, forecastTransaction.getForecastItem().getId().toString());
                        if (forecastTransaction.getTransaction() != null) {
                            preparedStmt.setString(4, forecastTransaction.getTransaction().getId().toString());
                        } else {
                            preparedStmt.setString(4, null);
                        }
                        preparedStmt.execute();
                        forecastTransaction = forecastTransaction.getNextTransaction();
                    }
                }
            }

        } catch (SQLException e) {
            System.out.println(errorMessage);
            if (preparedStmt != null) preparedStmt.close();
            throw e;
        }
    } // End Forecast.save().

    // Add an item to the linked list of items in the forecast:
    public void addForecastItem(ForecastItem forecastItem) {
        if (firstForecastItem == null) {
            firstForecastItem = forecastItem;
        } else {
            lastForecastItem.setNextItem(forecastItem);
        }
        lastForecastItem = forecastItem;
    }

    // The types of significant events that can be generated:
    public enum SignificantEvents { daysBelowMinimumBalance }
}
