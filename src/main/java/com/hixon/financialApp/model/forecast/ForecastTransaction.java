package com.hixon.financialApp.model.forecast;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.ItemOfInterest;
import com.hixon.financialApp.model.entity.Entity;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.entity.IndependentEntity;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.utility.Utility;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

import static com.hixon.financialApp.model.entity.EntityInt.SaveMethod.UPDATE;
import static com.hixon.financialApp.model.entity.EntityInt.executeUpdate;
import static com.hixon.financialApp.utility.Utility.*;

/**
 * This the class that represents a single transaction in the forecast.
 */
public class ForecastTransaction extends IndependentEntity {

    //private static final Logger logger = LogManager.getLogger(ForecastTransaction.class);

    /*
     * Fields:
     */
    // Version:
    protected Calendar version;

    // Amount of the transaction in case of an override:
    protected double remainingAmount = 0;

    // The date that this transaction is expected to occur, or is due:
    protected Calendar plannedDate;

    // A description that further refines the semantics of this forecast transaction.  For example if the category is
    // "automotive" and the payee is "maintenance", the description would be something like "oil change":
    protected String memo = null;

    // The id of the forecast item that this transaction is an instance of:
    protected UUID idForecastItem = null;

    // Indicates if this is the first occurrence of this forecast item in the forecast:
    protected boolean firstOccurrence = false;

    // Flag indicating if the transaction has been manually entered into the forecast, or the user has changed the
    // date or amount of a generated transaction.  It is used to determine if transaction can be deleted and
    // regenerated from the database.  If it is true, then the answer is no.
    protected boolean overridden = false;

    // Indicates if a transaction imported from some external source has a corresponding transaction int the database:
    protected boolean found = false;

    // Running runningBalance of the forecast:
    protected double runningBalance = 0;

    // A reference to the forecast item that this transaction is an occurrence of;
    protected ForecastItem forecastItem = null;

    // A pointer to the next transaction on the same date:
    protected ForecastTransaction nextTransaction = null;

    // A pointer to the next transaction in the list of significant transactions:
    protected ForecastTransaction nextSignificantEvent = null;

    /*
     * Getters and setters:
     */
    public Calendar getVersion() {
        return version;
    }

    public void setVersion(Calendar version) {
        this.version = version;
    }

    public Calendar getPlannedDate() {
        return plannedDate;
    }

    public void setPlannedDate(Calendar date) {
        this.plannedDate = date;
        setDirty(true);
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
        setDirty(true);
    }

    public double getRemainingAmount() {
        return remainingAmount;
    }

    public void setRemainingAmount(double remainingAmount) {
        this.remainingAmount = remainingAmount;
        setDirty(true);
    }

    public boolean isOverridden() {
        return overridden;
    }

    public void setOverridden(boolean overridden) {
        this.overridden = overridden;
        setDirty(true);
    }

    public boolean isFound() {
        return found;
    }

    public void setFound(boolean found) {
        this.found = found;
        setDirty(true);
    }

    public static void setAllFound(boolean found) throws EntityException, RegisterException {
        throw new EntityException("setAllFound(boolean) is deprecated and should not be used. Use setAllFound(Forecast, boolean) instead to avoid cross-forecast contamination.");
    }

    /**
     * Sets the found flag for all forecast transactions in a specific forecast.
     * @param forecast The forecast whose transactions should be updated
     * @param found The value to set the found flag to
     * @throws EntityException If a database error occurs
     * @throws RegisterException If a register error occurs
     */
    public static void setAllFound(Forecast forecast, boolean found) throws EntityException, RegisterException {
        String foundString = (found) ? "true" : "false";
        String query = "update forecast_transaction ft " +
                "inner join forecast_item fi on ft.ForecastItem_idForecastItem = fi.idForecastItem " +
                "set ft.found = " + foundString + " " +
                "where fi.Forecast_idForecast = uuid_to_bin('" + forecast.getId() + "')";
        executeUpdate(query, "attempting to set all the Forecast Transaction found flags " +
                "to " + foundString + " for forecast " + forecast.getId() + ".");
    }

    public double getRunningBalance() {
        return runningBalance;
    }

    public void setRunningBalance(double runningBalance) {
        this.runningBalance = runningBalance;
        setDirty(true);
    }

    public UUID getIdForecastItem() throws EntityException, SQLException, ForecastException, BudgetException {
        if (idForecastItem == null) {
            if (forecastItem != null) {
                idForecastItem = forecastItem.getId();
            }
        }
        return idForecastItem;
    }

    public void setIdForecastItem(UUID idForecastItem) {
        this.idForecastItem = idForecastItem;
    }

    public ForecastItem getForecastItem() throws EntityException, SQLException, ForecastException, BudgetException {
        if (forecastItem == null) {
            if (idForecastItem != null) {
                forecastItem = ForecastItem.getById(idForecastItem);
            }
        }
        return forecastItem;
    }

    public void setForecastItem(ForecastItem forecastItem) {
        this.forecastItem = forecastItem;
    }

    public ForecastTransaction getNextTransaction() {
        return nextTransaction;
    }

    public void setNextTransaction(ForecastTransaction nextTransaction) {
        this.nextTransaction = nextTransaction;
        setDirty(true);
    }

    public ForecastTransaction getNextSignificantEvent() {
        return nextSignificantEvent;
    }

    public void setNextSignificantEvent(ForecastTransaction forecastTransaction) {
        nextSignificantEvent = forecastTransaction;
        setDirty(true);
    }

    public boolean isFirstOccurrence() {
        return firstOccurrence;
    }

    public void setFirstOccurrence(boolean firstOccurrence) {
        this.firstOccurrence = firstOccurrence;
    }

    @Override
    public String getName() throws EntityException {
        try {
            if (forecastItem != null) {
                return forecastItem.getCategory() + " - " + forecastItem.getPayee();
            } else if (idForecastItem != null) {
                ForecastItem item = ForecastItem.getById(idForecastItem);
                return item.getCategory() + " - " + item.getPayee();
            }
            return "";
        } catch (Exception e) {
            throw new EntityException("Error getting name for forecast transaction", e);
        }
    }


    /*
     * Constructors:
     */
    public ForecastTransaction() {
        super(true);
    }

    public ForecastTransaction(ForecastItem forecastItem, Calendar nextDate, boolean firstOccurrence) throws Exception {
        super(true);
        if (forecastItem == null || nextDate == null) throw new Exception("ForecastItem seeds cannot be null.");
        this.forecastItem = forecastItem;
        idForecastItem = forecastItem.getId();
        remainingAmount = forecastItem.getAmount();
        plannedDate = (Calendar) nextDate.clone();
        memo = forecastItem.getMemo();
        this.firstOccurrence = firstOccurrence;
        this.version = Calendar.getInstance();
    }

