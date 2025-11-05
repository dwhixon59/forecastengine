package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.TransactionSplit;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.entity.MatchQuery;
import com.hixon.financialApp.model.forecast.*;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.base.UserResponse;
import com.hixon.financialApp.view.base.ViewInt;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static com.hixon.financialApp.model.entity.EntityInt.SaveMethod.INSERT_ON_DUPLICATE_UPDATE;
import static com.hixon.financialApp.model.entity.EntityInt.SaveMethod.UPDATE;
import static com.hixon.financialApp.model.entity.EntityInt.executeUpdate;
import static com.hixon.financialApp.model.forecast.ForecastTransactionSplit.SplitDisposition.ASSIGN;
import static com.hixon.financialApp.model.forecast.ForecastTransactionSplit.SplitDisposition.IGNORE;
import static com.hixon.financialApp.utility.Utility.*;
import static com.hixon.financialApp.view.base.ViewInt.*;

public class ForecastTransactionController {

    /*
     * Fields for ForecastTransactionController:
     */
    protected Register register;
    protected Budget budget;
    protected Forecast forecast;
    protected ViewInt view;
    protected NotificationServiceInt notificationService;


    /**
     * Constructors and destructor for ForecastTransactionController:
     */
    public ForecastTransactionController(Register register, Budget budget, Forecast forecast, ViewInt view, NotificationServiceInt
            notificationService) {
        this.register = register;
        this.budget = budget;
        this.forecast = forecast;
        this.view = view;
        this.notificationService = notificationService;
    }


    /**
     * Main methods for ForecastTransactionController:
     */

    /**
     * Manage forecast transactions interactively using a unified search-based interface.
     * The workflow is:
     * 1. Search for a forecast transaction (or create new)
     * 2. Choose what to do with it (view, update, delete, manage splits)
     *
     * @throws Exception if any error occurs during management operations
     */
    public void manageForecastTransactions() throws Exception {
        boolean done = false;

        while (!done) {
            try {
                // Ask whether to search for existing or create new
                String choice = view.selectFromMenu("What would you like to do?",
                        List.of("search for existing forecast transaction", "create new forecast transaction"),
                        DO_NOT_ALLOW_NONE, SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);

                if (choice.equals("c")) {
                    // User chose to create - not supported for forecast transactions as they're generated
                    view.say("Forecast transactions are automatically generated from forecast items.");
                    view.say("To add transactions, create or modify forecast items in the budget.");
                    continue;
                }

                // User chose to search
                ForecastTransaction selectedTransaction = selectForecastTransaction();

                if (selectedTransaction == null) {
                    // User cancelled the search
                    continue;
                }

                // User selected an existing transaction - show action menu
                boolean actionComplete = false;
                while (!actionComplete) {
                    // Display the selected transaction
                    view.say();
                    view.say("Selected forecast transaction:");
                    view.say("  " + selectedTransaction.toStringConcise());

                    // Ask what to do with this transaction
                    String action = view.selectFromMenu("What would you like to do with this forecast transaction?",
                            List.of("view details", "update this transaction", "delete this transaction",
                                    "manage splits/dispositions", "search again"),
                            DO_NOT_ALLOW_NONE, SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);

                    switch (action) {
                        case "v":  // view details
                            displayForecastTransactionDetails(selectedTransaction);
                            break;

                        case "u":  // update this transaction
                            updateForecastTransaction(selectedTransaction);
                            actionComplete = true;  // After update, go back to search
                            break;

                        case "d":  // delete this transaction
                            deleteForecastTransaction(selectedTransaction);
                            actionComplete = true;  // After delete, go back to search
                            break;

                        case "m":  // manage splits/dispositions
                            manageForecastTransactionSplits(selectedTransaction);
                            break;

                        case "s":  // search again
                            actionComplete = true;
                            break;

                        case "c":  // cancel
                            actionComplete = true;
                            break;

                        default:
                            throw new InvalidEntryException("Unexpected menu option: " + action);
                    }
                }

            } catch (CancelException e) {
                // User cancelled - return to data manager menu
                done = true;
            } catch (QuitException e) {
                throw e;
            }
        }
    }

