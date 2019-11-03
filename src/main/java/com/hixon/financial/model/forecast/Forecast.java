package com.hixon.financial.model.forecast;

import com.hixon.financial.Utility;
import com.hixon.financial.model.EntityException;
import com.hixon.financial.model.EntityInt;
import com.hixon.financial.model.IndependentEntity;
import com.hixon.financial.model.budget.BudgetException;
import com.hixon.financial.model.budget.Item;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.UUID;

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
    protected String budgetname;
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
    public enum SignificantEvents { daysBelowMinimumBalance }


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
    public boolean getInSync() {return inSync;}
    public void setInSync(boolean inSync) {this.inSync = inSync; setDirty(true);}

    @Override
    public String getInsertQuery() {
        return insertQuery + "uuid_to_bin('" + id + ", " + description + ", " +
                Utility.calendarDateToSqlDateString(dateGenerated) + ", " +
                Utility.calendarDateToSqlDateString(startDate) + ", " + startingBalance +
                Utility.calendarDateToSqlDateString(endDate) + ", " + endingBalance + ", " +  numberOfMonths + ", " +
                " uuid_to_bin('" + idBudget + "')";
    }

    @Override
    public String getInsertOnDuplicateUpdateQuery() throws BudgetException {
        return null;
    }

    @Override
    public String getUpdateQuery() {
        return updateQuery + "description = '" + description + "', " +
                "dateGenerated = " + Utility.calendarDateToSqlDateString(dateGenerated) + ", startDate = " +
                Utility.calendarDateToSqlDateString(startDate) + ", startingBalance = " + startingBalance + ", " +
                "endDate = " + Utility.calendarDateToSqlDateString(endDate) + ", endingBalance = " + endingBalance +
                ", numberOfMonths = " +  numberOfMonths + ", Budget_idBudget = uuid_to_bin('" + idBudget + "') " +
                "where idForecast = uuid_to_bin('" + id + "')";
    }

    @Override
    public String getDeleteQuery() {
        return deleteQuery + "id = uuid_to_bin('" + id + "')";
    }

    @Override
    public String getEntityTypeName() {
        return "forecast";
    }

    /*
     * Forecast class constructors:
     */
    public Forecast(String budgetName, Calendar startDate, double startingBalance, double minimumBalance,
                    int numberOfMonths) throws ForecastException, SQLException {

        super(true);
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
     * Load and save methods:
     */
    public static Forecast getMostRecent() throws EntityException, SQLException {
        String selectMostRecentQuery = selectQuery + "order by dateGenerated desc";
        ResultSet rs = EntityInt.getSingletonRS(selectMostRecentQuery, "Database error occurred " +
                "trying to retrieve the most recent forecast.");
        Forecast forecast = null;
        if (rs != null) {
            forecast = new Forecast(rs);
        }
        return forecast;
    }

    //  Save the forecast object:


    // Save the all of the forecast to the database:
    public void saveAll(Connection dbConnection) throws SQLException, BudgetException, EntityException, ForecastException {
        PreparedStatement preparedStmt = null;
        String errorMessage = null;
        try {
            // Insert the forecast tuple:
            errorMessage = "SQL error attempting to insert the Forecast object into the database.";
            String query = "insert into ForecastDatabase.Forecast (idForecast, description, dateGenerated, " +
                    "startDate, startingBalance, endDate, endingBalance, numberOfMonths, inSync, Budget_idBudget) " +
                    "values(UUID_TO_BIN(?), ?, ?, ?, ?, ?, ?, ?, ?, UUID_TO_BIN(?))";
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
            preparedStmt.execute();

            // Insert the forecast item tuples:
            errorMessage = "SQL error attempting to insert a forecast item into the database.";
            query = "insert into ForecastDatabase.Forecast_Item (idForecastItem, category, payee, period, amount, " +
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
                preparedStmt.setDate(6,  Utility.calendarDateToSqlDate(forecastItem.getStartDate()));
                preparedStmt.setInt(7,forecastItem.getNumberOfPayments());
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

            // Insert the forecast transaction tuples:
            errorMessage = "SQL error attempting to insert a forecast transaction into the database.";
            query = "insert into ForecastDatabase.Forecast_Transaction (idForecastTransaction, remainingAmount, " +
                    "plannedDate, firstOccurrence, ForecastItem_idForecastItem) values (UUID_TO_BIN(?), ?, ?, ?, " +
                    "UUID_TO_BIN(?))";
            preparedStmt = dbConnection.prepareStatement(query);
            for (int i = 0; i < this.transactions.length; i++) {
                if (this.transactions[i] != null) {
                    ForecastTransaction forecastTransaction = this.transactions[i];
                    while (forecastTransaction != null)
                    {
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
    } // End Forecast.save().


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
    public void addTransactionOnDate(ForecastItem forecastItem, Calendar nextDate, boolean firstOccurrence) throws Exception {

        // Calculate the index to assign this transaction by calculating the number of days between
        // the start date of the forecast and the day this transaction occurs:
        int index = (int) ChronoUnit.DAYS.between(startDate.toInstant(), nextDate.toInstant());

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
    public void summarize(SignificantEvents[] events) throws EntityException, SQLException, ForecastException, BudgetException {

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
            lastSignificantEvent = forecastTransaction;
        } else {
            lastSignificantEvent.setNextSignificantEvent(forecastTransaction);
            lastSignificantEvent = forecastTransaction;
        }
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
}