    public ForecastTransaction(ResultSet rs) throws SQLException {
        super(false);
        this.id = UUID.fromString(rs.getString("ft.idForecastTransaction"));
        this.plannedDate = localDateToCalendarDate(rs.getObject("ft.plannedDate", LocalDate.class));
        this.memo = rs.getString("ft.memo");
        this.firstOccurrence = rs.getBoolean("ft.firstOccurrence");
        this.overridden = rs.getBoolean("ft.overridden");
        this.found = rs.getBoolean("ft.found");
        this.remainingAmount = rs.getDouble("ft.remainingAmount");
        this.runningBalance = rs.getDouble("ft.runningBalance");
        this.version = SqlTimestampToCalendarDate(rs.getTimestamp("ft.version"));
        this.idForecastItem = UUID.fromString(rs.getString("ft.idForecastItem"));
    }

    public ForecastTransaction(ForecastTransaction forecastTransaction) throws Exception, EntityException, BudgetException {
        super(true);
        if (forecastTransaction == null) throw new Exception("Forecast transaction to copy cannot be null.");
        this.plannedDate = (Calendar) forecastTransaction.getPlannedDate().clone();
        this.memo = forecastTransaction.getMemo();
        this.firstOccurrence = forecastTransaction.isFirstOccurrence();
        this.overridden = forecastTransaction.isOverridden();
        this.found = forecastTransaction.isFound();
        this.remainingAmount = forecastTransaction.getRemainingAmount();
        this.runningBalance = forecastTransaction.getRunningBalance();
        this.idForecastItem = forecastTransaction.getIdForecastItem();
        this.version = Calendar.getInstance();
        copyDate(forecastTransaction.getVersion(), this.version);
        this.forecastItem = forecastTransaction.getForecastItem();
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


    /*
     *  CRUD methods:
     */
    // The select query:
    public static final String selectColumns = " bin_to_uuid(ft.idForecastTransaction) as 'ft.idForecastTransaction', " +
            "ft.updatedTimeStamp as 'ft.version', ft.remainingAmount as 'ft.remainingAmount', ft.plannedDate as " +
            "'ft.plannedDate', ft.memo as 'ft.memo', ft.runningBalance as 'ft.runningBalance', ft.overridden as 'ft.overridden', " +
            "ft.firstOccurrence as 'ft.firstOccurrence', ft.found as 'ft.found', " +
            "bin_to_uuid(ft.ForecastItem_idForecastItem) as 'ft.idForecastItem' ";

    public static String getSelectColumns() {
        return selectColumns;
    }

    public static final String selectQuery = "select" + selectColumns + "from forecast_transaction ft";

    public static final String getSelectQuery() {
        return selectQuery;
    }

    // The insert query:
    public static final String insertQuery = "insert into forecast_transaction (idForecastTransaction, " +
            "remainingAmount, plannedDate, memo, runningBalance, overridden, firstOccurrence, found, " +
            "ForecastItem_idForecastItem) values (";

    @Override
    public String getInsertQuery() throws EntityException, SQLException, ForecastException, BudgetException {
        return insertQuery + "uuid_to_bin('" + getId() + "'), " + remainingAmount + ", " +
                calendarDateToSqlDateString(plannedDate) + ", \"" + memo + "\", " + runningBalance + ", " +
                overridden + ", " + firstOccurrence + ", " + found + ", uuid_to_bin('" + getIdForecastItem() + "'))";
    }

    // The insert on duplicate update query:
    @Override
    public String getInsertOnDuplicateUpdateQuery() throws EntityException, SQLException, ForecastException, BudgetException {
        String query = getInsertQuery() + "on duplicate key update " + getUpdateClause();
        return query;
    }

    // The update query:
    public static final String updateQuery = "update forecast_transaction set ";

    public static String getUpdateQuery() {
        return updateQuery;
    }

    public String getUpdateClause() {
        return "remainingAmount = " + remainingAmount + ", plannedDate = " + calendarDateToSqlDateString(plannedDate) +
                ", memo = \"" + memo + "\", runningBalance = " + runningBalance + ", overridden = " +
                overridden + ", firstOccurrence = " + firstOccurrence + ", found = " + found +
                ", updatedTimeStamp = current_timestamp() where idForecastTransaction = uuid_to_bin('" + id + "')";
    }

    @Override
    public String getUpdateByIdQuery() {
        return getUpdateQuery() + getUpdateClause();
    }

    // The delete query:
    public static final String deleteQuery = "delete from forecast_transaction ";

    public static String getDeleteQuery() {
        return deleteQuery;
    }

    @Override
    public String getDeleteByIdQuery() {
        return getDeleteQuery() + "where idForecastTransaction = uuid_to_bin('" + id + "')";
    }

    // The entity name:
    @Override
    public String getPrintableTypeName() {
        return getPrintableTypeName_static();
    }

    public static String getPrintableTypeName_static() {
        return "forecast transaction";
    }

    public static ForecastTransaction getById(UUID idForecastTransaction)
            throws ForecastException, EntityException, SQLException {
        ResultSet rs = EntityInt.getRSById(selectQuery + " where idForecastTransaction = ", idForecastTransaction,
                "No Forecast Transaction found with id " + idForecastTransaction);
        return (rs != null) ? new ForecastTransaction(rs) : null;
    }

    /*
     *  Main methods:
     */
    /**
     * Zero out the running balances for all forecast transactions (DEPRECATED - affects all forecasts).
     * @deprecated Use {@link #zeroRunningBalances(Forecast)} instead to avoid cross-forecast contamination.
     */
    @Deprecated
    public static void zeroRunningBalances() throws EntityException, RegisterException {
        throw new EntityException("zeroRunningBalances() is deprecated and should not be used. " +
                "Use zeroRunningBalances(Forecast) instead to avoid cross-forecast contamination.");
    }

    /**
     * Zero out the running balances for all forecast transactions in a specific forecast.
     * @param forecast The forecast whose running balances should be zeroed
     * @throws EntityException If a database error occurs
     * @throws RegisterException If a register error occurs
     */
    public static void zeroRunningBalances(Forecast forecast) throws EntityException, RegisterException {
        String query = getUpdateQuery() +
                "runningBalance = 0 " +
                "where ForecastItem_idForecastItem in (" +
                    "select idForecastItem from forecast_item " +
                    "where Forecast_idForecast = uuid_to_bin('" + forecast.getId() + "')" +
                ")";
        executeUpdate(query, "to zero the running balances of all Forecast Transactions in forecast " +
                forecast.getId() + ".");
    }

    public static ForecastTransactionIterator getForecastTransactionsStartingOn(Forecast forecast, Calendar startDate)
            throws EntityException, ForecastException, SQLException, BudgetException {
        ForecastTransactionIterator forecastTransactions;
        if (forecast.isDirty()) {
            forecastTransactions = new ForecastTransactionAndItemMemoryIterator(forecast, startDate);
        } else {
            forecastTransactions = new ForecastTransactionAndItemDatabaseIterator(forecast, startDate);
        }
        return forecastTransactions;
    }

    public static ForecastTransactionIterator getNonZeroForecastTransactionsForBudgetItem(UUID idBudgetItem, UUID idForecast)
            throws EntityException {

        String selectQuery = getSelectQuery() + " " +
                "inner join forecast_item fi on ft.ForecastItem_idForecastItem = fi.idForecastItem " +
                "where ft.remainingAmount <> 0 and " +
                "fi.BudgetItem_idBudgetItem = uuid_to_bin('" + idBudgetItem + "') and " +
                "fi.Forecast_idForecast = uuid_to_bin('" + idForecast + "') " +
                "order by ft.plannedDate asc ";

        ResultSet rs = EntityInt.getRS(selectQuery, "Database error occurred attempting to " +
                "get a list of Forecast Transactions by date.");

        return new ForecastTransactionDatabaseIterator(rs);

    }

    private static ForecastTransactionIterator getNonZeroForecastTransactionsForForecastItem(ForecastItem forecastItem)
            throws EntityException, SQLException {

        String selectQuery = getSelectQuery() + " " +
                "inner join forecast_item fi on ft.ForecastItem_idForecastItem = fi.idForecastItem " +
                "where ft.remainingAmount <> 0 " +
                "and fi.idForecastItem = uuid_to_bin('" + forecastItem.getId() + "') " +
                // TODO: This doesn't seem correct.  Both sides of the equality come from the same object?  The forecast
                // TODO: item ID in the table should be the same as the forecast item ID in the object.
                "and fi.Forecast_idForecast = uuid_to_bin('" + forecastItem.getForecast().getId() + "') " +
                "order by ft.plannedDate asc ";

        ResultSet rs = EntityInt.getRS(selectQuery, "Database error occurred attempting to " +
                "get a list of Forecast Transactions by date.");

        return new ForecastTransactionDatabaseIterator(rs);

    }

    /**
     * Create and return an iterator that will traverse a chronological list of forecast transactions in the specified
     * forecast that have non-zero remaining amounts.  This means the same thing as all the forecast transactions that
     * are forecast to occur.
     *
     * @return Always returns an iterator, though there may be no transactions in the iterator.
     */
    public static ForecastTransactionIterator getNonZeroForecastTransactions(Forecast forecast) throws EntityException {
        String selectQuery = getSelectQuery() + " " +
                "inner join forecast_item fi on ft.ForecastItem_idForecastItem = fi.idForecastItem " +
                "where ft.remainingAmount <> 0 and ft.ForecastItem_idForecastItem = fi.idForecastItem and " +
                "fi.Forecast_idForecast = uuid_to_bin('" + forecast.getId() + "') " +
                "order by ft.plannedDate asc, fi.amount desc";

        ResultSet rs = EntityInt.getRS(selectQuery, "Database error occurred attempting to " +
                "get a list of non-zero Forecast Transactions for forecast " + forecast.getDescription());

        return new ForecastTransactionDatabaseIterator(rs);
    }

    private static ForecastTransactionIterator getNonZeroForecastTransactionsForForecastItem(UUID idForecastItem)
            throws EntityException {
        String selectQuery = getSelectQuery() + " " +
                "where ft.remainingAmount <> 0 " +
                "and ForecastItem_idForecastItem = uuid_to_bin('" + idForecastItem + "') " +
                "order by ft.plannedDate asc ";

        ResultSet rs = EntityInt.getRS(selectQuery, "Database error occurred attempting to " +
                "get a list of Forecast Transactions by date.");

        return new ForecastTransactionDatabaseIterator(rs);
    }

    /**
     * Get the list of goals for a particular forecastItem.  Envelopes are forecast items in an forecastItem type register. Goals
     * are forecast transactions for a particular forecast item that are in the future and have a negative amount.
     *
     * @param forecastItem The forecastItem to get the goals for.
     * @param OnOrAfterDate The date after which is considered the future from the perspective af goals.
     * @return A list of goals (forecast transaction in the future with negative amounts).                                  .
     */
    public static List<ForecastTransaction> getNegativeForecastTransForItemOnOrAfter(ForecastItem forecastItem,
             Calendar OnOrAfterDate) throws ForecastException, EntityException {

        // Get a ResultSet of forecast transactions for the forecastItem:
        String selectQuery =
                getSelectQuery() + " " +
                "where " +
                    "ft.remainingAmount < 0 and " +
                    "ft.plannedDate > " + calendarDateToSqlDateString(OnOrAfterDate) + " and " +
                    "ft.ForecastItem_idForecastItem = uuid_to_bin('" + forecastItem.getId() + "') " +
                "order by " +
                    "ft.plannedDate asc ";
        ResultSet rs = EntityInt.getRS(selectQuery, "Database error occurred attempting to " +
                "get a list of goas for the forecastItem " + forecastItem.toStringVeryConcise() + ".");

        // Create a list of goals:
        List<ForecastTransaction> goals = new ArrayList<>();
        try {
            while (rs.next()) {
                goals.add(new ForecastTransaction(rs));
            }
        } catch (SQLException e) {
            // Create a forecast exception and log the error:
            String message = "Database error occurred attempting to get a list of goals for the forecastItem " +
                    forecastItem.toStringVeryConcise() + ".";
            //logger.error(message);
            ForecastException forecastException = new ForecastException(message);
            forecastException.initCause(e);
            throw forecastException;
        }

        return goals;
    }


    // Update the dates of all forecast transactions for a particular forecast item:
    private static boolean updateAllDates(ForecastTransaction forecastTransaction, Calendar newDate)
            throws EntityException, Exception, BudgetException {

        // Don't change the passed date parameter:
        Calendar nextDate = (Calendar) newDate.clone();

        // Was the update successful?:
        boolean result;

        // If the user wants to move the date to before previous occurrences that would cause a problem:
        if (newDate.compareTo(forecastTransaction.getForecastItem().getPreviousDateOfOccurrence(
                forecastTransaction.getPlannedDate())) > 0) {
            result = false;
        } else {
            // Iterate over all the non-zero forecast transactions associated with this forecast item and update their dates.
            // Don't touch the ones that have already been fully reconciled.  To do this get a list forecast transactions
            // beginning with the earliest non-zero amount occurrence of a forecast transaction in the forecast for the
            // budget item associated with the split.  This should start with the existing forecast transaction:
            ForecastItem forecastItem = forecastTransaction.getForecastItem();
            ForecastTransactionIterator it =
                    ForecastTransaction.getNonZeroForecastTransactionsForForecastItem(forecastTransaction.getIdForecastItem());
            ForecastTransaction forecastTransactionOccurrence = it.getNext();
            while (forecastTransactionOccurrence != null) {
                forecastTransactionOccurrence.setPlannedDate((Calendar) nextDate.clone());
                nextDate = forecastItem.getNextDateOfOccurrence(nextDate);
                forecastTransactionOccurrence = it.getNext();
            }
            result = true;
        }
        return result;
    }


    /*
     *  Helper methods:
     */
    /**
     * Determine whether this forecast transaction is overdue as of today's date.
     *
     * @return True if this forecast transaction is considered overdue today.
     */
    private boolean isOverdue() throws BudgetException {
        int variance = daysBetween(getPlannedDate(), Calendar.getInstance());
        return !forecastItem.isWithinNormalDateVariance(variance);
    }

    /**
     * Get the most recent reconciled transaction for the specified forecast transaction.  The algorithm is to use a
     * single SQL query that joins the forecast item associated with all it's forecast transactions that have forecast
     * transaction splits in descending order by date and limit the result set to 1.
     * <p>
     * This method queries the database to find the most recently posted transaction that has been reconciled
     * to any forecast transaction associated with the same forecast item as the given forecast transaction.
     * This is useful for determining when the last occurrence of a recurring budget item actually took place.
     * </p>
     *
     * @param forecastTransaction The forecast transaction to find the most recent reconciled transaction for.
     *                           Must not be null and must have a valid forecast item.
     * @return The most recent Transaction that has been reconciled to a forecast transaction with the same
     *         forecast item, or null if no reconciled transactions exist.
     * @throws EntityException If there is a database error while executing the query
     * @throws SQLException If there is a SQL error during database operations
     * @throws ForecastException If there is an error retrieving the forecast item ID
     * @throws BudgetException If there is an error related to budget processing
     */
    public Transaction getMostRecentReconciledTransaction(ForecastTransaction forecastTransaction)
            throws EntityException, SQLException, ForecastException, BudgetException {

        // Build the SQL query that joins forecast_transaction_split, transaction, and forecast_transaction
        // to find the most recent transaction by date for the same forecast item
        String query = Transaction.getSelectQuery() + " " +
                "INNER JOIN forecast_transaction_split fts ON tr.idTransaction = fts.Transaction_Split_idTransaction " +
                "INNER JOIN forecast_transaction ft ON fts.ForecastTransaction_idForecastTransaction = ft.idForecastTransaction " +
                "WHERE ft.ForecastItem_idForecastItem = uuid_to_bin('" + forecastTransaction.getIdForecastItem() + "') " +
                "ORDER BY tr.postDate DESC " +
                "LIMIT 1";

        // Execute the query and get the result
        ResultSet rs = EntityInt.getSingletonRS(query, "getting most recent reconciled transaction for forecast transaction");

        // If a result was found, create and return the Transaction object
        if (rs != null) {
            return new Transaction(rs);
        }

        // No reconciled transactions found
        return null;
    }

    // Timing of a date with respect to the applicability period of a forecast transaction:
    public enum Timing {PRIOR_TO, WITHIN, AFTER, UNDEFINED}

    /**
     * Determine the timing of a date with respect to the applicability period of a forecast transaction.
     *
     * @param date The date to determine the timing of.
     * @return PRIOR_TO if the date is prior to the applicability period of the transaction.  WITHIN if the date is
     * within the applicabiity of the transaction.  AFTER if the date occurs after the applicability period of the
     * transaction.
     * @throws ForecastException
     * @throws EntityException
     * @throws BudgetException
     * @throws SQLException
     */
    public Timing fallsWithinWindow(Calendar date) throws ForecastException, EntityException, BudgetException, SQLException {
        Timing timing = null;
        int comparison = date.compareTo(plannedDate);

        switch (getForecastItem().getHowOccurs()) {
            case COLLECTION:
            case PERIODIC:
            case VARIABLE_PERIODIC:
                // period is from the date of the forecast transaction to the day before the next occurrence
                // If the transaction date is earlier than the forecast transaction planned date:
                if (comparison < 0) {
                    timing = Timing.PRIOR_TO;
                } else {
                    // Compute the next date of occurrence for the related forecast item:
                    Calendar nextDate = forecastItem.getNextDateOfOccurrence(plannedDate);

                    // If the transaction date is prior to the next forecast transaction planned date:
                    if (nextDate != null && date.compareTo(nextDate) < 0) {
                        timing = Timing.WITHIN;
                    } else {
                        timing = Timing.AFTER;
                    }
                }
                break;

            case ENVELOPE:  // The period is from the date of the forecast transaction to the day after the previous occurrence
                // If the date passed in is before the date of this forecast transaction:
                if (comparison < 0) {

                    // Get the date of the previous transaction (if one exists):
                    Calendar previousTransaction = getForecastItem().getPreviousDateOfOccurrence(plannedDate);

                    // If there is a previous forecast transaction:
                    if (previousTransaction != null) {
                        // Then if it is after the date of the previous forecast transaction:
                        if (date.compareTo(previousTransaction) > 0) {
                            // then it is within the period of this forecast transaction.
                            timing = Timing.WITHIN;
                        } else { // else it is prior to the period of this forecast transaction.
                            timing = Timing.PRIOR_TO;
                        }
                    } else {
                        // if there isn't a previous transaction, then consider it within the current transaction:
                        timing = Timing.WITHIN;
                    }
                } else { // the date passed in is either equal to or after the date of this forecast transaction:
                    // If date passed in is the same as the date of this forecast item:
                    if (comparison == 0) {
                        // Then by definition it is within the period of this item:
                        timing = Timing.WITHIN;
                    } else { // The date passed in is after the date of the forecast transaction:
                        // so then by definition the timing of the passed in date is after the period:
                        timing = Timing.AFTER;
                    }
                }
                break;

            case UNPLANNED:
                // There is no planned date, so a transaction always "falls within" the window for one of these.
                timing = Timing.WITHIN;
                break;

            default:
                throw new ForecastException("Unknown 'howOccurs' method:  " + getForecastItem().getHowOccurs());
        }

        return timing;
    }

    @Override
    public String toString() {
        String s;
        try {
            s = "Forecast Transaction:  \n\tPlanned Date = " + calendarDateToStringDate(getPlannedDate()) +
                    ", \n\tCategory = " + getForecastItem().getCategory() +
                    ", \n\tPayee =  " + getForecastItem().getPayee() +
                    ", \n\tForcast Item Memo =  " + getForecastItem().getMemo() +
                    ", \n\tMemo =  " + getMemo() +
                    ", \n\tBudgeted Amount = " + formatDollarAmount(forecastItem.getAmount()) +
                    ", \n\tRemaining Amount = " + formatDollarAmount(remainingAmount) +
                    ", \n\tFirst occurrence = " + firstOccurrence +
                    ", \n\tfound = " + found +
                    ", \n\tForecast transaction - ID = " + this.getId().toString() +
                    ", \n\tNext significant event = " + this.getNextSignificantEvent();
        } catch (Exception e) {
            s = "\nUnable to print out the forecast transaction.";
        }
        return s;
    }

    public String toStringConcise() {
        String s;
        try {
            String memoString =
                    (this.getForecastItem().getMemo() == null || this.getForecastItem().getMemo().isEmpty()) ?
                            "" : " Memo = " + this.getForecastItem().getMemo();

            // Get split amount if splits exist
            String splitString = "";
            try {
                double splitAmount = getTotalSplitAmount();
                if (splitAmount != 0.0) {
                    splitString = ", Split Amount = " + formatDollarAmount(splitAmount);
                }
            } catch (Exception e) {
                // If we can't get split amount, just don't show it
            }

            s = "Forecast Transaction (" + Utility.calendarDateToMonthDayStringDate(getVersion()) + "):  Planned Date = "
                    + calendarDateToStringDate(this.getPlannedDate()) + ", Category = " +
                    this.getForecastItem().getCategory() + ", Payee = " + this.getForecastItem().getPayee() + memoString +
                    ", Budgeted Amount = " + formatDollarAmount(forecastItem.getAmount()) + ", Remaining Amount = " +
                    formatDollarAmount(remainingAmount) + splitString;
        } catch (Exception e) {
            s = "\nUnable to print out the forecast transaction.";
        }
        return s;
    }

    /**
     * Get the total amount of all splits associated with this forecast transaction.
     * @return The total split amount, or 0.0 if no splits exist
     */
    private double getTotalSplitAmount() {
        try {
            String query = ForecastTransactionSplit.getSelectQuery() +
                    " WHERE fts.ForecastTransaction_idForecastTransaction = uuid_to_bin('" + this.getId() + "')";
            ResultSet rs = EntityInt.getRS(query, "getting forecast transaction splits for display");

            double totalSplitAmount = 0.0;
            if (rs != null) {
                while (rs.next()) {
                    ForecastTransactionSplit split = new ForecastTransactionSplit(rs);
                    // Get the actual transaction split to get the amount
                    String transSplitQuery = com.hixon.financialApp.model.budget.TransactionSplit.getSelectQuery() +
                            "WHERE ts.BudgetItem_idBudgetItem = uuid_to_bin('" + split.getIdBudgetItem() + "') AND " +
                            "ts.Transaction_idTransaction = uuid_to_bin('" + split.getIdTransaction() + "')";
                    ResultSet tsRs = EntityInt.getRS(transSplitQuery, "getting transaction split amount");
                    if (tsRs != null && tsRs.next()) {
                        com.hixon.financialApp.model.budget.TransactionSplit transSplit =
                                new com.hixon.financialApp.model.budget.TransactionSplit(tsRs);
                        totalSplitAmount += transSplit.getAmount();
                    }
                }
            }
            return totalSplitAmount;
        } catch (Exception e) {
            return 0.0;
        }
    }

    public String toStringVeryConcise() throws BudgetException, SQLException, EntityException, ForecastException {
        return "Forecast Transaction:  Planned date = " + calendarDateToMonthDayStringDate(getPlannedDate()) +
                ", Budgeted amount = " + formatDollarAmount(getForecastItem().getAmount()) +
                ", Remaining amount = " + formatDollarAmount(getRemainingAmount());
    }

    public String toStringCompact() {
        String s;
        try {
            ForecastItem item = this.getForecastItem();
            StringBuilder sb = new StringBuilder();

            // Date with year
            sb.append(calendarDateToStringDate(this.getPlannedDate()));

            // Category - Payee
            sb.append(", ").append(item.getCategory()).append(" - ").append(item.getPayee());

            // Memo (if present)
            if (item.getMemo() != null && !item.getMemo().isEmpty()) {
                sb.append(", ").append(item.getMemo());
            }

            // Budgeted amount
            sb.append(", Budgeted: ").append(formatDollarAmount(item.getAmount()));

            // Remaining amount
            sb.append(", Remaining: ").append(formatDollarAmount(remainingAmount));

            s = sb.toString();
        } catch (Exception e) {
            s = "\nUnable to print out the forecast transaction.";
        }
        return s;
    }


    /*
     *  Main methods:
     */
    /*
     *   Get a list of forecast transactions that a particular user is tracking closely:
     */
    public static List<Entity> getTrackingForecastTransactionsOfInterest(User user) throws EntityException, Exception,
            BudgetException, RegisterException {
        final List<Entity> items = new ArrayList<>();

        // First get a list of the items of interest to the user:
        ResultSet rsII = ItemOfInterest.getTrackingItemsOfInterestForUser(user);

        // Then for each item of interest:
        Calendar today = Calendar.getInstance();
        while (rsII.next()) {

            // Get the applicable forecast transaction:
            ItemOfInterest itemOfInterest = new ItemOfInterest(rsII);
            ForecastTransaction forecastTransaction = getApplicableForecastTransaction(itemOfInterest.getIdBudgetItem(),
                    today);

            // and if there is an applicable transaction:
            if (forecastTransaction != null) {

                // then add it to the list of interesting forecast transactions:
                items.add(forecastTransaction);
            }
        }

        //  Sort the list in ascending order by payee:
        Comparator<Entity> comparator = (t1, t2) -> {
            try {
                String t1Key = ((ForecastTransaction) t1).getForecastItem().getBudgetItem().getPayee();
                String t2Key = ((ForecastTransaction) t2).getForecastItem().getBudgetItem().getPayee();
                return t1Key.compareTo(t2Key);
            } catch (EntityException | SQLException | ForecastException | BudgetException e) {
                throw new ClassCastException(e.getMessage());
            }
        };
        items.sort(comparator);
        return items;
    }


    /*
     *   Get a list of upcoming forecast transactions that a particular user is interested in:
     */
    public static List<Entity> getUpcomingForecastTransactionsOfInterest(User user) throws EntityException, Exception,
            BudgetException, RegisterException {
        final List<Entity> items = new ArrayList<>();

        // First get a list of the items of interest to the user:
        ResultSet rsII = ItemOfInterest.getUpcomingItemsOfInterestForUser(user);

        // Then for each item of interest:
        Calendar today = Calendar.getInstance();
        while (rsII.next()) {

            ItemOfInterest itemOfInterest = new ItemOfInterest(rsII);

            // Get the first non-zero occurence of a forecast transaction for this item of interest:
            ForecastItem forecastItem = ForecastItem.getByBudgetItemId(itemOfInterest.getIdBudgetItem());
            ForecastTransaction forecastTransaction = getFirstNonZeroOccurrence(forecastItem);

            // and if there is an applicable transaction:
            if (forecastTransaction != null) {

                // then add it to the list of interesting forecast transactions:
                items.add(forecastTransaction);
            }
        }

        //  Sort the list in ascending order by date:
        Comparator<Entity> comparator = (t1, t2) -> {
            Calendar t1Key = ((ForecastTransaction) t1).getPlannedDate();
            Calendar t2Key = ((ForecastTransaction) t2).getPlannedDate();
            return t1Key.compareTo(t2Key);
        };
        items.sort(comparator);
        return items;
    }


    public static ForecastTransaction getApplicableForecastTransaction(UUID idBudgetItem, Calendar date)
            throws EntityException, Exception, BudgetException, RegisterException {
        ForecastTransaction forecastTransaction = getApplicableForecastTransaction(Forecast.getMostRecent(), idBudgetItem,
                date);
        return forecastTransaction;
    }

    public static ForecastTransaction getApplicableForecastTransaction(Forecast forecast, UUID idBudgetItem, Calendar date)
            throws EntityException, Exception, BudgetException, RegisterException {
        // TODO:  Fix situation where there is no forecast item for the budget item.
        ForecastItem forecastItem = ForecastItem.getByBudgetItemId(forecast, idBudgetItem);
        if (forecastItem != null) {
            ForecastTransaction forecastTransaction = getApplicableForecastTransaction(ForecastItem.getByBudgetItemId(forecast,
                    idBudgetItem), date);
            return forecastTransaction;
        } else {
            return null;
        }
    }


    /**
     * Find the forecast transaction in the forecast that applies to the specified forecast item on the specified date:
     *
     * @param forecastItem The forecast item that we are looking for a forecast transaction instance for.
     * @param date         The date of interest.
     * @return The applicable forecast transaction.  Null if there is none (hasn't occurred yet in the forecast).
     * @throws SQLException
     * @throws EntityException
     * @throws BudgetException
     * @throws ForecastException
     */
    public static ForecastTransaction getApplicableForecastTransaction(ForecastItem forecastItem, Calendar date)
            throws Exception, EntityException, BudgetException, RegisterException {

        // For efficiency purposes, first look for a non-zero forecast transaction (one that isn't exhausted) that
        // applies to the specified forecast item on the specified date:
        ForecastTransaction forecastTransaction = getApplicableNonZeroOccurrence(forecastItem, date);

        // If there isn't one:
        if (forecastTransaction == null) {

            // then look for an exhausted (zero) forecast item that applies to the specified date:
            forecastTransaction = getApplicableZeroOccurrence(forecastItem, date);
        }

        return forecastTransaction;
    }


    /**
     * Get the applicable non-zero occurrence of a forecast transaction for a given date.  The algorithm used is to
     * find the first non-zero forecast transaction for a given forecast item.  Then, if that forecast transaction is
     * prior to the applicability period of the specified date, roll up the old forecast transactions until we get to
     * the one that is.
     *
     * @param forecastItem The item to find a forecast transaction for.
     * @param date         The that defines the applicability period.
     * @return The applicable forecast transaction if there is one, else null.
     */
    public static ForecastTransaction getApplicableNonZeroOccurrence(ForecastItem forecastItem, Calendar date) throws
            EntityException, Exception, BudgetException, RegisterException {


        // Get a list of forecast transactions beginning with the earliest non-zero amount occurrence of a forecast
        // transaction in the forecast for the budget item associated with the forecast item:
        ForecastTransactionIterator it =
                ForecastTransaction.getNonZeroForecastTransactionsForForecastItem(forecastItem);

        // Find the forecast transaction in the list that this split applies to.  Roll up any old forecast transactions
        // encountered in the process:
        ForecastTransaction forecastTransaction = it.getNext();
        if (forecastTransaction != null) {

            Timing timing = forecastTransaction.fallsWithinWindow(date);
            switch (timing) {

                case PRIOR_TO:  // The date occurs before the period of this forecast transaction:

                    // If the specified date is prior to the applicability period of the first non-zero
                    // transaction in the forecast, then there is no applicable non-zero transaction:
                    forecastTransaction = null;
                    break;

                case WITHIN:  // Found the applicable forecast transaction.
                    break;

                case AFTER:  // There is money left from a prior period for the budgeted item:

                    switch (forecastItem.getHowOccurs()) {

                        case COLLECTION: // This is an instance of underspending.
                        case PERIODIC: // The previous instance was never paid:
                        case VARIABLE_PERIODIC:  // The previous instance was never paid:

                            // Roll the money forward:
                            while (forecastTransaction.fallsWithinWindow(date) == Timing.AFTER) {
                                double remainingAmount = forecastTransaction.getRemainingAmount();
                                forecastTransaction.setRemainingAmount(0);
                                forecastTransaction.save(UPDATE);
                                forecastTransaction = it.getNext();
                                if (forecastTransaction != null) {
                                    forecastTransaction.setRemainingAmount(forecastTransaction.getRemainingAmount() +
                                            remainingAmount);
                                    forecastTransaction.save(UPDATE);
                                } else {
                                    break;
                                }
                            }
                            break;

                        case ENVELOPE:  // Once the date for an envelope contribution passes, remove it:

                            // Roll up the expired items into the current item and mark them expired:
                            do {
                                // The forecast item contains the current envelope balance, so add this forecast
                                // transaction to it:
                                forecastItem.setRunningBalance(forecastItem.getRunningBalance() +
                                        forecastTransaction.getRemainingAmount());
                                forecastItem.save(UPDATE);

                                // and zero out the forecast transaction:
                                forecastTransaction.setRemainingAmount(0);
                                forecastTransaction.save(UPDATE);
                                forecastTransaction = it.getNext();

                            } while (forecastTransaction != null &&
                                    forecastTransaction.fallsWithinWindow(date) == Timing.AFTER);
                            break;

                        default:
                            throw new ForecastException("Unexpected 'howOccurs' method:  " + forecastItem.getHowOccurs());
                    }
            }
        }
        return forecastTransaction;

    }

    /**
     * Find the applicable forecast transaction for a forecast on the specified date given the fact that there are no
     * non-zero transactions that apply to it.
     *
     * @param forecast     The forecast in which to find the applicable forecast transactions.
     * @param idBudgetItem The ID of the budget item associated with the forecast transaction we are looking for.
     * @param date         The date on which the forecast transaction must be the applicable forecast transaction.
     * @return The applicable forecast transaction.
     */
    public static ForecastTransaction getApplicableZeroOccurrence(Forecast forecast, UUID idBudgetItem, Calendar date)
            throws SQLException, EntityException, ForecastException, BudgetException {

        return getApplicableZeroOccurrence(ForecastItem.getByBudgetItemId(idBudgetItem), date);
    }


    /**
     * Find the forecast transaction for a forecast item that applies on the specified date given the fact that there
     * are no non-zero forecast transactions that apply to it.  Given that fact, the algorithm is to simply get the a
     * last transaction for the item that occurs on or before the specified date. If that transaction does not apply to
     * the date, then the next occurrence of a forecast transaction must apply (the applicability period is prior to the
     * item) so return it (if there is one).
     *
     * @param forecastItem The forecast item for which to find applicable forecast transactions.
     * @param date         The date on which the forecast transaction must be the applicable forecast transaction.
     * @return The applicable forecast transaction.
     */
    private static ForecastTransaction getApplicableZeroOccurrence(ForecastItem forecastItem, Calendar date)
            throws SQLException, EntityException, ForecastException, BudgetException {

        ForecastTransaction forecastTransaction = null;

        // Find the last occurrence of a forecast transaction on or before today for this item of interest:
        String lastOccurrenceBeforeDateQuery =
                "select fi.category as 'fi.category', fi.payee as 'fi.payee', " + ForecastTransaction.getSelectColumns() +
                        "from forecast_transaction ft " +
                        "inner join forecast_item fi on ft.ForecastItem_idForecastItem = fi.idForecastItem " +
                        "where " +
                        "ft.plannedDate <= " + calendarDateToSqlDateString(date) + " and " +
                        "fi.category = \"" + forecastItem.getCategory() + "\" and " +
                        "fi.payee = \"" + forecastItem.getPayee() + "\" " +
                        "order by ft.plannedDate desc " +
                        "limit 1";
        ResultSet rsLO = EntityInt.getRS(lastOccurrenceBeforeDateQuery, "retrieve the latest occurrence " +
                "of a the forecast transaction for forecast item" + forecastItem + " before " +
                calendarDateToStringDate(date) + ".");

        // If there is a last occurrence before today of a forecast transaction for the forecast item:
        if (rsLO.next()) {

            forecastTransaction = new ForecastTransaction(rsLO);

            // Check if today's date falls within the effective window of this forecast transaction:
            Timing timing = forecastTransaction.fallsWithinWindow(date);
            switch (timing) {

                case PRIOR_TO:
                    // The specified date is prior to the applicability window of this forecast transaction, which is
                    // not possible given that this transaction occurs before that date.
                    throw new ForecastException("A date which occurs after the planned date of a forecast " +
                            "transaction was determined to be prior to the applicability period of the transaction," +
                            "which is not possible.");

                case WITHIN:
                    // The specified date falls within the applicability window of this forecast transaction so this is
                    // the one we are looking for:
                    break;

                case AFTER:
                    // The specified date occurs after the applicability window of the last transaction scheduled for
                    // that date.  By the "bracketing principle" if forecast transaction immediately prior to a date
                    // does not apply to that date, then one immediately after must, so return that one:
                    ForecastTransaction nextForecastTransaction = getNextOccurrence(forecastItem, date);

                    // If there is a next forecast transaction:
                    if (nextForecastTransaction != null) {

                        // Check to see if the returned forecast transaction is applicable to the specified date.  If not,
                        // then the specified date is in a hole between the applicability windows of the forecast transaction
                        // preceding the specified date and the forecast transaction immediately following that date.  This
                        // can occur if the user manually adjusts the planned dates of forecast transactions creating holes
                        // between them.
                        timing = nextForecastTransaction.fallsWithinWindow(date);
                        switch (timing) {

                            case PRIOR_TO:
                                // The specified date is prior to the applicability window of this forecast transaction, which
                                // means we are in a hole between the applicability windows of the preceding and succeeding
                                // forecast transactions.  In this case return the preceding forecast transaction:
                                // not possible given that this transaction occurs before that date.
                                break;

                            case WITHIN:
                                // The specified date falls within the applicability window of this forecast transaction so this is
                                // the one we are looking for:
                                break;

                            case AFTER:
                                // The specified date occurs after the applicability window of the next forecast transaction
                                // planned, which is a violation of the "bracketing principle":
                                throw new ForecastException("The next forecast transaction \n" + nextForecastTransaction +
                                        "\nfollowing the one immediately prior to " + calendarDateToMonthDayStringDate(date) +
                                        " is \n" + forecastTransaction + "\nwhich is also prior to the specified date, which " +
                                        "should not occur.");
                        }
                    }
                    break;

            }
        } else {

            // but if there isn't a latest occurrence, then try the next occurrence:
            forecastTransaction = getNextOccurrence(forecastItem, date);

            // Check if the specified date falls within the effective window of this forecast transaction:
            if (forecastTransaction != null) {
                Timing timing = forecastTransaction.fallsWithinWindow(date);
                switch (timing) {

                    case PRIOR_TO:
                        // The specified date occurs before the applicability window of the next transaction in the forecast
                        // for the specified forecast item.  Since there was not a forecast transaction with a planned date
                        // prior to the specified date, then the date must be before the first occurrence of that forecast
                        // item in the forecast.  Therefore there is no applicable forecast transaction.
                        forecastTransaction = null;
                        break;

                    case WITHIN:
                        // The specified date falls within the applicability window of this forecast transaction so this is
                        // the one we are looking for:
                        break;

                    case AFTER:
                        // The specified date is after to the applicability window of this forecast transaction, which is
                        // not possible given that this transaction occurs after that date.
                        throw new ForecastException("A date which occurs prior to the planned date of a forecast " +
                                "transaction was determined to be after the applicability period of the transaction," +
                                "which is not possible.");

                }
            }
        }

        return forecastTransaction;
    }


    /**
     * Get the next occurrence of a forecast transaction for a forecast item after a specified date:
     *
     * @param forecastItem
     * @param date
     * @return
     * @throws EntityException
     * @throws SQLException
     * @throws ForecastException
     * @throws BudgetException
     */
    private static ForecastTransaction getNextOccurrence(ForecastItem forecastItem, Calendar date)
            throws EntityException, SQLException, ForecastException, BudgetException {

        // then get the next occurrence of a forecast transaction for the forecast item of interest after the specified
        // date:
        String nextOccurrenceQuery =
                ForecastTransaction.getSelectQuery() + " " +
                        "where ft.ForecastItem_idForecastItem = uuid_to_bin ('" + forecastItem.getId() + "') and " +
                        "ft.plannedDate > " + calendarDateToSqlDateString(date) + " " +
                        "order by ft.plannedDate asc " +
                        "limit 1";
        ResultSet rsNO = EntityInt.getRS(nextOccurrenceQuery, "retrieve the next occurrence of a forecast " +
                "transactions after the latest occurrence of that forecast transaction for the forecast item" +
                forecastItem + ".");

        // If there is a next occurrence:
        if (rsNO.next()) {

            // then return it:
            return new ForecastTransaction(rsNO);

        } else {

            // This is normal if the forecast item is unplanned because unplanned forecast items only occur in the forecast
            // if they are required to reconcile transaction splits.  However, if it isn't unplanned, Then this is an
            // odd situation.  Probably the item either expired before the forecast begins, or the forecast ended before
            // the first occurrence of the item in the forecast.
            return null;
        }
   }


    /**
     * Get the first non-zero occurrence of a forecast transaction. The algorithm is to return the head of a list of
     * of forecast transactions for the forecast item with a nan-zero remaining amount in chronological order.
     *
     * @param forecastItem
     * @return
     * @throws EntityException
     * @throws SQLException
     * @throws ForecastException
     * @throws BudgetException
     */
    private static ForecastTransaction getFirstNonZeroOccurrence(ForecastItem forecastItem)
            throws EntityException, SQLException, ForecastException, BudgetException {

        String firstOccurrenceQuery =
                ForecastTransaction.getSelectQuery() + " " +
                        "where ft.ForecastItem_idForecastItem = uuid_to_bin ('" + forecastItem.getId() + "') and " +
                        "ft.remainingAmount <> 0 " +
                        "order by ft.plannedDate asc " +
                        "limit 1";
        ResultSet rsNZ = EntityInt.getRS(firstOccurrenceQuery, "retrieve the first occurrence of a forecast " +
                "transaction with a non-zero remaining amount for the forecast item" + forecastItem + ".");

        // If there is a non-zero occurrence:
        if (rsNZ.next()) {

            // then return it:
            return new ForecastTransaction(rsNZ);

        } else {

            // This is normal if the forecast item is unplanned because unplanned forecast items only occur in the forecast
            // if they are required to reconcile transaction splits.  However, if it isn't unplanned, Then this is an
            // odd situation.  Probably the item either expired before the forecast begins, or the forecast ended before
            // the first occurrence of the item in the forecast.
            return null;
        }
    }

    /**
     * Clean up the forecast.  This is required when forecasts are updated or merged.  Reset all the first occurrence
     * flags and roll up any expired transactions while we are at it (good hygiene):
     *
     * @param forecast The forecast to be cleaned up.
     * @throws EntityException
     * @throws RegisterException
     * @throws SQLException
     */
    public static void cleanUpForecast(Forecast forecast) throws EntityException, RegisterException, Exception, BudgetException {

        // Set all the forecast transactions in THIS forecast to "not the first occurrence".
        String query = getUpdateQuery() +
                "firstOccurrence = false " +
                "where ForecastItem_idForecastItem in (" +
                    "select idForecastItem from forecast_item " +
                    "where Forecast_idForecast = uuid_to_bin('" + forecast.getId() + "')" +
                ")";
        EntityInt.executeUpdate(query, "attempting to set " +
                "the first occurrence flags to false in forecast " + forecast.getId() + ".");

        // Set the first occurrence of every forecast transaction to "first occurrence" and roll up any expired
        // transactions.  The algorithm used is to go get all the forecast items in the forecast then for each one get
        // the earliest occurrence of a forecast transaction for it in the forecast and set that occurrence as the first
        // occurrence, then get the applicable transaction to today's date which will roll any expired transactions
        // as a side effect:
        ResultSet rsFI = ForecastItem.getAllUsableForecastItemsInForecast(forecast);
        Calendar today = Calendar.getInstance();
        if (rsFI != null) {
            while (rsFI.next()) {

                // Get the first occurrence of a forecast transaction for the current forecast item:
                String firstOccurrenceQuery = getSelectQuery() + " where ft.ForecastItem_idForecastItem = uuid_to_bin('"
                        + rsFI.getString("fi.idForecastItem") + "') order by ft.plannedDate asc limit 1";
                ResultSet rsFT = EntityInt.getRS(firstOccurrenceQuery, "retrieve the first occurrence of a " +
                        "forecast transaction for forecast item" + new ForecastItem(rsFI));

                // If we found one, then update it to indicate that it is the first occurrence:
                if (rsFT != null && rsFT.next()) {
                    ForecastTransaction forecastTransaction = new ForecastTransaction(rsFT);
                    String updateSql = getUpdateQuery() + "firstOccurrence = true where idForecastTransaction " +
                            "= uuid_to_bin('" + rsFT.getString("ft.idForecastTransaction") + "')";
                    EntityInt.executeUpdate(updateSql, "attempting to retrieve the firstOccurrence of the " +
                            "forecast transaction with ID = " + rsFT.getString("ft.idForecastTransaction"));

                    // Call getApplicableForecastTransaction() just to cause it to roll up expired transactions if any:
                    ForecastItem forecastItem = new ForecastItem(rsFI);
                    ForecastTransaction.getApplicableForecastTransaction(forecastItem, today);
                } else {
                    // If a forecast item does not to occur in the forecast because it is expired, then delete it.  If
                    // it is not expired, then its first occurrence must be after the end of the forecast, which is OK,
                    // so we don't need to do anything.
                    ForecastItem forecastItem = new ForecastItem(rsFI);
                    if (forecastItem.isExpired(Calendar.getInstance())) {
                        forecastItem.delete();
                    }
                }
            }
        }
    }

    public static List<Entity> getOverdueItems(User user, Forecast forecast) throws SQLException, EntityException, ForecastException,
            BudgetException {

        // Get a result set from the database for the overdue items.  Overdue items are unreconciled items that are
        // unacceptably past their planned date:
        Calendar currentDate = Calendar.getInstance();
        String selectQuery =
                "select " + getSelectColumns() + ", " + ForecastItem.getSelectColumns() +
                        "from forecast_transaction ft inner join forecast_item fi " +
                        "on ft.ForecastItem_idForecastItem = fi.idForecastItem" +
                        " where plannedDate < " + calendarDateToSqlDateString(currentDate) +
                        " and remainingAmount <> 0" +
                        " and fi.Forecast_idForecast = uuid_to_bin('" + forecast.getId() + "') ";
        ResultSet rs = EntityInt.getRS(selectQuery, "Database error occurred attempting to " +
                "get a list of overdue Forecast Transactions.");

        // Load the retrieved items into a list.  Exclude any items that are not considered overdue yet:
        List<Entity> overdueItemsList = new ArrayList<>();
        while (rs.next()) {
            ForecastTransaction forecastTransaction = new ForecastTransaction(rs);
            ForecastItem forecastItem = new ForecastItem(rs);
            forecastTransaction.setForecastItem(forecastItem);
            if (forecastTransaction.isOverdue()) {
                overdueItemsList.add(forecastTransaction);
            }
        }

        return overdueItemsList;
    }


    public static List<Entity> getItemsUpToDate(Forecast forecast, Calendar endDate) throws Exception, EntityException,
            BudgetException {

        // Get a result set from the database for the upcoming items.  Upcoming items are non-zero amount items that are
        // whose planned date is before three days from today:
        ForecastTransactionAndItemDatabaseIterator iterator = new ForecastTransactionAndItemDatabaseIterator(forecast,
                Forecast.getFirstNonZeroTransactionDate(forecast));


        // Load the retrieved items into a list. Exclude any items considered overdue:
        List<Entity> upcomingItemsList = new ArrayList<>();
        Calendar today = Calendar.getInstance();
        ForecastTransaction forecastTransaction = iterator.getNext();
        while (forecastTransaction != null && forecastTransaction.getPlannedDate().compareTo(endDate) <= 0) {

            // If this transaction was planned before today:
            if (forecastTransaction.getPlannedDate().compareTo(today) < 0) {

                // Then if it is not an overdue transaction (only periodic transactions can be overdue):
                if (!forecastTransaction.isOverdue()) {

                    // include it in the list:
                    upcomingItemsList.add(forecastTransaction);
                }
            } else {
                // the transaction can't be overdue, so include it in the list:
                upcomingItemsList.add(forecastTransaction);
            }
            forecastTransaction = iterator.getNext();
        }

        return upcomingItemsList;
    }

} // End class ForecastTransaction.