    /**
     * Search for and select a forecast transaction.
     *
     * @return The selected ForecastTransaction, or null if cancelled
     * @throws Exception if any error occurs
     */
    private ForecastTransaction selectForecastTransaction() throws Exception {
        SelectionController selectionController = new SelectionController(view);

        // Build query that only selects forecast_transaction columns but joins for search/sort
        String queryBeforeMatch = "select " + ForecastTransaction.getSelectColumns() +
                "from forecast_transaction ft " +
                "inner join forecast_item fi on ft.ForecastItem_idForecastItem = fi.idForecastItem " +
                "WHERE fi.Forecast_idForecast = uuid_to_bin('" + forecast.getId() + "') AND ";

        String queryAfterMatch = "ORDER BY ft.plannedDate DESC";

        return selectionController.getByNameFullText(
                null,  // No seed name
                forecast,  // Scope to this forecast
                DO_NOT_ALLOW_NONE,
                DO_NOT_ALLOW_CREATE,
                ALLOW_CANCEL,
                ALLOW_QUIT,
                DO_NOT_ALLOW_SKIP,
                ForecastTransaction.getPrintableTypeName_static(),
                ForecastTransaction::toStringCompact,
                new MatchQuery(queryBeforeMatch,
                        "ft.plannedDate",
                        "fi.category, fi.payee, fi.memo",
                        queryAfterMatch),
                rs -> {
                    try {
                        return new ForecastTransaction(rs);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                },
                (scope, newName) -> null);  // Don't allow creating new forecast transactions
    }

    /**
     * Display detailed information about a forecast transaction.
     *
     * @param transaction The forecast transaction to display
     * @throws Exception if any error occurs
     */
    private void displayForecastTransactionDetails(ForecastTransaction transaction) throws Exception {
        view.say();
        view.say("Forecast Transaction Details:");
        view.say("──────────────────────────────────────");
        view.say("Planned Date: " + calendarDateToStringDate(transaction.getPlannedDate()));
        view.say("Remaining Amount: " + formatDollarAmount(transaction.getRemainingAmount()));
        view.say("Running Balance: " + formatDollarAmount(transaction.getRunningBalance()));
        view.say("Memo: " + (transaction.getMemo() != null ? transaction.getMemo() : ""));
        view.say("First Occurrence: " + (transaction.isFirstOccurrence() ? "yes" : "no"));
        view.say("Overridden: " + (transaction.isOverridden() ? "yes" : "no"));
        view.say("Found: " + (transaction.isFound() ? "yes" : "no"));

        // Display forecast item details
        ForecastItem item = transaction.getForecastItem();
        view.say();
        view.say("Associated Forecast Item:");
        view.say("  Category: " + item.getCategory());
        view.say("  Payee: " + item.getPayee());
        view.say("  Budgeted Amount: " + formatDollarAmount(item.getAmount()));
        view.say("  Period: " + item.getPeriod());

        view.say("──────────────────────────────────────");
    }

    /**
     * Update a forecast transaction.
     *
     * @param transaction The forecast transaction to update
     * @throws Exception if any error occurs
     */
    private void updateForecastTransaction(ForecastTransaction transaction) throws Exception {
        view.say();
        view.say("──── Update Forecast Transaction ────");

        boolean done = false;
        while (!done) {
            // Show current values
            view.say();
            view.say("Current values:");
            view.say("  Planned Date: " + calendarDateToStringDate(transaction.getPlannedDate()));
            view.say("  Remaining Amount: " + formatDollarAmount(transaction.getRemainingAmount()));
            view.say("  Memo: " + (transaction.getMemo() != null ? transaction.getMemo() : ""));
            view.say("  Overridden: " + (transaction.isOverridden() ? "yes" : "no"));
            view.say("  Found: " + (transaction.isFound() ? "yes" : "no"));

            // Ask what to update
            String choice = view.selectFromMenu("What would you like to update?",
                    List.of("planned date", "remaining amount", "memo", "overridden flag", "found flag", "done - save changes"),
                    DO_NOT_ALLOW_NONE, SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);

            switch (choice) {
                case "p":  // planned date
                    String dateStr = view.getResponseString("Enter new planned date (MM-DD-YYYY):",
                            calendarDateToStringDate(transaction.getPlannedDate()), ALLOW_NONE,
                            DO_NOT_SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);
                    Calendar newDate = stringDateDashToCalendarDate(dateStr);
                    transaction.setPlannedDate(newDate);
                    transaction.setOverridden(true);  // Mark as overridden when date changes
                    break;

                case "r":  // remaining amount
                    double newAmount = view.getResponseCurrency("Enter new remaining amount:",
                            ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);
                    transaction.setRemainingAmount(newAmount);
                    transaction.setOverridden(true);  // Mark as overridden when amount changes
                    break;

                case "m":  // memo
                    String newMemo = view.getResponseString("Enter new memo:",
                            transaction.getMemo() != null ? transaction.getMemo() : "",
                            ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                            ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);
                    transaction.setMemo(newMemo);
                    break;

                case "o":  // overridden flag
                    String overriddenStr = view.getResponseString("Mark as overridden? (y/n):",
                            transaction.isOverridden() ? "y" : "n", ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                            ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);
                    transaction.setOverridden(overriddenStr.equalsIgnoreCase("y"));
                    break;

                case "f":  // found flag
                    String foundStr = view.getResponseString("Mark as found? (y/n):",
                            transaction.isFound() ? "y" : "n", ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                            ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);
                    transaction.setFound(foundStr.equalsIgnoreCase("y"));
                    break;

                case "d":  // done
                    if (transaction.isDirty()) {
                        transaction.save(UPDATE);
                        view.say("Forecast transaction successfully updated.");
                    }
                    done = true;
                    break;

                case "c":  // cancel
                    view.say("Update cancelled.");
                    done = true;
                    break;

                default:
                    throw new InvalidEntryException("Unexpected menu option: " + choice);
            }
        }
    }

    /**
     * Delete a forecast transaction after confirmation.
     *
     * @param transaction The forecast transaction to delete
     * @throws Exception if any error occurs
     */
    private void deleteForecastTransaction(ForecastTransaction transaction) throws Exception {
        view.say();
        view.say("You are about to delete:");
        view.say("  " + transaction.toStringConcise());

        view.say();
        view.say("WARNING: Deleting this forecast transaction cannot be undone.");
        view.say("The transaction will be regenerated if you rebuild the forecast.");

        String confirm = view.getResponseString("Are you sure you want to delete this forecast transaction? (yes/no):",
                "no", ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);

        if (confirm.equalsIgnoreCase("yes")) {
            transaction.delete();
            view.say("Forecast transaction successfully deleted.");
        } else {
            view.say("Delete cancelled.");
        }
    }

    /**
     * Manage splits/dispositions for a forecast transaction.
     *
     * @param transaction The forecast transaction to manage splits for
     * @throws Exception if any error occurs
     */
    private void manageForecastTransactionSplits(ForecastTransaction transaction) throws Exception {
        boolean done = false;

        while (!done) {
            try {
                view.say();
                view.say("──── Manage Splits for Forecast Transaction ────");
                view.say(transaction.toStringCompact());

                // Get splits for this forecast transaction
                List<ForecastTransactionSplit> splits = getForecastTransactionSplits(transaction);

                if (splits.isEmpty()) {
                    view.say("No splits currently associated with this forecast transaction.");
                    view.say("Splits are created during forecast reconciliation.");

                    view.getResponseString("Press Enter to return (or 'C' to cancel, 'Q' to quit):",
                            ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);
                    done = true;
                    continue;
                }

                // Display numbered list of splits
                view.say();
                view.say("Splits:");
                List<String> splitDisplayList = new ArrayList<>();
                for (ForecastTransactionSplit split : splits) {
                    // Query for the transaction split using proper column aliases
                    String query = TransactionSplit.getSelectQuery() +
                            "WHERE ts.BudgetItem_idBudgetItem = uuid_to_bin('" + split.getIdBudgetItem() + "') AND " +
                            "ts.Transaction_idTransaction = uuid_to_bin('" + split.getIdTransaction() + "')";
                    ResultSet rs = EntityInt.getRS(query, "getting transaction split");
                    if (rs != null && rs.next()) {
                        TransactionSplit transSplit = new TransactionSplit(rs);
                        String display = formatDollarAmount(transSplit.getAmount()) + " → " +
                                transSplit.getBudgetItem().getPayee() + " (" + split.getDisposition() + ")";
                        splitDisplayList.add(display);
                    }
                }

                Integer selectedIndex = view.selectByPositionFromList(
                        "Select a split to update its disposition:",
                        splitDisplayList,
                        DO_NOT_ALLOW_NONE,
                        ALLOW_CANCEL,
                        ALLOW_QUIT,
                        DO_NOT_ALLOW_SKIP);

                if (selectedIndex == null || selectedIndex < 0 || selectedIndex >= splits.size()) {
                    view.say("Invalid selection.");
                    continue;
                }

                ForecastTransactionSplit selectedSplit = splits.get(selectedIndex);

                // Update the disposition
                updateSplitDisposition(selectedSplit);

            } catch (CancelException e) {
                done = true;
            }
        }
    }

    /**
     * Get all splits for a forecast transaction.
     *
     * @param transaction The forecast transaction
     * @return List of ForecastTransactionSplit objects
     * @throws Exception if any error occurs
     */
    private List<ForecastTransactionSplit> getForecastTransactionSplits(ForecastTransaction transaction) throws Exception {
        String query = ForecastTransactionSplit.getSelectQuery() +
                " WHERE fts.ForecastTransaction_idForecastTransaction = uuid_to_bin('" + transaction.getId() + "')";
        ResultSet rs = EntityInt.getRS(query, "getting forecast transaction splits");

        List<ForecastTransactionSplit> splits = new ArrayList<>();
        if (rs != null) {
            while (rs.next()) {
                splits.add(new ForecastTransactionSplit(rs));
            }
        }
        return splits;
    }

    /**
     * Update the disposition of a forecast transaction split.
     *
     * @param split The split to update
     * @throws Exception if any error occurs
     */
    private void updateSplitDisposition(ForecastTransactionSplit split) throws Exception {
        view.say();
        view.say("Current disposition: " + split.getDisposition());
        view.say();
        view.say("Disposition meanings:");
        view.say("  ADJUST - Adjust the forecast transaction amount");
        view.say("  ASSIGN - Assign this split to the forecast transaction");
        view.say("  IGNORE - Ignore this split for this forecast transaction");
        view.say("  DISPUTE - Mark this split as disputed");
        view.say("  ROLL_FORWARD - Roll this amount forward to next occurrence");
        view.say("  ZERO_OUT - Zero out the forecast transaction");

        String newDispositionStr = view.selectFromMenu("Select new disposition:",
                List.of("ADJUST", "ASSIGN", "IGNORE", "DISPUTE", "ROLL_FORWARD", "ZERO_OUT"),
                DO_NOT_ALLOW_NONE, SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);

        if (newDispositionStr.equals("c")) {
            return;  // User cancelled
        }

        ForecastTransactionSplit.SplitDisposition newDisposition =
                ForecastTransactionSplit.SplitDisposition.valueOf(newDispositionStr.toUpperCase());

        split.setDisposition(newDisposition);
        split.save(UPDATE);
        view.say("Split disposition successfully updated.");
    }

    /**
     * Zero out the amounts for all the Forecast Transactions that are marked not found:
     *
     * @throws EntityException
     * @throws RegisterException
     * @throws SQLException
     * @throws BudgetException
     */
    public void zeroNotFound()
            throws EntityException, RegisterException, SQLException, BudgetException {

        // List the forecast transactions that are about to be zeroed out for the user:
        ResultSet rs = EntityInt.getRS(ForecastTransaction.getSelectQuery() + " " +
                        "inner join forecast_item fi on ft.ForecastItem_idForecastItem = " +
                        "fi.idForecastItem " +
                        "where found = false and remainingAmount <> 0 " +
                        "order by ft.plannedDate desc, fi.category asc, fi.payee asc",
                "Forecast Transactions that are marked not found."
        );
        boolean firstTime = true;
        while (rs.next()) {
            if (firstTime) {
                getView().say("\nThe following transactions were deleted from the spreadsheet and will be " +
                        "zeroed out in the forecast:  ");
                firstTime = false;
            }
            ForecastTransaction forecastTransaction = new ForecastTransaction(rs);
            getView().say(forecastTransaction.toStringConcise() + " .");
        }

        // Zero out the forcast transactions that were deleted from the spreadsheet:
        executeUpdate(ForecastTransaction.getUpdateQuery() + "remainingAmount = 0 where found = false and " +
                "remainingAmount <> 0", "to zero the Forecast Transactions that are marked not found.");
    }

    /**
     * This method finds the applicable forecast transaction for a given split.  The algorithm is to first get a list of
     * non-zero forecast transactions for the budget item in chronological order.  Then proceed through the list looking
     * for the forecast transaction where the date of the transaction associated with the split falls into the
     * applicability period.  Various special cases are handled based upon how the item occurs, like periodic vs.
     * collection type items and whether the split date falls just outside the forecast transaction applicability
     * period.
     *
     * @param forecast The forecast is which to look for applicable forecast transactions.
     * @param split    The split to match to a forecast transaction.
     * @return The applicable forecast transaction if one is found, else null.
     * @throws EntityException
     * @throws Exception
     * @throws BudgetException
     * @throws RegisterException
     */
    public ForecastTransaction getApplicableForecastTransaction(Forecast forecast, TransactionSplit split)
            throws EntityException, Exception, BudgetException, RegisterException {

        // Get a list of forecast transactions beginning with the earliest non-zero amount occurrence of a forecast
        // transaction in the forecast for the budget item associated with the split:
        ForecastTransactionIterator it =
                ForecastTransaction.getNonZeroForecastTransactionsForBudgetItem(split.getIdBudgetItem(), forecast.getId());

        // Find the forecast transaction in the list that this split applies to.  Roll up any old forecast transactions
        // encountered in the process:
        ForecastTransaction forecastTransaction = it.getNext();
        if (forecastTransaction != null) {

            ForecastTransaction.Timing timing = forecastTransaction.fallsWithinWindow(split.getTransaction().getDate());
            switch (timing) {

                case PRIOR_TO:  // The split occurs before the period of this forecast transaction:

                    switch (split.getBudgetItem().getHowOccurs()) {

                        case COLLECTION: // This split is an instance of overspending.
                            // If the transaction split occurred prior to the first occurrence of forecast item in the
                            // forecast, then it doesn't apply because collection forecast items always occur prior to any
                            // associated splits:
                            if (forecastTransaction.isFirstOccurrence()) {
                                getView().say("Split occurs before the first occurrence of the budget item " +
                                        split.getBudgetItem().getPayee() + " in the forecast.  Ignoring it.");
                                forecastTransaction = null;
                            } else {
                                // Set the applicable forecast transaction to the one applicable to the date of the
                                // split regardless of the fact that forecast transaction is exhausted:
                                forecastTransaction = ForecastTransaction.getApplicableZeroOccurrence(forecast,
                                        split.getIdBudgetItem(), split.getTransaction().getDate());
                            }
                            break;

                        case ENVELOPE: // We are before the effective start of this forecast item so nothing to reconcile to.
                            getView().say("Split occurred before the forecast item became effective.  Ignoring.");
                            split.setDisposition(IGNORE);
                            forecastTransaction = null;
                            break;

                        case PERIODIC: // The transaction was paid early?
                        case VARIABLE_PERIODIC:

                            // Determine if the actual date a forecast transaction occurred is "on or about" the planned date:
                            int variance = Utility.daysBetween(forecastTransaction.getPlannedDate(),
                                    split.getTransaction().getDate());

                            // If it is not on or about the planned date, then ask the user what they want to do:
                            if (!split.getBudgetItem().isWithinNormalDateVariance(variance)) {

                                // Ask the user to determine if the split is an occurrence of the forecast transaction:
                                ForecastController forecastController = new ForecastController(register, budget, forecast,
                                        view, notificationService);
                                UserResponse resp = forecastController.assignSplitDateToForecastTransaction(split,
                                        forecastTransaction);
                                split.setDisposition(resp.getDisposition());
                                switch (split.getDisposition()) {

                                    case ADJUST: // Change the seed date for the budget item:
                                        split.getBudgetItem().setStartDate(Utility.stringDateDashToCalendarDate(
                                                resp.getResponse()));
                                        split.getBudgetItem().save(UPDATE);
                                        forecastTransaction.setPlannedDate(Utility.stringDateDashToCalendarDate(
                                                resp.getResponse()));
                                        // TODO: ForecastTransaction.updateAllDates(forecastTransaction,
                                        //  Utility.stringDateDashToCalendarDate(resp.getResponse()));
                                        forecast.setInSync(false);
                                        forecast.save(UPDATE);
                                        break;

                                    case ASSIGN: // Assign the split to the forecast transaction:
                                        break;

                                    case IGNORE:
                                        forecastTransaction = null;
                                        break;

                                    case DISPUTE:
                                        split.getTransaction().setIsImproper(true);
                                        split.getTransaction().save(INSERT_ON_DUPLICATE_UPDATE);
                                        split.getTransaction().getRegister().addSignificantEvent(split.getTransaction());
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
                            while (forecastTransaction.fallsWithinWindow(split.getTransaction().getDate()) == ForecastTransaction.Timing.AFTER) {
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
                                    forecastTransaction.fallsWithinWindow(split.getTransaction().getDate()) == ForecastTransaction.Timing.AFTER);
                            split.setDisposition(ASSIGN);
                            break;

                        case PERIODIC: // The transaction was paid late?
                        case VARIABLE_PERIODIC:
                        case UNPLANNED:

                            // Determine if the actual date a forecast transaction occurred is "on or about" the planned date:
                            int variance = Utility.daysBetween(split.getTransaction().getDate(),
                                    forecastTransaction.getPlannedDate());
                            if (!split.getBudgetItem().isWithinNormalDateVariance(variance)) {

                                // Ask the user to determine if the split is an occurrence of the forecast transaction:
                                ForecastController forecastController = new ForecastController(register, budget, forecast,
                                        view, notificationService);
                                UserResponse resp = forecastController.assignSplitDateToForecastTransaction(split, forecastTransaction);
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
                                        split.getTransaction().setIsImproper(true);
                                        split.getTransaction().save(INSERT_ON_DUPLICATE_UPDATE);
                                        split.getTransaction().getRegister().addSignificantEvent(split.getTransaction());
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
}
