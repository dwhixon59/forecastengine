package com.hixon.financialApp.model.forecast;

import com.hixon.financialApp.controller.QuitException;
import com.hixon.financialApp.controller.SkipException;
import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.Item;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.entity.IndependentEntity;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.base.ViewInt;
import lombok.Getter;

import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.hixon.financialApp.model.entity.EntityInt.getSingletonRS;


/**
 * Forecast class represents a financial forecast for a budget over a specified period of time.
 * It includes methods to create, save, and manage forecast items and transactions.
 * It also provides functionality to summarize the forecast and generate significant events.
 */
public class Forecast extends IndependentEntity {

    //    private static final Logger logger = LogManager.getLogger(Forecast.class);

    // The types of significant events that can be generated:
    public enum SignificantEvents {daysBelowMinimumBalance}

    /*
     * Forecast class fields:
     */
    // Fields from the database:
    protected String description;
    protected UUID idBudget;
    @Getter
    protected Calendar startDate;
    protected Calendar dateGenerated;
    protected double startingBalance;
    @Getter
    protected Item.PeriodType payPeriod = Item.PeriodType.SEMIMONTHLY;

    // Derived fields:
    protected Calendar endDate;
    protected double minimumBalance;
    protected double endingBalance;
    protected int numberOfMonths;
    protected String budgetName;

    private ForecastItem firstForecastItem = null;
    private ForecastItem lastForecastItem = null;

    protected ForecastTransaction[] transactions = null;
    private ForecastTransaction firstSignificantEvent = null;
    private ForecastTransaction lastSignificantEvent = null;

    private boolean inSync = true;

    private static final String selectQuery = "select bin_to_uuid(idForecast) as idForecast, description, " +
            "dateGenerated, startDate, startingBalance, endDate, endingBalance, numberOfMonths, " +
            "bin_to_uuid(Budget_idBudget) as idBudget from forecast ";

    public static String getSelectQuery() {
        return selectQuery;
    }

    private static final String insertQuery = "insert into forecast (idForecast, description, " +
            "dateGenerated, startDate, startingBalance, endDate, endingBalance, numberOfMonths, Budget_idBudget) " +
            "values (";
    private static final String updateQuery = "update forecast set ";
    private static final String deleteQuery = "delete from forecast where ";

    // inside Forecast
    private static class OverrideKey {
        private final UUID forecastItemId;
        private final LocalDate date;

