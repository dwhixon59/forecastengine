package com.hixon.financialApp.model.forecast;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.budget.Item;
import com.hixon.financialApp.model.budget.ItemOfInterest;
import com.hixon.financialApp.model.entity.Entity;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.entity.IndependentEntity;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.model.register.TransactionSplit;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.base.TransactionResolverInt;
import com.hixon.financialApp.view.base.UserResponse;
import com.sun.istack.internal.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import static com.hixon.financialApp.model.entity.EntityInt.SaveMethod.*;
import static com.hixon.financialApp.model.entity.EntityInt.executeUpdate;
import static com.hixon.financialApp.model.forecast.ForecastTransactionSplit.SplitDisposition.*;

/**
 * This the class that represents a single transaction in the forecast.
 */
public class ForecastTransaction extends IndependentEntity {

    /*
     * Fields:
     */
    // Version:
    protected Calendar version;

    // Amount of the transaction in case of an override:
    protected double remainingAmount = 0;

    // The date that this transaction is expected to occur, or is due:
    protected Calendar plannedDate;

    // The id of the forecast item that this transaction is an instance of:
    protected UUID idForecastItem = null;

    // Indicates if this is the first occurrence of this forecast item in the forecast:
    protected boolean firstOccurrence = false;

    // Indicates if this is the first occurrence of this forecast item in the forecast:
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

    public double getRemainingAmount() {
        return remainingAmount;
    }

    public void setRemainingAmount(double remainingAmount) {
        this.remainingAmount = remainingAmount;
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
        String foundString = (found) ? "true" : "false";
        String query = "update forecast_transaction ft set ft.found = " + foundString;
        executeUpdate(query, "attempting to set all the Forecast Transaction found flags " +
                "to " + foundString + ".");
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


    /*
     * Constructors:
     */
    public ForecastTransaction() {
        super(true);
    }

    public ForecastTransaction(ForecastItem item, Calendar nextDate, boolean firstOccurrence) throws Exception {
        super(true);
        if (item == null || nextDate == null) throw new Exception("ForecastItem seeds cannot be null.");
        remainingAmount = item.getAmount();
        plannedDate = (Calendar) nextDate.clone();
        this.firstOccurrence = firstOccurrence;
        idForecastItem = item.getId();
        forecastItem = item;
        System.out.println(this.toString());
    }

    public ForecastTransaction(ResultSet rs) throws SQLException {
        super(false);
        this.id = UUID.fromString(rs.getString("ft.idForecastTransaction"));
        this.plannedDate = Utility.localDateToCalendarDate(rs.getObject("ft.plannedDate", LocalDate.class));
        this.firstOccurrence = rs.getBoolean("ft.firstOccurrence");
        this.found = rs.getBoolean("ft.found");
        this.remainingAmount = rs.getDouble("ft.remainingAmount");
        this.version = Utility.SqlTimestampToCalendarDate(rs.getTimestamp("ft.version"));
        this.idForecastItem = UUID.fromString(rs.getString("ft.idForecastItem"));
    }

    public ForecastTransaction(ForecastTransaction forecastTransaction) throws Exception, EntityException, BudgetException {
        super(true);
        if (forecastTransaction == null) throw new Exception("Forecast transaction to copy cannot be null.");
        this.plannedDate = (Calendar) forecastTransaction.getPlannedDate().clone();
        this.firstOccurrence = forecastTransaction.isFirstOccurrence();
        this.found = forecastTransaction.isFound();
        this.remainingAmount = forecastTransaction.getRemainingAmount();
        this.idForecastItem = forecastTransaction.getIdForecastItem();
        Utility.copyDate(forecastTransaction.getVersion(), this.version);
        this.forecastItem = forecastTransaction.getForecastItem();
    }


    /*
     *  CRUD methods:
     */
    // The select query for forecast transactions for a forecast item:
    public static final String transactionsForItemQuery = "select ft.idForecastTransaction as 'id', ft.updatedTimeStamp " +
            "as 'version', ft.remainingAmount, ft.plannedDate, ft.firstOccurrence, ft.found as 'ft.found', ft.updatedTimeStamp as " +
            "'ft.version', ft.ForecastItem_idForecastItem as 'idForecastItem' from forecast_transaction " +
            "ft inner join forecast_item fi on ft.ForecastItem_idForecastItem = fi.idForecastItem where " +
            "ft.remainingAmount > 0";

    // The select query:
    public static final String selectColumns = " bin_to_uuid(ft.idForecastTransaction) as 'ft.idForecastTransaction', " +
            "ft.updatedTimeStamp as 'ft.version', ft.remainingAmount as 'ft.remainingAmount', ft.plannedDate as " +
            "'ft.plannedDate', ft.firstOccurrence as 'ft.firstOccurrence', ft.found as 'ft.found'," +
            " bin_to_uuid(ft.ForecastItem_idForecastItem) as 'ft.idForecastItem' ";

    public static String getSelectColumns() {
        return selectColumns;
    }

    public static final String selectQuery = "select" + selectColumns + "from forecast_transaction ft";

    public static final String getSelectQuery() {
        return selectQuery;
    }

    // The insert query:
    public static final String insertQuery = "insert into forecast_transaction (idForecastTransaction, " +
            "remainingAmount, plannedDate, firstOccurrence, found, ForecastItem_idForecastItem) values (";

    @Override
    public String getInsertQuery() throws EntityException, SQLException, ForecastException, BudgetException {
        return insertQuery + "uuid_to_bin('" + getId() + "'), " + remainingAmount + ", " +
                Utility.calendarDateToSqlDateString(plannedDate) + ", " + firstOccurrence + ", " + found + ", " +
                "uuid_to_bin('" + getIdForecastItem() + "'))";
    }

    // The insert on duplicate update query:
    @Override
    public String getInsertOnDuplicateUpdateQuery() throws EntityException, SQLException, ForecastException, BudgetException {
        String query = getInsertQuery() + "on duplicate key update " + getupdateClause();
        return query;
    }

    // The update query:
    public static final String updateQuery = "update forecast_transaction set ";

    public static String getUpdateQuery() {
        return updateQuery;
    }

    public String getupdateClause() {
        return "remainingAmount = " + remainingAmount + ", plannedDate = " +
                Utility.calendarDateToSqlDateString(plannedDate) + ", firstOccurrence = " + firstOccurrence +
                ", found = " + found + " where idForecastTransaction = uuid_to_bin('" + id + "')";
    }

    @Override
    public String getUpdateByIdQuery() {
        return getUpdateQuery() + getupdateClause();
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
    public String getPrintableEntityTypeName() {
        return "forecast transaction";
    }

    public static ForecastTransaction getById(UUID idForecastTransaction) throws ForecastException, EntityException, SQLException {
        ResultSet rs = EntityInt.getRSById(selectQuery + " where idForecastTransaction = ", idForecastTransaction,
                "No Forecast Transaction found with id " + idForecastTransaction);
        return (rs != null) ? new ForecastTransaction(rs) : null;
    }

    /*
     *  Main methods:
     */
    // Zero out the amounts for all the Forecast Transactions that are marked not found:
    public static void zeroNotFound() throws EntityException, RegisterException {
        executeUpdate(getUpdateQuery() + "remainingAmount = 0 where found = false", "to zero the " +
                "Forecast Transactions that are marked not found.");
    }

    public static ForecastTransactionIterator getForecastTransactionsStartingOn(Forecast forecast, Calendar startDate)
            throws EntityException, ForecastException, SQLException, BudgetException {
        ForecastTransactionIterator forecastTransactions;
        if (forecast.isDirty()) {
            forecastTransactions = new ForecastTransactionMemoryIterator(forecast, startDate);
        } else {
            forecastTransactions = new ForecastTransactionAndItemDatabaseIterator(forecast, startDate);
        }
        return forecastTransactions;
    }

    private static ForecastTransactionIterator getNonZeroForecastTransactionsForBudgetItem(UUID idBudgetItem, UUID idForecast)
            throws EntityException {

        String selectQuery =
                "select bin_to_uuid(ft.idForecastTransaction) as 'ft.idForecastTransaction', ft.remainingAmount, " +
                        "ft.plannedDate, ft.firstOccurrence, ft.found, ft.updatedTimeStamp as 'ft.version', " +
                        "bin_to_uuid(ft.ForecastItem_idForecastItem) as 'ft.idForecastItem', fi.category, " +
                        "fi.payee, fi.amount as 'plannedAmount' " +
                        "from forecast_transaction ft " +
                        "inner join forecast_item fi on ft.ForecastItem_idForecastItem = fi.idForecastItem " +
                        "inner join budget_item bi on fi.BudgetItem_idBudgetItem = bi.idBudgetItem " +
                        "where ft.remainingAmount <> 0 and bi.idBudgetItem = uuid_to_bin('" + idBudgetItem + "') " +
                        "and fi.Forecast_idForecast = uuid_to_bin('" + idForecast + "') " +
                        "order by ft.plannedDate asc ";

        ResultSet rs = EntityInt.getRS(selectQuery, "Database error occurred attempting to " +
                "get a list of Forecast Transactions by date.");

        return new ForecastTransactionDatabaseIterator(rs);

    }

    private static ForecastTransactionIterator getNonZeroForecastTransactionsForForecastItem(ForecastItem forecastItem)
            throws EntityException, SQLException {

        String selectQuery =
                "select bin_to_uuid(ft.idForecastTransaction) as 'ft.idForecastTransaction', ft.remainingAmount, " +
                        "ft.plannedDate, ft.firstOccurrence, ft.found, ft.updatedTimeStamp as 'ft.version', " +
                        "bin_to_uuid(ft.ForecastItem_idForecastItem) as 'ft.idForecastItem', fi.category, " +
                        "fi.payee, fi.amount as 'plannedAmount' " +
                        "from forecast_transaction ft " +
                        "inner join forecast_item fi on ft.ForecastItem_idForecastItem = fi.idForecastItem " +
                        "where ft.remainingAmount <> 0 and fi.idForecastItem = uuid_to_bin('" + forecastItem.getId() + "') " +
                        "and fi.Forecast_idForecast = uuid_to_bin('" + forecastItem.getForecast().getId() + "') " +
                        "order by ft.plannedDate asc ";

        ResultSet rs = EntityInt.getRS(selectQuery, "Database error occurred attempting to " +
                "get a list of Forecast Transactions by date.");

        return new ForecastTransactionDatabaseIterator(rs);

    }

    private static ForecastTransactionIterator getForecastTransactionsForForecastItem(UUID idForecastItem)
            throws EntityException {
        String selectQuery = "select bin_to_uuid(ft.idForecastTransaction) as idForecastTransaction, ft.remainingAmount, " +
                "ft.plannedDate, ft.firstOccurrence, ft.found, ft.updatedTimeStamp as 'ft.version' " +
                "bin_to_uuid(ft.ForecastItem_idForecastItem) as 'idForecastItem', from forecast_transaction ft " +
                "where ft.remainingAmount <> 0 and ForecastItem_idForecastItem = uuid_to_bin('" + idForecastItem + "') " +
                "order by ft.plannedDate asc ";

        ResultSet rs = EntityInt.getRS(selectQuery, "Database error occurred attempting to " +
                "get a list of Forecast Transactions by date.");

        return new ForecastTransactionDatabaseIterator(rs);
    }


    // Update the dates of all forecast transactions for a particular forecast item:
    private static boolean updateAllDates(ForecastTransaction forecastTransaction, Calendar newDate) throws EntityException,
            Exception, BudgetException {

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
                    ForecastTransaction.getForecastTransactionsForForecastItem(forecastTransaction.getIdForecastItem());
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
    // Timing of a date with respect to the applicability period of a forecast transaction:
    enum Timing {PRIOR_TO, WITHIN, AFTER, UNDEFINED}

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
                    // Compute the next date of occurrence of the related forecast item:
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
            s = "Forecast Transaction:  \n\tPlanned Date = " + Utility.calendarDateToStringDate(this.getPlannedDate()) +
                    ", \n\tCategory = " + this.getForecastItem().getCategory() +
                    ", \n\tPayee =  " + this.getForecastItem().getPayee() + ", \n\tBudgeted Amount = " +
                    Utility.formatDollarAmount(forecastItem.getAmount()) + ", \n\tRemaining Amount = " +
                    Utility.formatDollarAmount(remainingAmount) + ", \n\tFirst occurrence = " + firstOccurrence + ", \n\tfound = "
                    + found + ", \n\tForecast transaction - ID = " + this.getId().toString() + ", \n\tNext significant event = " +
                    this.getNextSignificantEvent();
        } catch (Exception | EntityException | BudgetException e) {
            s = "Unable to print out the forecast transaction.";
        }
        return s;
    }


    /*
     *  Main methods:
     */
    // Reconcile a register transaction with a forecast transaction:
    public static void reconcile(Forecast forecast, Transaction transaction, List<TransactionSplit> splits,
                                 TransactionResolverInt resolver) throws Exception, RegisterException, EntityException, BudgetException {

        // For each split assigned to this transaction:
        ForecastTransactionSplit forecastTransactionSplit;
        for (TransactionSplit split : splits
        ) {
            // If it hasn't already been reconciled:
            if (ForecastTransactionSplit.getForecastTransactionSplitsCount(split, forecast) == 0) {

                // Find the forecast transaction in the list that this split applies to:
                ForecastTransaction forecastTransaction = getMatchingForecastTransaction(forecast, transaction, resolver,
                        split);

                // if we weren't able to match the split to a forecast transaction.
                if (forecastTransaction == null) {
                    // Create a forecast transaction and forecast item (if it doesn't already exist) for it so we have
                    // something to link the forecast transaction split to:
                    ForecastItem forecastItem = ForecastItem.getByBudgetItemId(split.getIdBudgetItem());
                    if (forecastItem == null) {
                        forecastItem = new ForecastItem(forecast, split.getBudgetItem());
                        forecastItem.setAmount(split.getAmount());
                        forecastItem.save(INSERT);
                    }
                    forecastTransaction = new ForecastTransaction(forecastItem, split.getTransaction().getDate(), true);
                    forecastTransaction.setRemainingAmount(0);
                    forecastTransaction.save(INSERT);
                    split.setDisposition(IGNORE);
                }

                // if this split is part of the forecast:
                if (split.getDisposition() != DISPUTE && split.getDisposition() != IGNORE) {

                    // Then deduct the amount of the split:
                    forecastTransaction = deductSplitAmount(forecast, transaction, resolver, split, forecastTransaction);

                } // End if this split is part of the forecast.

                // And finally link the split to the forecast transaction for historical purposes:
                forecastTransactionSplit = new ForecastTransactionSplit(forecastTransaction, split);
                forecastTransactionSplit.save(INSERT);

            } // End if it hasn't already reconciled.
            else {
                System.out.println("Already reconciled.  Skipping.");
            } // End if it has already been reconciled.
        } // End for each split assigned to this transaction.
    } // End ForecastTransaction.reconcile().


    public static ForecastTransaction getMatchingForecastTransaction(Forecast forecast, Transaction transaction,
                                                                     TransactionResolverInt resolver, TransactionSplit split)
            throws EntityException, Exception, BudgetException, RegisterException {

        // Get a list of forecast transactions beginning with the earliest non-zero amount occurrence of a forecast
        // transaction in the forecast for the budget item associated with the split:
        ForecastTransactionIterator it =
                ForecastTransaction.getNonZeroForecastTransactionsForBudgetItem(split.getIdBudgetItem(), forecast.getId());

        // Find the forecast transaction in the list that this split applies to.  Roll up any old forecast transactions
        // encountered in the process:
        ForecastTransaction forecastTransaction = it.getNext();
        if (forecastTransaction != null) {

            Timing timing = forecastTransaction.fallsWithinWindow(transaction.getDate());
            switch (timing) {

                case PRIOR_TO:  // The split occurs before the period of this forecast transaction:

                    switch (split.getBudgetItem().getHowOccurs()) {

                        case COLLECTION: // This split is an instance of overspending.
                            // If the transaction split occurred prior to the first occurrence of forecast item in the
                            // forecast, then it doesn't apply because collection forecast items always occur prior to any
                            // associated splits:
                            if (forecastTransaction.isFirstOccurrence()) {
                                split.setDisposition(IGNORE);
                                resolver.say("Split occurs before the first occurrence of the budget item " +
                                        split.getBudgetItem().getPayee() + " in the forecast.  Ignoring it.");
                            } else {
                                resolver.say("The amount that was allocated for this budget item in the current period (" +
                                        Utility.formatDollarAmount(split.getBudgetItem().getAmount()) + ") is exhausted.");
                                split.setDisposition(resolver.assignOverageAmount(split.getAmount()));
                            }
                            switch (split.getDisposition()) {
                                case ADJUST:
                                    split.getBudgetItem().setAmount(split.getBudgetItem().getAmount() + split.getAmount());
                                    split.getBudgetItem().save(INSERT_ON_DUPLICATE_UPDATE);
                                    forecast.setInSync(false);
                                    forecast.save(UPDATE);
                                    break;

                                case DISPUTE:
                                    transaction.setIsImproper(true);
                                    transaction.save(UPDATE);
                                    transaction.getRegister().addSignificantEvent(transaction);
                                    break;

                                case IGNORE:
                                case ROLL_FORWARD:
                                    break;
                            }
                            break;

                        case ENVELOPE: // We are before the effective start of this forecast item so nothing to reconcile to.
                            resolver.say("Split occurred before the forecast item became effective.  Ignoring.");
                            split.setDisposition(IGNORE);
                            break;

                        case PERIODIC: // The transaction was paid early?
                        case VARIABLE_PERIODIC:

                            // Determine if the actual date a forecast transaction occurred is "on or about" the planned date:
                            int variance = Utility.daysBeteween(forecastTransaction.getPlannedDate(), transaction.getDate());
                            if (!split.getBudgetItem().withinNormalDateVariance(variance)) {

                                // Ask the user to determine if the split is an occurrence of the forecast transaction:
                                UserResponse resp = resolver.assignSplitDateToForecastTransaction(split, forecastTransaction);
                                split.setDisposition(resp.getDisposition());
                                switch (split.getDisposition()) {

                                    case ADJUST: // Change the seed date for the budget item:
                                        split.getBudgetItem().setStartDate(Utility.stringDateDashToCalendarDate(
                                                resp.getResponse()));
                                        split.getBudgetItem().save(UPDATE);
                                        forecastTransaction.setPlannedDate(Utility.stringDateDashToCalendarDate(
                                                resp.getResponse()));
                                        // TODO: ForecastTransaction.updateAllDates(forecastTransaction, Utility.stringDateDashToCalendarDate(resp.getResponse()));
                                        forecast.setInSync(false);
                                        forecast.save(UPDATE);
                                        break;

                                    case ASSIGN: // Assign the split to the forecast transaction:
                                        break;

                                    case IGNORE:
                                        forecastTransaction = null;
                                        break;

                                    case DISPUTE:
                                        transaction.setIsImproper(true);
                                        transaction.save(INSERT_ON_DUPLICATE_UPDATE);
                                        transaction.getRegister().addSignificantEvent(transaction);
                                        break;
                                }
                            }
                            break;

                        default:
                            throw new ForecastException("Invalid item howOccurs:  " + split.getBudgetItem().getHowOccurs()
                                    + ".");
                    }
                    break;

                case WITHIN:  // Found the applicable forecast transaction.
                    split.setDisposition(ASSIGN);
                    break;

                case AFTER:  // There is money left from a prior period for the budgeted item:

                    switch (split.getBudgetItem().getHowOccurs()) {

                        case COLLECTION: // This split is an instance of underspending.  Roll the money forward:
                            while (forecastTransaction.fallsWithinWindow(transaction.getDate()) == Timing.AFTER) {
                                double remainingAmount = forecastTransaction.getRemainingAmount();
                                forecastTransaction.setRemainingAmount(0);
                                forecastTransaction.save(UPDATE);
                                forecastTransaction = it.getNext();
                                if (forecastTransaction != null) {
                                    forecastTransaction.setRemainingAmount(forecastTransaction.getRemainingAmount() +
                                            remainingAmount);
                                } else break;
                            }
                            split.setDisposition(ASSIGN);
                            break;

                        case ENVELOPE:  // Once the date for an envelope contribution passes, remove it:

                            // Roll up the expired items into the current item and mark them expired:
                            do {
                                // The budget item contains the running balance, so add this forecast transaction to it:
                                split.getBudgetItem().setRunningBalance(split.getBudgetItem().getRunningBalance() +
                                        forecastTransaction.getRemainingAmount());
                                split.getBudgetItem().save(UPDATE);

                                // and zero out the forecast transaction:
                                forecastTransaction.setRemainingAmount(0);
                                forecastTransaction.save(UPDATE);
                                forecastTransaction = it.getNext();
                            } while (forecastTransaction != null &&
                                    forecastTransaction.fallsWithinWindow(transaction.getDate()) == Timing.AFTER);
                            split.setDisposition(ASSIGN);
                            break;

                        case PERIODIC: // The transaction was paid late?
                        case VARIABLE_PERIODIC:
                        case UNPLANNED:

                            // Determine if the actual date a forecast transaction occurred is "on or about" the planned date:
                            int variance = Utility.daysBeteween(transaction.getDate(), forecastTransaction.getPlannedDate());
                            if (!split.getBudgetItem().withinNormalDateVariance(variance)) {

                                // Ask the user to determine if the split is an occurrence of the forecast transaction:
                                UserResponse resp = resolver.assignSplitDateToForecastTransaction(split, forecastTransaction);
                                split.setDisposition(resp.getDisposition());
                                switch (split.getDisposition()) {

                                    case ADJUST: // Change the seed date for the budget item:
                                        split.getBudgetItem().setStartDate(Utility.stringDateDashToCalendarDate(
                                                resp.getResponse()));
                                        split.getBudgetItem().save(UPDATE);

                                        forecastTransaction.getForecastItem().setNextDate(Utility.stringDateDashToCalendarDate(
                                                resp.getResponse()));
                                        forecastTransaction.getForecastItem().save(UPDATE);

                                        forecastTransaction.setPlannedDate(Utility.stringDateDashToCalendarDate(
                                                resp.getResponse()));
                                        forecastTransaction.save(UPDATE);

                                        forecast.setInSync(false);
                                        forecast.save(UPDATE);
                                        break;

                                    case ASSIGN: // Assign the split to the forecast transaction:
                                        break;

                                    case IGNORE:
                                        forecastTransaction = null;
                                        break;

                                    case DISPUTE:
                                        transaction.setIsImproper(true);
                                        transaction.save(INSERT_ON_DUPLICATE_UPDATE);
                                        transaction.getRegister().addSignificantEvent(transaction);
                                        break;
                                }
                            }
                            break;
                    }
                    break;
            }
        }
        return forecastTransaction;
    }


    // Deduct or zero the amount of the split from the forecast transaction or budget item as appropriate:
    public static ForecastTransaction deductSplitAmount(Forecast forecast, Transaction transaction,
                                                        TransactionResolverInt resolver, TransactionSplit split, ForecastTransaction forecastTransaction)
            throws EntityException, BudgetException, Exception, RegisterException {

        UserResponse resp;
        double remainingAmount = 0;
        // If the amount is substantially different than the assigned budget items:
        if (!split.getBudgetItem().withinNormalAmountVariance(forecastTransaction.getRemainingAmount() - split.getAmount())) {

            // Ask the user to determine if the split is an occurrence of the forecast transaction:
            resp = resolver.transactionAmountDiscrepancy(transaction, split, forecastTransaction);
            split.setDisposition(resp.getDisposition());
            switch (split.getDisposition()) {

                case ADJUST: // Change the amount of the budget item:
                    split.getBudgetItem().setAmount(Double.parseDouble(resp.getResponse()));
                    split.getBudgetItem().save(UPDATE);

                    forecastTransaction.getForecastItem().setAmount(Double.parseDouble(resp.getResponse()));
                    forecastTransaction.getForecastItem().save(UPDATE);

                    forecastTransaction.setRemainingAmount(Double.parseDouble(resp.getResponse()));
                    forecastTransaction.save(UPDATE);

                    forecast.setInSync(false);
                    forecast.save(UPDATE);
                    break;

                case ASSIGN: // The actual amount is ok this one time.
                    break;

                case DISPUTE:
                    transaction.setIsImproper(true);
                    transaction.save(INSERT_ON_DUPLICATE_UPDATE);
                    transaction.getRegister().addSignificantEvent(transaction);
                    remainingAmount = forecastTransaction.getRemainingAmount();
                    break;

                case IGNORE:
                    break;
            }
        }

        // Deduct the actual amount from the planned amount:
        if (split.getDisposition() != DISPUTE && split.getDisposition() != IGNORE) {
            switch (split.getBudgetItem().getHowOccurs()) {

                case COLLECTION:

                    // If the user has overspent on this item, e.g. the amount of the split is greater than the remaining
                    // amount in the current period of the budgeted amount per period for this budget item:
                    if (split.getAmount() < forecastTransaction.getRemainingAmount()) {

                        // Ask the user what they would like to do:
                        resolver.say("The amount that was allocated for this budget item in the current period (" +
                                Utility.formatDollarAmount(split.getBudgetItem().getAmount()) + ") is exhausted.");
                        split.setDisposition(resolver.assignOverageAmount(split.getAmount()));

                        // Execute the user's request:
                        switch (split.getDisposition()) {

                            case ADJUST:  // The user would like to increase the budgeted amount to cover the overage:
                                split.getBudgetItem().setAmount(split.getBudgetItem().getAmount() + split.getAmount());
                                split.getBudgetItem().save(INSERT_ON_DUPLICATE_UPDATE);
                                forecast.setInSync(false);
                                forecast.save(UPDATE);
                                break;

                            case DISPUTE:  // The user believes that the register transaction is in error and would like to
                                // dispute it and not reconcile the split:
                                transaction.setIsImproper(true);
                                transaction.save(UPDATE);
                                transaction.getRegister().addSignificantEvent(transaction);
                                break;

                            case IGNORE:  // This is a one time overage.  Ignore the overage and do not reconcile the split.
                                // However, the amount spent exceeds the amount budgeted for this period, so zero
                                // out the remaining amount for this budget item in the current period:
                                forecastTransaction.setRemainingAmount(0);
                                forecastTransaction.save(UPDATE);
                                break;

                            case ROLL_FORWARD:
                                remainingAmount = split.getAmount() - forecastTransaction.getRemainingAmount();
                                forecastTransaction.setRemainingAmount(0);
                                forecastTransaction.save(UPDATE);
                                ForecastTransactionIterator it = ForecastTransaction.getNonZeroForecastTransactionsForBudgetItem(
                                        split.getIdBudgetItem(), forecast.getId());
                                ForecastTransaction nextForecastTransaction = it.getNext();
                                if (nextForecastTransaction != null) {
                                    forecastTransaction = nextForecastTransaction;
                                    split.setAmount(remainingAmount);
                                }
                                break;
                        }
                    }

                    // If the user hasn't cancelled reconciliation of this split:
                    if (split.getDisposition() != DISPUTE && split.getDisposition() != IGNORE) {

                        // Calculate the remaining amount in the forecast transaction:
                        forecastTransaction.setRemainingAmount(forecastTransaction.getRemainingAmount() - split.getAmount());
                        forecastTransaction.save(UPDATE);

                        // Save off the remaining amount so that we can inform the user later:
                        remainingAmount = forecastTransaction.getRemainingAmount();

                        // Create an overspent significant event for the import summary:
                        if (forecastTransaction.getRemainingAmount() > 0) {
                            forecast.addSignificantEvent(forecastTransaction);
                        }
                    }
                    break;

                case ENVELOPE:
                    split.getBudgetItem().setRunningBalance(split.getBudgetItem().getRunningBalance() +
                            split.getAmount());
                    split.getBudgetItem().save(UPDATE);
                    remainingAmount = split.getBudgetItem().getRunningBalance();
                    if (split.getBudgetItem().getRunningBalance() < 0) {
                        forecast.addSignificantEvent(forecastTransaction);
                    }
                    break;

                case PERIODIC:
                case VARIABLE_PERIODIC:
                case UNPLANNED:
                    forecastTransaction.setRemainingAmount(0);
                    forecastTransaction.save(UPDATE);
                    break;
            }

            resolver.say("Deduct split amount of " + Utility.formatDollarAmount(split.getAmount()) + " from forecast " +
                    "transaction.  Date planned = " + Utility.calendarDateToStringDate(forecastTransaction.getPlannedDate()) +
                    ", Date authorized = " + Utility.calendarDateToStringDate(transaction.getAuthorizationDate()) +
                    ", Date posted = " + Utility.calendarDateToStringDate(transaction.getPostDate()) + ", Category = " +
                    forecastTransaction.getForecastItem().getCategory() + ", Payee = " +
                    forecastTransaction.getForecastItem().getPayee() + ", Budgeted Amount = " +
                    Utility.formatDollarAmount(forecastTransaction.getForecastItem().getAmount()) + ", Remaining amount = " +
                    Utility.formatDollarAmount(remainingAmount) + ".");

        } // End if the transaction isn't disputed or ignored.

        return forecastTransaction;
    }


    /*
     *   Get a list of forecast transactions that a particular user is monitoring closely:
     */
    public static List<Entity> getForecastTransactionsOfInterest(User user) throws EntityException, Exception,
            BudgetException, RegisterException {
        final List<Entity> items = new ArrayList<>();

        // First get a list of the items of interest to the user:
        ResultSet rsII = ItemOfInterest.getItemsOfInterestForUser(user);

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
        ForecastTransaction forecastTransaction = getApplicableForecastTransaction(ForecastItem.getByBudgetItemId(forecast,
                idBudgetItem), date);
        return forecastTransaction;
    }


    /**
     * Find the transaction in the forecast that applies to the specified date:
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
     * Get the applicable non-zero occurrence of a forecast transaction for a given date .  The algorithm used is to
     * find the first non-zero forecast transaction for a given forecast item.  Then if that transaction is prior to the
     * applicability period of the specified date, roll up the old forecast transactions until we get to the one that is.
     *
     * @param forecastItem The item to find a forecast transaction for.
     * @param date         The that defines the applicability period.
     * @return The applicable forecast transaction if there is one, else null.
     */
    @Nullable
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
                                // The budget item contains the running balance, so add this forecast transaction to it:
                                BudgetItem budgetItem = forecastItem.getBudgetItem();
                                budgetItem.setRunningBalance(budgetItem.getRunningBalance() +
                                        forecastTransaction.getRemainingAmount());
                                budgetItem.save(UPDATE);

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
     * non-zero transactions that apply to it.  Given that fact, the algorithm is to simply get the head a list of
     * transactions for the item in descending order beginning with the last transaction to occur on or before the
     * specified date. If that transaction does not apply to the date, then the next occurrence of a forecast
     * transaction must apply (the applicability period is prior to the item) so return it (if there is one).
     *
     * @param forecastItem
     * @param date
     * @return
     */
    private static ForecastTransaction getApplicableZeroOccurrence(ForecastItem forecastItem, Calendar date)
            throws SQLException, EntityException, ForecastException, BudgetException {

        ForecastTransaction forecastTransaction = null;

        // Find the last occurrence of a forecast transaction on or before today for this item of interest:
        String lastOccurrenceBeforeDateQuery =
                "select fi.category as 'fi.category', fi.payee as 'fi.payee', " + ForecastTransaction.getSelectColumns() +
                        "from forecast_transaction ft " +
                        "inner join forecast_item fi on ft.ForecastItem_idForecastItem = fi.idForecastItem " +
                        "inner join budget_item bi on fi.BudgetItem_idBudgetItem = bi.idBudgetItem " +
                        "inner join items_of_interest ii on bi.idBudgetItem = ii.BudgetItem_idBudgetItem " +
                        "where ft.plannedDate <= current_date() and fi.category = \"" + forecastItem.getCategory() + "\" and " +
                        "fi.payee = \"" + forecastItem.getPayee() + "\" " +
                        "order by ft.plannedDate desc " +
                        "limit 1";
        ResultSet rsLO = EntityInt.getRS(lastOccurrenceBeforeDateQuery, "retrieve the latest occurrence " +
                "of a the forecast transaction for forecast item" + forecastItem + " before " +
                Utility.calendarDateToStringDate(date) + ".");

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
                    // The specified date occurs after the applicability window of the last transaction scheduled that
                    // date.  By the "bracketing principle" if forecast transaction immediately prior to a date does
                    // not apply to that date, then one immediately after must, so return that one:
                    forecastTransaction = getNextOccurrence(forecastItem, date);
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
                ForecastTransaction.getSelectQuery() +
                        " where " +
                        "ft.ForecastItem_idForecastItem = uuid_to_bin ('" + forecastItem.getId() + "') and " +
                        "ft.plannedDate > " + Utility.calendarDateToSqlDateString(date) + " " +
                        "order by ft.plannedDate asc " +
                        "limit 1";
        ResultSet rsNO = EntityInt.getRS(nextOccurrenceQuery, "retrieve the next occurrence of a forecast " +
                "transactions after the latest occurrence of that forecast transaction for the forecast item" +
                forecastItem + ".");

        // If there is a next occurrence:
        ForecastTransaction forecastTransaction = null;
        if (rsNO.next()) {

            // then retrieve it:
            forecastTransaction = new ForecastTransaction(rsNO);

        } else {

            // This is normal if the forecast item is unplanned because unplanned forecast items only occur in the forecast
            // if they are required to reconcile transaction splits.  However if it isn't unplanned:
            if (forecastItem.getHowOccurs() != Item.HowOccurs.UNPLANNED) {
                // Then this is an odd situation.  The item either expired, or the forecast ended:
                Utility.getResolver().say("Warning:  There is no forecast transaction for forecast item " +
                        forecastItem.getCategory() + ", " + forecastItem.getPayee() + " that occurs after today's date.");
            }
        }

        return forecastTransaction;
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

        // Set all the forecast transactions to "not the first occurrence".
        EntityInt.executeUpdate(getUpdateQuery() + "firstOccurrence = false", "attempting to set " +
                "the first occurrence flags to false in the forecast.");

        // Set the first occurrence of every forecast transaction to "first occurrence" and roll up any expired
        // transactions.  The algorithm used is to go get all the forecast items in the forecast then for each one get
        // the earliest occurrence of a forecast transaction for it in the forecast and set that occurrence as the first
        // occurrence, then get the applicable transaction to today's date which will roll any expired transactions
        // as a side effect:
        ResultSet rsFI = ForecastItem.getAllUsableForecastItemsInForecast(forecast);
        Calendar today = Calendar.getInstance();
        if (rsFI != null) {
            Utility.getResolver().say("\nCleaning up the forecast.");
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
                    // It is unusual for usable forecast item not to occur in the forecast, so issue a warning:
                        ForecastItem forecastItem = new ForecastItem(rsFI);
                        Utility.getResolver().say("\nWARNING:  This forecast item does not occur in the forecast:\n" +
                                forecastItem.toString());
                }
            }
        }
    }
} // End class ForecastTransaction.