        OverrideKey(UUID forecastItemId, LocalDate date) {
            this.forecastItemId = forecastItemId;
            this.date = date;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof OverrideKey)) return false;
            OverrideKey that = (OverrideKey) o;
            return Objects.equals(forecastItemId, that.forecastItemId)
                    && Objects.equals(date, that.date);
        }

        @Override
        public int hashCode() {
            return Objects.hash(forecastItemId, date);
        }
    }

    private Set<OverrideKey> overriddenTransactionKeys;  // lazily loaded

    /*
     * Forecast class getters and setters:
     */
    public String getName() {
        return getDescription();
    }

    public String getDescription() {
        return description;
    }

    public void setForecastName(String description) {
        this.description = description;
        setDirty(true);
    }

    public void setBudgetId(UUID idBudget) {
        this.idBudget = idBudget;
        setDirty(true);
    }

    public void setStartDate(Calendar forecastStartDate) {
        this.startDate = forecastStartDate;
        setDirty(true);
    }

    public Calendar getEndDate() {
        return endDate;
    }

    public void setEndDate(Calendar endDate) {
        this.endDate = endDate;
        setDirty(true);
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

    public void setNumberOfMonths(int numberOfMonths) {
        this.numberOfMonths = numberOfMonths;
        setDirty(true);
    }

    public ForecastItem getFirstForecastItem() {
        return this.firstForecastItem = firstForecastItem;
    }

    public void setFirstForecastItem(ForecastItem firstForecastItem) {
        this.firstForecastItem = firstForecastItem;
    }

    public ForecastItem getLastForecastItem() {
        return this.lastForecastItem = lastForecastItem;
    }

    public void setLastForecastItem(ForecastItem lastForecastItem) {
        this.lastForecastItem = lastForecastItem;
    }

    ForecastTransaction[] getTransactions() {
        return transactions;
    }

    public void setTransactions(ForecastTransaction[] forecastTransactions) {
        this.transactions = forecastTransactions;
    }

    public ForecastTransaction getFirstSignificantEvent() {
        return firstSignificantEvent;
    }

    public List<Envelope> getEnvelopes() throws Exception {

        // Get a list of all the forecast transaction in the forecast:
        List<ForecastItem> envelopes =
                Collections.unmodifiableList(ForecastItem.getListOfAllUsableForecastItemsInForecast(this));

        // Make the forecast items into envelopes:
        List<Envelope> envelopesList = new ArrayList<>();
        for (ForecastItem forecastItem : envelopes) {
            envelopesList.add(new Envelope(forecastItem));
        }

        // Sort the envelopes by name:
        envelopesList.sort(Comparator.comparing(Envelope::getName));

        return envelopesList;
    }

    public boolean getInSync() {
        return inSync;
    }

    public void setInSync(boolean inSync) {
        this.inSync = inSync;
        setDirty(true);
    }


    /**
     * Validate the fields of an object.  Every entity is required to provide a method that validates the contents of
     * the entity.
     *
     * @return true if the object is valid
     */
    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public String getInsertQuery() {
        String descVal = description != null ? "'" + description + "'" : "NULL";
        String dateGenVal = dateGenerated != null ? Utility.calendarDateToSqlDateString(dateGenerated) : "NULL";
        String startDateVal = startDate != null ? Utility.calendarDateToSqlDateString(startDate) : "NULL";
        String endDateVal = endDate != null ? Utility.calendarDateToSqlDateString(endDate) : "NULL";

        return insertQuery + "uuid_to_bin('" + id + "'), " +
                descVal + ", " +
                dateGenVal + ", " +
                startDateVal + ", " +
                startingBalance + ", " +
                endDateVal + ", " +
                endingBalance + ", " +
                numberOfMonths + ", " +
                "uuid_to_bin('" + idBudget + "'))";
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
    public String getPrintableTypeName() {
        return getPrintableTypeName_static();
    }

    public static String getPrintableTypeName_static() {
        return "forecast";
    }


    /*
     * Constructors:
     */
    public Forecast(Budget budget, Calendar startDate, int numberOfMonths, double startingBalance,
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
        this.budgetName = budget.getName();
        this.idBudget = budget.getId();

    }

    // Create a forecast from the database:
    Forecast(ResultSet rs) throws SQLException {
        super(false);
        this.id = UUID.fromString(rs.getString("idForecast"));
        this.description = rs.getString("description");
        this.dateGenerated = Utility.localDateToCalendarDate(rs.getObject("dateGenerated", LocalDate.class));
        this.startDate = Utility.SqlDateToCalendarDate(rs.getDate("startDate"));
        this.startingBalance = rs.getDouble("startingBalance");
        this.endDate = Utility.localDateToCalendarDate(rs.getObject("endDate", LocalDate.class));
        this.endingBalance = rs.getDouble("endingBalance");
        this.numberOfMonths = rs.getInt("numberOfMonths");
        this.idBudget = UUID.fromString(rs.getString("idBudget"));
    }

    // Default constructor for creating new forecasts:
    public Forecast() {
        super(false);
        this.dateGenerated = Calendar.getInstance();
        this.startingBalance = 0.0;
        this.endingBalance = 0.0;
        this.numberOfMonths = 0;
    }

    /*
     * CRUD methods:
     */

    public static Forecast getById(UUID idForecast) throws EntityException, SQLException {
        return new Forecast(EntityInt.getRSById(selectQuery + "where idForecast = ", idForecast,
                "Database error encountered trying to retrieve a forecast."));
    }

    public static Forecast getByName(String name) throws EntityException, SQLException {
        return new Forecast(EntityInt.getSingletonRS(selectQuery + "where description = '" + name + "'",
                "Database error encountered trying to retrieve the forecast with description " + name + "."));
    }

    /**
     * Select a forecast by name from a list of all the forecasts for a particular budget in the database.
     *
     * @return Forecast The forecast that was selected.
     * @throws ForecastException If there are no forecasts in the database or if no forecast was selected.
     * @throws SQLException      If there is a database error.
     * @throws EntityException   If there is an error non-database error.
     * @throws SkipException
     * @throws QuitException
     */
    public static Forecast selectForecast(Budget budget) throws ForecastException, SQLException, EntityException,
            SkipException, QuitException {

        // Get a list of all the forecasts for the budget:
        List<Forecast> forecasts = Forecast.getListOf(budget);

        // If there are no forecasts, offer to create one:
        if (forecasts.size() == 0) {
            Utility.getView().say("No forecasts exist for budget '" + budget.getName() + "'.");

            if (Utility.getView().getYesOrNo("Would you like to create a forecast for this budget?")) {
                // Create a new forecast
                Forecast newForecast = new Forecast();
                newForecast.setId(java.util.UUID.randomUUID());
                newForecast.setForecastName(budget.getName() + " Forecast");
                newForecast.setBudgetId(budget.getId());
                newForecast.setStartDate(java.util.Calendar.getInstance());
                newForecast.setNumberOfMonths(12);  // Default to 12 months (1 year) forecast period

                try {
                    newForecast.insert();
                    Utility.getView().say("✓ Forecast '" + newForecast.getDescription() + "' created successfully.");
                    return newForecast;
                } catch (Exception e) {
                    throw new ForecastException("Error creating forecast: " + e.getMessage());
                }
            } else {
                throw new ForecastException("No forecast available for budget '" + budget.getName() + "'. Cannot proceed.");
            }
        }

        // If there is only one forecast, return it:
        if (forecasts.size() == 1) {
            return forecasts.get(0);
        }

        // Otherwise, let the user select a forecast:
        Forecast forecast = Utility.getView().selectByNameFromList("Select a forecast:", forecasts,
                ViewInt.DO_NOT_ALLOW_NONE);

        // If a forecast was selected, return it, else throw an exception:
        if (forecast != null) {
            return forecast;
        } else {
            throw new ForecastException("No forecast was selected.");
        }
    }

    /**
     * Retrieves all forecasts associated with a given budget.
     * 
     * @param budget The budget whose forecasts should be retrieved
     * @return A list of Forecast objects for the given budget
     * @throws ForecastException If an error occurs while retrieving forecasts
     */
    public static List<Forecast> getListOf(Budget budget) throws ForecastException {
        try (Statement statement = Utility.getDbConnection().createStatement()) {

            ResultSet rs;
            String query = selectQuery + " where Budget_idBudget = uuid_to_bin('" + budget.getId() + "') order by " +
                    "description";
            rs = statement.executeQuery(query);
            List<Forecast> forecasts = new ArrayList<>();
            while (rs.next()) {
                Forecast forecast = new Forecast(rs);
                forecasts.add(forecast);
            }
            return forecasts;

        } catch (Exception e) {
            ForecastException fe = new ForecastException("Error occurred trying to retrieve a list of forcasts with the " +
                    "sql statement " + selectQuery);
            fe.initCause(e);
            throw fe;
        }
    }

    /**
     * Get the date of the first forecast transaction in the forecast that is not a placeholder and has a non-zero
     * remaining amount (which means it is a real transaction).  This is the date that the forecast starts on.  If there
     * are no forecast transactions, then return today's date.
     *
     * @return The date that the forecast is considered to start on.
     * @throws SQLException    If there is a database error.
     * @throws EntityException If there is an error non-database error.
     */
    public static Calendar getFirstNonZeroTransactionDate(Forecast forecast) throws EntityException, SQLException {
        String query =
                "select MIN(plannedDate) as 'ft.plannedDate' " +
                        "from forecast_transaction ft inner join forecast_item fi on ft.ForecastItem_idForecastItem = fi.idForecastItem " +
                        "where ft.remainingAmount <>0 and fi.Forecast_idForecast = uuid_to_bin('" + forecast.getId() + "')";
        ResultSet rs = EntityInt.getSingletonRS(query, "Database error encountered trying to retrieve the " +
                "forecast start date.");
        if (rs != null) {
            return Utility.SqlDateToCalendarDate(rs.getDate("ft.plannedDate"));
        } else {
            return Calendar.getInstance();
        }
    }

    /**
     * Gets the most recent forecast across ALL budgets in the system.
     * @return The most recent forecast, or null if no forecasts exist
     * @throws EntityException If a database error occurs
     * @throws SQLException If a SQL error occurs
     * @deprecated Use {@link #getMostRecent(Budget)} instead to avoid cross-budget contamination.
     * This method retrieves the globally most recent forecast regardless of budget, which may not be
     * what you want when working with a specific register/budget.
     */
    @Deprecated
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

    /**
     * Gets the most recent forecast for a specific budget.
     * @param budget The budget whose most recent forecast should be retrieved
     * @return The most recent forecast for the budget, or null if no forecasts exist for that budget
     * @throws EntityException If a database error occurs
     * @throws SQLException If a SQL error occurs
     */
    public static Forecast getMostRecent(Budget budget) throws EntityException, SQLException {
        String selectMostRecentQuery = selectQuery +
                "where Budget_idBudget = uuid_to_bin('" + budget.getId() + "') " +
                "order by dateGenerated desc";
        ResultSet rs = getSingletonRS(selectMostRecentQuery, "Database error occurred " +
                "trying to retrieve the most recent forecast for budget " + budget.getName() + ".");
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
            String query = "insert into forecast (idForecast, description, dateGenerated, " +
                    "startDate, startingBalance, endDate, endingBalance, numberOfMonths, inSync, Budget_idBudget) " +
                    "values(UUID_TO_BIN(?), ?, ?, ?, ?, ?, ?, ?, ?, UUID_TO_BIN(?)) on duplicate key update " +
                    "description = ?, dateGenerated = ?, startDate =?, startingBalance = ?, endDate = ?, " +
                    "endingBalance = ?, numberOfMonths = ?, inSync = ?";
            preparedStmt = dbConnection.prepareStatement(query);
            preparedStmt.setString(1, id.toString());
            preparedStmt.setString(2, description);
            preparedStmt.setObject(3, new java.sql.Timestamp(System.currentTimeMillis()));
            preparedStmt.setDate(4, new java.sql.Date(startDate.getTimeInMillis()));
            preparedStmt.setDouble(5, startingBalance);
            preparedStmt.setDate(6, new java.sql.Date(endDate.getTimeInMillis()));
            preparedStmt.setDouble(7, endingBalance);
            preparedStmt.setInt(8, numberOfMonths);
            preparedStmt.setBoolean(9, true);
            preparedStmt.setString(10, idBudget.toString());
            preparedStmt.setString(11, description);
            preparedStmt.setObject(12, new java.sql.Timestamp(System.currentTimeMillis()));
            preparedStmt.setDate(13, new java.sql.Date(startDate.getTimeInMillis()));
            preparedStmt.setDouble(14, startingBalance);
            preparedStmt.setDate(15, new java.sql.Date(endDate.getTimeInMillis()));
            preparedStmt.setDouble(16, endingBalance);
            preparedStmt.setInt(17, numberOfMonths);
            preparedStmt.setBoolean(18, true);
            preparedStmt.execute();

            // Mark the forecast as saved:
            setDirty(false);

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
            String query = "insert into forecast_item (idForecastItem, category, payee, period, amount, " +
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

    // Save the forecast transactions to the database:
    public void saveForecastTransactions() throws SQLException, BudgetException, EntityException, ForecastException {
        Connection dbConnection = Utility.getDbConnection();
        String errorMessage = null;
        try {
            // Insert the forecast transaction tuples:
            errorMessage = "SQL error attempting to insert a forecast transaction into the database.";
            String query = "insert into forecast_transaction (idForecastTransaction, remainingAmount, " +
                    "plannedDate, runningBalance, firstOccurrence, memo, ForecastItem_idForecastItem) values (UUID_TO_BIN(?), " +
                    "?, ?, ?, ?, ?, UUID_TO_BIN(?))";
            try (PreparedStatement preparedStmt = dbConnection.prepareStatement(query)) {
                for (ForecastTransaction transaction : this.transactions) {
                    if (transaction != null) {
                        ForecastTransaction forecastTransaction = transaction;
                        Utility.getView().say("Updating " + forecastTransaction.getForecastItem().toStringShort());
                        while (forecastTransaction != null) {
                            preparedStmt.setString(1, forecastTransaction.getId().toString());
                            preparedStmt.setDouble(2, forecastTransaction.getRemainingAmount());
                            preparedStmt.setDate(3, new java.sql.Date(forecastTransaction.getPlannedDate().getTimeInMillis()));
                            preparedStmt.setDouble(4, forecastTransaction.getRunningBalance());
                            preparedStmt.setBoolean(5, forecastTransaction.isFirstOccurrence());
                            preparedStmt.setString(6, forecastTransaction.getMemo());
                            preparedStmt.setString(7, forecastTransaction.getForecastItem().getId().toString());
                            preparedStmt.execute();
                            forecastTransaction = forecastTransaction.getNextTransaction();
                        }
                    }
                }
            }
        } catch (SQLException | BudgetException | EntityException | ForecastException e) {
            System.out.println(errorMessage);
            throw e;
        }
    } // End saveForecastTransactions().

    /**
     * Detects and reports duplicate forecast transactions.
     * Finds transactions with the same ForecastItem + plannedDate combination but different IDs,
     * which indicates logical duplicates that should not exist.
     *
     * NOTE: Transactions linked to different splits are NOT considered duplicates - they represent
     * different actual transactions that matched the same forecast item on the same date.
     */
    public void checkForDuplicateTransactions() throws SQLException {
        String query =
            "SELECT " +
            "    BIN_TO_UUID(fi.idForecastItem) as forecastItemId, " +
            "    fi.category, " +
            "    fi.payee, " +
            "    ft.plannedDate, " +
            "    COUNT(*) as duplicateCount, " +
            "    GROUP_CONCAT(BIN_TO_UUID(ft.idForecastTransaction) ORDER BY ft.updatedTimeStamp DESC SEPARATOR ', ') as transactionIds, " +
            "    COUNT(DISTINCT fts.Transaction_Split_idTransaction) as distinctTransactionCount " +
            "FROM forecast_transaction ft " +
            "INNER JOIN forecast_item fi ON ft.ForecastItem_idForecastItem = fi.idForecastItem " +
            "LEFT JOIN forecast_transaction_split fts ON ft.idForecastTransaction = fts.ForecastTransaction_idForecastTransaction " +
            "WHERE fi.Forecast_idForecast = UUID_TO_BIN('" + this.getId() + "') " +
            "  AND ft.plannedDate >= " + Utility.calendarDateToSqlDateString(this.startDate) + " " +
            "GROUP BY fi.idForecastItem, ft.plannedDate " +
            "HAVING COUNT(*) > 1 " +
            "  AND (COUNT(DISTINCT fts.Transaction_Split_idTransaction) <= 1 OR COUNT(DISTINCT fts.Transaction_Split_idTransaction) IS NULL) " +
            "ORDER BY ft.plannedDate DESC, fi.category, fi.payee";

        try (Statement statement = Utility.getDbConnection().createStatement();
             ResultSet rs = statement.executeQuery(query)) {

            boolean foundDuplicates = false;
            while (rs.next()) {
                if (!foundDuplicates) {
                    Utility.getView().say("");
                    Utility.getView().say("WARNING: Duplicate forecast transactions detected!");
                    Utility.getView().say("=========================================");
                    foundDuplicates = true;
                }

                String category = rs.getString("category");
                String payee = rs.getString("payee");
                LocalDate plannedDate = rs.getObject("plannedDate", LocalDate.class);
                int count = rs.getInt("duplicateCount");
                String transactionIds = rs.getString("transactionIds");

                Utility.getView().say(String.format(
                    "  [%s] %s - %s: %d duplicates",
                    plannedDate.toString(),
                    category,
                    payee,
                    count
                ));
                Utility.getView().say("    Transaction IDs: " + transactionIds);
            }

            if (foundDuplicates) {
                Utility.getView().say("=========================================");
                Utility.getView().say("Run cleanup_duplicate_forecast_transactions.sql to remove duplicates.");
                Utility.getView().say("");
            }
        } catch (SQLException e) {
            System.err.println("Error checking for duplicate forecast transactions: " + e.getMessage());
            throw e;
        }
    } // End checkForDuplicateTransactions().

    // Save the entire forecast to the database, including all the forecast items and forecast transactions:
    public void saveAll() throws SQLException, BudgetException, EntityException, ForecastException {

        // Save the forecast:
        save();
        saveForecastItems();
        saveForecastTransactions();

    } // End saveAll().


    /*
     *  Helper methods:
     */
    public void createTransactionsArray() {
        this.transactions = new ForecastTransaction[numberOfMonths * 31];
    }

    public Budget getBudget() throws EntityException, BudgetException, SQLException {
        Budget budget = Budget.getById(idBudget);
        return budget;
    }

    /**
     * Get the number of months remaining until the forecast's end date from the passed in date.
     * @param startDate The date to start counting from.
     * @return The number of months remaining.
     */
    public double getMonthsRemaining(Calendar startDate) {
        // Convert Calendar to YearMonth
        YearMonth startYearMonth = YearMonth.from(startDate.toInstant().atZone(ZoneId.systemDefault()));
        YearMonth endYearMonth = YearMonth.from(this.endDate.toInstant().atZone(ZoneId.systemDefault()));

        // Calculate the difference in months
        return ChronoUnit.MONTHS.between(startYearMonth, endYearMonth);
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

    /**
     * Compute the ending daily balance for a range of future dates.  The algorithm is to use the current balance of the
     * register associated with this forecast, then apply all the non-zero remaining amount forecast transactions up to
     * the end date of the date range.  The ending balance of each day in the date range is computed and saved in a daily
     * balance object.
     *
     * @param forecast  The forecast that this request for daily balances applies to.
     * @param startDate The starting date of the range of daily balances requested.
     * @param endDate   The end date of the range of daily balances requested.
     * @return A list of DailyBalance objects (one for each day) in chronological order.
     */
    public static List<DailyBalance> getDailyBalanceList(Forecast forecast, Calendar startDate, Calendar endDate)
            throws EntityException, BudgetException, Exception, RegisterException {

        List<DailyBalance> dailyBalances = new ArrayList<>();

        // Get the current balance of the applicable register:
        double balance = forecast.getBudget().getRegisters().get(0).getBalance();

        // Get a chronological list of the non-zero transactions in the forecast:
        ForecastTransactionIterator forecastTransactions = ForecastTransaction.getNonZeroForecastTransactions(forecast);

        // Compute the forward balances saving the ending balance for each day in the date range:
        ForecastTransaction forecastTransaction = forecastTransactions.getNext();
        while (forecastTransaction != null) {

            // Subtract of the remaining amount of the current forecast transaction:
            balance += forecastTransaction.remainingAmount;

            // Save the date of the current forecast transaction:
            Calendar forecastTransactionDate = forecastTransaction.getPlannedDate();

            // Get the next forecast transaction in the list:
            forecastTransaction = forecastTransactions.getNext();

            // If the planned date of the current forecast transaction is within the date range:
            if (forecastTransaction != null) {
                if (forecastTransaction.getPlannedDate().compareTo(startDate) >= 0) {

                    // If the date changed:
                    if (forecastTransaction.getPlannedDate().compareTo(forecastTransactionDate) > 0) {

                        // then the current balance is the ending balance on the date of the previous forecast transaction, so
                        // add it to the daily balances list.  There may be missing days between the date of the last forecast
                        // transaction and the current one, so fill them in as well:
                        int daysBeteween = Utility.daysBetween(forecastTransactionDate, forecastTransaction.getPlannedDate());
                        for (int i = 0; i < daysBeteween; i++) {
                            DailyBalance dailyBalance = new DailyBalance(forecastTransactionDate, balance);
                            dailyBalances.add(dailyBalance);
                        }
                    }
                }

                // and if the date of this forecast transaction is after the end of the date range:
                if (forecastTransaction.getPlannedDate().compareTo(endDate) > 0) {

                    // then we are done
                    forecastTransaction = null;
                }
            }
        }

        return dailyBalances;
    }

    private void loadOverriddenTransactionKeys() {
        if (overriddenTransactionKeys != null) {
            return; // already loaded
        }

        Set<OverrideKey> result = new HashSet<>();

        String sql =
                "SELECT BIN_TO_UUID(fi.idForecastItem) AS idForecastItem, ft.plannedDate " +
                        "FROM forecast_transaction ft " +
                        "INNER JOIN forecast_item fi ON ft.ForecastItem_idForecastItem = fi.idForecastItem " +
                        "WHERE fi.Forecast_idForecast = UUID_TO_BIN(?) " +
                        "AND ft.overridden = TRUE";

        try (PreparedStatement ps = Utility.getDbConnection().prepareStatement(sql)) {
            ps.setString(1, this.getId().toString());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID itemId = UUID.fromString(rs.getString("idForecastItem"));
                    LocalDate date = rs.getDate("plannedDate").toLocalDate();
                    result.add(new OverrideKey(itemId, date));
                }
            }
        } catch (SQLException e) {
            // log as needed
            result = Collections.emptySet();
        }

        this.overriddenTransactionKeys = result;
    }


    /**
     * Checks if this forecast contains an overridden forecast transaction for the given forecast item and date.
     * @param forecastItem the forecast item
     * @param date the planned date
     * @return true if an overridden transaction exists for this forecast item and date in this forecast, false otherwise
     */
    public boolean hasOverriddenForecastTransactionOnDate(ForecastItem forecastItem, Calendar date) {
        if (forecastItem == null || date == null) {
            return false;
        }

        loadOverriddenTransactionKeys();

        LocalDate localDate = date.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();

        OverrideKey key = new OverrideKey(forecastItem.getId(), localDate);
        return overriddenTransactionKeys.contains(key);
    }

    /**
     * Checks if this forecast contains a reconciled forecast transaction for the given forecast item and date.
     * A reconciled transaction is one that has been matched to actual transactions (found=true or has splits).
     * This prevents the forecast update process from creating duplicate transactions.
     *
     * @param forecastItem the forecast item
     * @param date the planned date
     * @return true if a reconciled transaction exists for this forecast item and date in this forecast, false otherwise
     */
    public boolean hasReconciledForecastTransactionOnDate(ForecastItem forecastItem, Calendar date) {
        if (forecastItem == null || date == null) {
            return false;
        }

        String sql =
                "SELECT COUNT(*) as count " +
                "FROM forecast_transaction ft " +
                "INNER JOIN forecast_item fi ON ft.ForecastItem_idForecastItem = fi.idForecastItem " +
                "WHERE fi.Forecast_idForecast = UUID_TO_BIN(?) " +
                "  AND fi.idForecastItem = UUID_TO_BIN(?) " +
                "  AND ft.plannedDate = ? " +
                "  AND (ft.found = TRUE OR EXISTS (" +
                "    SELECT 1 FROM forecast_transaction_split fts " +
                "    WHERE fts.ForecastTransaction_idForecastTransaction = ft.idForecastTransaction" +
                "  ))";

        try (PreparedStatement ps = Utility.getDbConnection().prepareStatement(sql)) {
            ps.setString(1, this.getId().toString());
            ps.setString(2, forecastItem.getId().toString());

            LocalDate localDate = date.toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
            ps.setDate(3, java.sql.Date.valueOf(localDate));

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count") > 0;
                }
            }
        } catch (SQLException e) {
            // Log as needed, default to false to avoid blocking forecast generation
            System.err.println("Error checking for reconciled forecast transaction: " + e.getMessage());
        }

        return false;
    }

}
