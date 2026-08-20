package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.*;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.*;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.base.AbstractForecastView;
import com.hixon.financialApp.view.base.UserResponse;
import com.hixon.financialApp.view.base.ViewInt;
import com.hixon.financialApp.view.csv.CsvForecastView;
import com.hixon.financialApp.view.excel.ExcelForecastView;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import static com.hixon.financialApp.model.budget.Item.ItemType.INCOME;
import static com.hixon.financialApp.model.entity.EntityInt.SaveMethod.*;
import static com.hixon.financialApp.model.entity.EntityInt.executeUpdate;
import static com.hixon.financialApp.model.entity.EntityInt.getRS;
import static com.hixon.financialApp.model.forecast.ForecastTransactionSplit.SplitDisposition.*;
import static com.hixon.financialApp.utility.Utility.*;
import static com.hixon.financialApp.utility.Utility.StartDateType.*;
import static com.hixon.financialApp.view.base.ViewInt.*;
import static java.util.Calendar.DATE;
import static java.util.Calendar.MONTH;

public class ForecastController {

    /*
     * Fields for ForecastController:
     */
    protected SessionController sessionController;
    protected Register register;
    protected Budget budget;
    protected Forecast forecast;
    protected ViewInt view;
    protected NotificationServiceInt notificationService;


    /**
     * Constructors and destructor for ForecastController:
     */
    public ForecastController(SessionController sessionController) {
        this.sessionController = sessionController;
        this.register = sessionController.getRegister();
        this.budget = sessionController.getBudget();
        this.forecast = sessionController.getForecast();
        this.view = sessionController.getView();
        this.notificationService = sessionController.getNotificationService();
    }


    /**
     * Helper Methods for ForecastController:
     */

    /**
     * Calculate and save running balances for all forecast transactions in the forecast.
     * This method iterates through all forecast transactions in chronological order,
     * calculates the running balance for each, and saves it to the database.
     *
     * @param forecast The forecast to calculate running balances for
     * @throws Exception if an error occurs while calculating or saving running balances
     */
    public void calculateAndSaveRunningBalances(Forecast forecast) throws Exception {
        // Get the starting balance from the first register associated with the budget
        List<Register> registers = forecast.getBudget().getRegisters();
        if (registers == null || registers.isEmpty()) {
            return; // No registers, no running balance to calculate
        }

        double runningBalance = registers.getFirst().getBalance();

        // Get all forecast transactions in chronological order
        ForecastTransactionIterator forecastTransactions =
            ForecastTransaction.getForecastTransactionsStartingOn(forecast, forecast.getStartDate());

        ForecastTransaction forecastTransaction = forecastTransactions.getNext();

        // Calculate and save running balance for each transaction
        while (forecastTransaction != null) {
            runningBalance += forecastTransaction.getRemainingAmount();
            forecastTransaction.setRunningBalance(runningBalance);
            forecastTransaction.save(UPDATE);
            forecastTransaction = forecastTransactions.getNext();
        }
    }

    /**
     * Main methods for ForecastController:
     */
    /**
     * Ask the user if they want to regenerate the forecast:
     *
     * @return true if they want to regenerate the forecast
     */
    public boolean askRegenerateForecast() {
        return view.getYesOrNo("Changes were made to budget items and the forecast is out of sync now.  " +
                "Do you want to regenerate the long term forecast?");
    }

    /**
     * Ask the user for the starting date for rendering a forecast.  Getting a start date is not strictly necessary.  Most
     * of the time the user wants to render the entire forecast, not just the transactions on or after a certain date.
     *
     * @return The date that the forecast rendering should begin on.
     * @throws QuitException if the user quits the operation
     * @throws CancelException if the user cancels the operation
     */
    public Calendar askStartDate() throws QuitException, CancelException {
        // Get the starting date type:
        UserResponse response = getForecastStartDate();

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
     * Ask the user what to do if the split amount exceeds the budgeted amount:
     *
     * @param prompt
     * @return the user's response
     * @throws IOException
     */
    public ForecastTransactionSplit.SplitDisposition assignOverageAmount(String prompt) {
        ForecastTransactionSplit.SplitDisposition disposition = null;

        view.ask(prompt + "What would you like to do (a-adjust, d-dispute, i-ignore, r-roll)?  ");

        boolean done = false;
        while (!done) {
            done = true;
            String line = view.getResponseString();
            switch (line) {
                case "a":
                    disposition = ADJUST;
                    break;

                case "d":
                    disposition = DISPUTE;
                    break;

                case "i":
                    disposition = IGNORE;
                    break;

                case "r":
                    disposition = ROLL_FORWARD;
                    break;

                default:
                    view.ask("Please enter a, d, i or r.");
                    done = false;
            }
        }
        return disposition;
    }

    /**
     * Ask the user what to do if the split amount differs from the forecast transaction amount:
     *
     * @param split
     * @param forecastTransaction
     * @return the user's response
     */
    public UserResponse assignSplitAmountToForecastTransaction(TransactionSplit split, ForecastTransaction
            forecastTransaction) {
        UserResponse response = new UserResponse();

        view.say("Applicable  " + forecastTransaction.toStringConcise());
        view.ask("What would you like to do (a-adjust, s-assign, d-dispute, i-ignore)? ");

        boolean done = false;
        while (!done) {
            done = true;
            String line = view.getResponseString();
            switch (line) {
                case "a":
                    response.setDisposition(ADJUST);
                    response.setResponse(view.parseDollarAmount("Enter the new amount", split.getAmount()));
                    break;

                case "s":
                    response.setDisposition(ASSIGN);
                    break;

                case "d":
                    response.setDisposition(DISPUTE);
                    break;

                case "i":
                    response.setDisposition(IGNORE);
                    break;

                default:
                    view.say("Please enter a, s, d, or i.");
                    done = false;
            }
        }
        return response;
    }

    /**
     * Ask the user what to do if the split date differs from the forecast transaction date:
     *
     * @param split
     * @param forecastTransaction
     * @return the user's response
     * @throws EntityException
     * @throws SQLException
     */
    public UserResponse assignSplitDateToForecastTransaction(TransactionSplit split, ForecastTransaction
            forecastTransaction)
            throws EntityException, SQLException {
        UserResponse response = new UserResponse();

        view.say("Applicable " + forecastTransaction.toStringConcise());
        view.ask("What would you like to do (a-adjust, s-assign, d-dispute, i-ignore)? ");

        boolean done = false;
        while (!done) {
            done = true;
            String line = view.getResponseString();
            switch (line) {
                case "a":
                    response.setDisposition(ADJUST);
                    response.setResponse(view.parseStringDate("Enter the new date",
                            split.getTransaction().getDate()));
                    break;

                case "s":
                    response.setDisposition(ASSIGN);
                    break;

                case "d":
                    response.setDisposition(DISPUTE);
                    break;

                case "i":
                    response.setDisposition(IGNORE);
                    break;

                default:
                    view.say("Please enter a, s, d, or i.");
                    done = false;
            }
        }
        return response;
    }

    /**
     * Ask the user what to do when a candidate forecast transaction matches on merchant and/or
     * date, but its amount differs materially from the split amount (see
     * {@link com.hixon.financialApp.utility.ForecastTransactionMatcher#AUTO_MATCH_AMOUNT_TOLERANCE}).
     *
     * <p>This prevents silently auto-assigning an imported transaction to a planned forecast
     * transaction whose amount is very different (e.g. a $1,200 charge auto-matched to a $50
     * planned expense simply because the merchant matched).
     *
     * @param split               the transaction split being reconciled
     * @param forecastTransaction the candidate forecast transaction whose amount differs
     * @return the user's chosen disposition (ASSIGN, IGNORE, or DISPUTE)
     * @throws EntityException
     * @throws SQLException
     */
    public UserResponse confirmForecastTransactionAmountMatch(TransactionSplit split,
            ForecastTransaction forecastTransaction) throws EntityException, SQLException {
        UserResponse response = new UserResponse();

        view.say("The transaction amount (" + Utility.formatDollarAmount(Math.abs(split.getAmount())) +
                ") differs significantly from the planned amount (" +
                Utility.formatDollarAmount(Math.abs(forecastTransaction.getRemainingAmount())) + ") for " +
                forecastTransaction.toStringConcise() + ".");
        view.ask("What would you like to do (s-assign anyway, i-do not assign, d-dispute)? ");

        boolean done = false;
        while (!done) {
            done = true;
            String line = view.getResponseString();
            switch (line) {
                case "s":
                    response.setDisposition(ASSIGN);
                    break;

                case "d":
                    response.setDisposition(DISPUTE);
                    break;

                case "i":
                    response.setDisposition(IGNORE);
                    break;

                default:
                    view.say("Please enter s, i, or d.");
                    done = false;
            }
        }
        return response;
    }

    /**
     * Ask the user what to do when every scored candidate forecast transaction for a budget item
     * was disqualified because the transaction's merchant doesn't match any of the budget item's
     * assigned merchants. Falling through to the merchant-blind sequential fallback matcher here
     * would defeat the purpose of merchant validation (see
     * {@link ForecastTransactionController#getApplicableForecastTransaction}), so the user must
     * explicitly opt in before merchant-blind date/amount matching is allowed to proceed.
     *
     * @param split the transaction split whose merchant didn't match any candidate
     * @return the user's chosen disposition (ASSIGN to proceed without merchant validation, or IGNORE)
     * @throws EntityException
     * @throws SQLException
     * @throws RegisterException
     * @throws BudgetException
     */
    public UserResponse confirmMerchantMismatchFallback(TransactionSplit split)
            throws EntityException, SQLException, RegisterException, BudgetException {
        UserResponse response = new UserResponse();

        view.say("The merchant for this transaction (" +
                split.getTransaction().getMerchant().getName() +
                ") does not match any merchant assigned to budget item " +
                split.getBudgetItem().getPayee() + ".");
        view.ask("What would you like to do (p-proceed without merchant match, i-ignore)? ");

        boolean done = false;
        while (!done) {
            done = true;
            String line = view.getResponseString();
            switch (line) {
                case "p":
                    response.setDisposition(ASSIGN);
                    break;

                case "i":
                    response.setDisposition(IGNORE);
                    break;

                default:
                    view.say("Please enter p or i.");
                    done = false;
            }
        }
        return response;
    }

    /**
     * {@inheritDoc}
     */
    public UserResponse getForecastStartDate() throws QuitException, CancelException {
        UserResponse response = new UserResponse();

        view.say("What date do you want to start on?  (l-first of last month, <enter> first of next month, t-Today, " +
                "f-First of this month, o-One month from today, c-Custom date)");

        boolean done = false;
        while (!done) {
            done = true;
            try {
                String line = view.getResponseString("", null, ViewInt.ALLOW_NONE,
                        ViewInt.SHOW_CANCEL_QUIT_SKIP, ViewInt.ALLOW_CANCEL,
                        ViewInt.ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP, null);

                if (line == null) {
                    line = "";
                }

                switch (line) {
                    case "l":
                        response.setStartDate(FIRST_OF_LAST_MONTH);
                        break;

                    case "t":
                        response.setStartDate(TODAY);
                        break;

                    case "f":
                        response.setStartDate(FIRST_OF_THIS_MONTH);
                        break;

                    case "o":
                        response.setStartDate(ONE_MONTH_FROM_TODAY);
                        break;

                    case "c":
                        response.setStartDate(ARBITRARY_DATE);
                        Calendar startDate = Calendar.getInstance();
                        response.setDate(view.parseCalendarDate("Enter the start date", startDate));
                        break;

                    case "":
                        response.setStartDate(FIRST_OF_NEXT_MONTH);
                        break;

                    default:
                        view.say("Please enter l, <enter>, t, f, o, or c.");
                        done = false;
                }
            } catch (SkipException e) {
                // Skip shouldn't happen since we don't allow it, but handle gracefully
                view.say("Please enter l, <enter>, t, f, o, or c.");
                done = false;
            }
        }
        return response;
    }

    // Reconcile a register transaction with a forecast transaction:
    public void reconcile(Transaction transaction, List<TransactionSplit> splits)
            throws Exception {

        // For each split assigned to this transaction:
        ForecastTransactionSplit forecastTransactionSplit;
        String prefix = "";
        for (TransactionSplit split : splits
        ) {
            // Let the user know what split we are working on:
            view.say(prefix + split.toString());

            // If it hasn't already been reconciled:
            forecastTransactionSplit = ForecastTransactionSplit.getForecastTransactionSplit(forecast, split);
            if (forecastTransactionSplit == null) {

                // Find the forecast transaction in the list that this split applies to:
                ForecastTransactionController forecastTransactionController =
                        new ForecastTransactionController(sessionController);
                ForecastTransaction forecastTransaction =
                        forecastTransactionController.getApplicableForecastTransaction(forecast, split);

                // if we weren't able to match the split to a forecast transaction.
                if (forecastTransaction == null) {

                    // Create a forecast transaction and forecast item (if it doesn't already exist) for it so we have
                    // something to link the forecast transaction split to:
                    ForecastItem forecastItem = ForecastItem.getByBudgetItemId(forecast, split.getIdBudgetItem());
                    if (forecastItem == null) {
                        forecastItem = new ForecastItem(forecast, split.getBudgetItem());
                        forecastItem.setAmount(split.getAmount());
                        forecastItem.save(INSERT);
                    }
                    forecastTransaction = new ForecastTransaction(forecastItem, split.getTransaction().getDate(), true);
                    forecastTransaction.setRemainingAmount(0);
                    forecastTransaction.save(INSERT);

                    // Let the user know we created a dummy forecast transaction:
                    view.say("Created a new forecast transaction for this split as there was no applicable " +
                            "forecast transaction in the forecast.");

                    // Let the user know about the new forecast transaction we created for the split:
                    view.say("New " + forecastTransaction.toStringConcise());

                } else { // but if we were able to match the split to a forecast transaction,

                    // then deduct the amount of the split from that forecast transaction:
                    deductSplitAmount(forecastTransaction, split);
                }

                // Link the split to the forecast transaction for historical purposes:
                // First check if this split is already linked to a different forecast transaction
                ForecastTransactionSplit existingSplit = ForecastTransactionSplit.getForecastTransactionSplit(forecast, split);
                if (existingSplit != null) {
                    // Split is already reconciled to a different forecast transaction
                    // This can happen if the same transaction is processed twice or if there's a data issue
                    view.say("WARNING: Split " + split.toStringConcise() + " is already reconciled to " +
                            "forecast transaction " + existingSplit.getForecastTransaction().toStringConcise() +
                            ". Skipping duplicate reconciliation.");
                } else {
                    // Create and save the split link
                    forecastTransactionSplit = new ForecastTransactionSplit(forecastTransaction, split);
                    forecastTransactionSplit.save(INSERT);
                }

                // And finally, if the forecast item is an envelope type item:
                if (forecastTransaction.getForecastItem().getHowOccurs() == Item.HowOccurs.ENVELOPE) {

                    // then credit the amount to the envelope and zero out the remaining amount in the forecast
                    // transaction:
                    ForecastItem forecastItem = forecastTransaction.getForecastItem();
                    forecastItem.setRunningBalance(forecastItem.getRunningBalance() + split.getAmount());
                    forecastItem.save(UPDATE);
                    forecastTransaction.setRemainingAmount(0);
                    forecastTransaction.save(UPDATE);
                    view.say(Utility.formatDollarAmount(split.getAmount()) + " credited to " +
                            forecastTransaction.toStringVeryConcise());
                }


            } // End if it hasn't already been reconciled.
            else {
                // but if it has been reconciled, show a summary of the reconciliation record to the user:
                ForecastTransaction forecastTransaction =
                        ForecastTransaction.getById(forecastTransactionSplit.getIdForecastTransaction());
                view.say("Already reconciled.");
                view.say("Applicable " + forecastTransaction.toStringConcise());
            } // End if it has already been reconciled.
            prefix = "==========\n";
        } // End for each split assigned to this transaction.
    } // End ForecastTransaction.reconcile().


    /**
     * Calculate how far a transaction split overshoots what is left to spend (or collect) in the current period of a
     * collection type budget item.
     *
     * <p>Expense amounts are negative and income amounts are positive, so "exceeds the remaining amount" has to be
     * evaluated in the direction of the item.  An expense split overshoots when it is more negative than the
     * remaining amount (-1300.00 against -1242.00 overshoots by 58.00); an income split, e.g. rent collected in
     * instalments, overshoots when it is more positive (1245.00 against 1242.00 overshoots by 3.00).  Comparing
     * income the same way as expense makes every partial payment look like an overage, and zeroing out the rest of
     * the period then drops the item from the rendered forecast entirely.</p>
     *
     * @param isIncome         true if the budget item is an income item, false if it is an expense item.
     * @param remainingAmount  The amount left in the current period of the forecast transaction.
     * @param splitAmount      The amount of the split being reconciled against it.
     * @return The amount by which the split exceeds the remaining amount.  Positive means the user overspent (or
     * over-collected); zero or negative means the split fits within the period.
     */
    public static double calculateOverage(boolean isIncome, double remainingAmount, double splitAmount) {
        return isIncome ? splitAmount - remainingAmount : remainingAmount - splitAmount;
    }

    /**
     * Determine whether a transaction split exceeds what is left in the current period of a collection type budget
     * item.  Currency is compared with the {@code CURRENCY_COMPARISON_THRESHOLD} so a split that matches the
     * remaining amount to the cent is not treated as an overage.
     *
     * @param isIncome         true if the budget item is an income item, false if it is an expense item.
     * @param remainingAmount  The amount left in the current period of the forecast transaction.
     * @param splitAmount      The amount of the split being reconciled against it.
     * @return true if the split overshoots the remaining amount for the period.
     * @see #calculateOverage(boolean, double, double)
     */
    public static boolean exceedsRemainingAmount(boolean isIncome, double remainingAmount, double splitAmount) {
        return calculateOverage(isIncome, remainingAmount, splitAmount) > CURRENCY_COMPARISON_THRESHOLD;
    }


    /**
     * Deduct the split amount from collection type items because they ore a collection of smaller transactions that are
     * add up to the budgeted (and spent) amount.  In addition, any remaining amounts at the end of the period are
     * carried over to the next period so we must keep track of the remaining amount.
     * <p>
     * For periodic and unplanned type items, zero out the remaining amount because they occur as a single payment each
     * period and do not carry over to subsequent periods.  If they are over or under what was expected, that may have
     * triggered an adjustment to the item planned amount, or it may have been ignored, but that does not matter here.
     * <p>
     * Finally, for envelope type items, deduct from the budget item rather than the forecast item because that is where
     * envelope amounts are stored.  As far as the remaining amount in the forecast transaction, if today is on or after
     * the planned date, then credit the amount to the envelope and zero out the remaining amount in the forecast
     * transaction.
     *
     * @param split
     * @param forecastTransaction
     * @return The remaining amount in the forecast transaction after the split amount has been deducted.
     * @throws EntityException
     * @throws BudgetException
     * @throws Exception
     * @throws RegisterException
     */
    public double deductSplitAmount(ForecastTransaction forecastTransaction, TransactionSplit split)
            throws EntityException, BudgetException, Exception, RegisterException {

        Forecast forecast = forecastTransaction.getForecastItem().getForecast();
        Transaction transaction = split.getTransaction();

        // Deduct the actual amount from the planned amount:
        double remainingAmount = 0;
        switch (split.getBudgetItem().getHowOccurs()) {

            case COLLECTION:

                // The comparison below runs in the direction of the item because expense amounts are negative and
                // income amounts are positive:
                boolean isIncome = forecastTransaction.getForecastItem().isIncome();
                double remainingInPeriod = forecastTransaction.getRemainingAmount();

                // If the user has overspent (or over-collected) on this item, e.g. the amount of the split is greater
                // than the remaining amount in the current period of the budgeted amount per period for this budget
                // item:
                if (exceedsRemainingAmount(isIncome, remainingInPeriod, split.getAmount())) {

                    // Then ask the user what they would like to do:
                    view.say("You exceeded the remaining amount for this budget item by " +
                            Utility.formatDollarAmount(
                                    calculateOverage(isIncome, remainingInPeriod, split.getAmount())) + ".  ");
                    ForecastTransactionIterator it = ForecastTransaction.getNonZeroForecastTransactionsForBudgetItem(
                            split.getIdBudgetItem(), forecast.getId());
                    ForecastTransaction nextNonZeroForecastTransaction = it.getNext();
                    if (nextNonZeroForecastTransaction != null) {
                        view.say("Next non-zero " + nextNonZeroForecastTransaction.toStringConcise());
                    }
                    split.setDisposition(assignOverageAmount(""));

                    // And Execute the user's request:
                    switch (split.getDisposition()) {

                        case ADJUST:  // The user would like to increase the budgeted amount to cover the overage:
                            split.getBudgetItem().setAmount(split.getAmount());
                            split.getBudgetItem().save(INSERT_ON_DUPLICATE_UPDATE);
                            forecast.setInSync(false);
                            forecast.save(UPDATE);
                            forecastTransaction.setRemainingAmount(split.getAmount());
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
                            view.say(Utility.formatDollarAmount(split.getAmount()) + " assigned to, but not " +
                                    ((split.getAmount() < 0) ? "deducted from " : " added to ") +
                                    forecastTransaction.toStringVeryConcise());
                            break;

                        case ROLL_FORWARD:
                            boolean done = false;
                            remainingAmount = split.getAmount();
                            while (!done) {
                                if (nextNonZeroForecastTransaction != null) {
                                    // If there is enough money in the forecast transaction to cover the split amount:
                                    if (nextNonZeroForecastTransaction.getRemainingAmount() <= remainingAmount) {

                                        // then deduct the split amount from the forecast transaction amount:
                                        nextNonZeroForecastTransaction.setRemainingAmount(
                                                nextNonZeroForecastTransaction.getRemainingAmount() - remainingAmount);

                                        // and save the new remaining amount in the forecast transactions:
                                        nextNonZeroForecastTransaction.save(UPDATE);
                                        view.say(Utility.formatDollarAmount(remainingAmount) +
                                                ((remainingAmount < 0) ? " deducted from " : " added to ") +
                                                nextNonZeroForecastTransaction.toStringVeryConcise());

                                        // and we are done.
                                        done = true;

                                    } else { // but if there isn't enough money to cover:

                                        // then figure out how much to carry over to the next forecast transaction:
                                        remainingAmount -= nextNonZeroForecastTransaction.getRemainingAmount();

                                        // then let the user know what we are doing:
                                        double amount =
                                                (currencyDifference(nextNonZeroForecastTransaction.getRemainingAmount(),
                                                        remainingAmount) >= 0) ?
                                                        remainingAmount : nextNonZeroForecastTransaction.getRemainingAmount();

                                        // and zero out and save off the current forecast transaction:
                                        view.say(Utility.formatDollarAmount(amount) +
                                                ((amount < 0) ? " deducted from " : " added to ") +
                                                nextNonZeroForecastTransaction.toStringVeryConcise());
                                        nextNonZeroForecastTransaction.setRemainingAmount(0);
                                        nextNonZeroForecastTransaction.save(UPDATE);

                                        // and move to the next non-zero forecast transaction
                                        nextNonZeroForecastTransaction = it.getNext();
                                        if (nextNonZeroForecastTransaction != null) {
                                            view.say("Carry over " + formatDollarAmount(remainingAmount) + " to " +
                                                    nextNonZeroForecastTransaction.toStringVeryConcise());
                                        } else {
                                            view.say("Unable to roll forward " + formatDollarAmount(remainingAmount) +
                                                    " because there are no more forecast transactions to roll foward to.");
                                            done = true;
                                        }
                                    }

                                } else {
                                    view.say("Unable to roll forward " + formatDollarAmount(remainingAmount) +
                                            " because there are no more forecast transactions to roll foward to.");
                                    done = true;
                                }
                            }
                            break;
                    }
                } else { // But if the user did not overspend on this item, e.g. the amount of the split is less than the
                    // remaining amount in the current period:

                    // then if this might be an overspend reimbursement to an expense category that has already been
                    // zeroed out:
                    boolean deduct = true;
                    if (split.getAmount() > CURRENCY_COMPARISON_THRESHOLD &&
                            forecastTransaction.getForecastItem().getItemType() != INCOME) {

                        // then check with the user to see if they want to credit the amount to the remaining amount
                        // of the forecast item:
                        resolver.say("Appicable:  " + forecastTransaction.toStringConcise());
                        deduct = resolver.getYesOrNo("Do you want to credit the amount of this split to the " +
                                "forecast transaction");
                    }

                    // then deduct the split amount from the remaining amount:
                    if (deduct) {
                        forecastTransaction.setRemainingAmount(forecastTransaction.getRemainingAmount() - split.getAmount());

                        // and save the new remaining amount in the forecast transactions:
                        forecastTransaction.save(UPDATE);

                        // and notify the user what we did:
                        view.say(Utility.formatDollarAmount(split.getAmount()) +
                                ((split.getAmount() < 0) ? " deducted from " : " added to ") +
                                forecastTransaction.toStringVeryConcise());
                    }
                }
                break;

            case ENVELOPE:
            case PERIODIC:
            case VARIABLE_PERIODIC:
            case UNPLANNED:
                forecastTransaction.setRemainingAmount(0);
                forecastTransaction.save(UPDATE);
                view.say(Utility.formatDollarAmount(split.getAmount()) +
                        ((split.getAmount() < 0) ? " deducted from " : " added to ") +
                        forecastTransaction.toStringVeryConcise());
                break;
        }

        return forecastTransaction.getRemainingAmount();
    }

    /**
     * Check if the external forecast file (Excel, CSV, TSV) has been modified since the last render.
     * @return true if the external file exists and is newer than lastRenderedDate, false otherwise
     */
    public boolean isExternalForecastFileNewer() {
        try {
            // Construct the base directory and filename from the forecast name:
            String forecastName = forecast.getName().replace(" ", "");  // Remove spaces from forecast name
            String baseDirectory = "C:\\Users\\dwhix\\OneDrive\\Shared Data\\Hixon Family Personal Business\\Finances\\Expenses";
            String baseFilename = "LongTermForecast-" + forecastName;

            // Check for Excel files first (.xlsx, .xls), then CSV/TSV
            String[] extensions = {".xlsx", ".xls", ".csv", ".tsv"};
            for (String ext : extensions) {
                File sourceFile = new File(baseDirectory + "\\" + baseFilename + ext);
                if (sourceFile.exists()) {
                    // Get the file's last modified time
                    long fileModifiedTime = sourceFile.lastModified();

                    // If we've never rendered this forecast, we can't compare
                    if (forecast.getLastRenderedDate() == null) {
                        return false;  // Don't prompt if we don't know when it was last rendered
                    }

                    // Get the last rendered time
                    long lastRenderedTime = forecast.getLastRenderedDate().getTimeInMillis();

                    // Truncate both times to seconds (remove millisecond precision)
                    // This is necessary because the database stores timestamps with second precision,
                    // but file modification times have millisecond precision
                    long fileModifiedTimeSeconds = (fileModifiedTime / 1000) * 1000;
                    long lastRenderedTimeSeconds = (lastRenderedTime / 1000) * 1000;

                    // Add a 60-second tolerance to avoid false positives from OneDrive sync
                    // or Excel AutoSave touching the file timestamp after the app writes it.
                    // OneDrive can take several seconds (or longer) to sync a newly written file
                    // to the cloud, and during this process it may update the file's lastModified
                    // timestamp. A 60-second window accounts for typical sync delays.
                    long differenceInSeconds = (fileModifiedTimeSeconds - lastRenderedTimeSeconds) / 1000;

                    // Only consider the file modified if it's more than 60 seconds newer
                    return differenceInSeconds > 60;
                }
            }

            // No external file found
            return false;

        } catch (Exception e) {
            view.say("Warning: Could not check external file modification time: " + e.getMessage());
            return false;
        }
    }

    /*
     * Update the forecast from a list of forecast transactions from some external source:
     */
    public void updateFromExternalSource()
            throws Exception {

        // Keep a count of the forecast transactions from the external source for debugging purposes:
        int i = 0;

        try {
            // We will need a BudgetController:
            BudgetController budgetController = createBudgetController();

            // Construct the base directory and filename from the forecast name:
            String forecastName = forecast.getName().replace(" ", "");  // Remove spaces from forecast name
            String baseDirectory = "C:\\Users\\dwhix\\OneDrive\\Shared Data\\Hixon Family Personal Business\\Finances\\Expenses";
            String baseFilename = "LongTermForecast-" + forecastName;

            // Try to find the source file, checking for Excel files first, then CSV/TSV
            String sourceName = null;
            AbstractForecastView externalSourceView = null;

            // Check for Excel files first (.xlsx, .xls)
            String[] excelExtensions = {".xlsx", ".xls"};
            for (String ext : excelExtensions) {
                String filePath = baseDirectory + "\\" + baseFilename + ext;
                if (fileExists(filePath)) {
                    sourceName = filePath;
                    externalSourceView = createExcelForecastView(forecast);
                    view.say("Found Excel forecast file: " + sourceName);
                    break;
                }
            }

            // If no Excel file found, check for CSV/TSV
            if (sourceName == null) {
                String[] csvExtensions = {".csv", ".tsv"};
                for (String ext : csvExtensions) {
                    String filePath = baseDirectory + "\\" + baseFilename + ext;
                    if (fileExists(filePath)) {
                        sourceName = filePath;
                        externalSourceView = createCsvForecastView(forecast);
                        view.say("Found CSV/TSV forecast file: " + sourceName);
                        break;
                    }
                }
            }

            // If still no file found, throw an exception
            if (sourceName == null) {
                throw new ForecastException("No forecast file found. Looked for:\n" +
                        "  - " + baseDirectory + "\\" + baseFilename + ".xlsx\n" +
                        "  - " + baseDirectory + "\\" + baseFilename + ".xls\n" +
                        "  - " + baseDirectory + "\\" + baseFilename + ".csv\n" +
                        "  - " + baseDirectory + "\\" + baseFilename + ".tsv");
            }

            // Open the external source and get a list of forecast transactions in it:
            List<ForecastTransaction> forecastTransactions = externalSourceView.openForecastTransactionSource(sourceName);

            // If we were able to open the external source:
            if (forecastTransactions != null) {

                // Mark all the forecast transactions in THIS forecast as not found:
                setAllFound(forecast, false);

                // For each forecast transaction from the external source:
                for (ForecastTransaction ssForecastTransaction : forecastTransactions) {

                    // Keep track of the list item number for debugging purposes:
                    i++;

                    // If the current spreadsheet forecast transaction has an ID (the update case):
                    if (ssForecastTransaction.getId() != null) {

                        // then get the matching forecast transaction from the database:
                        ForecastTransaction dbForecastTransaction =
                                lookupForecastTransactionById(ssForecastTransaction.getId());

                        // and if a matching forecast transaction was found in the database:
                        if (dbForecastTransaction != null) {

                            // then mark the transaction as found:
                            dbForecastTransaction.setFound(true);

                            // and since the spreadsheet does not contain the budgeted amount we can add that now:
                            ssForecastTransaction.getForecastItem().setAmount(
                                    dbForecastTransaction.getForecastItem().getAmount()
                            );

                            // then if the forecast planned date has been modified, then update the database transaction:
                            boolean overwrite;
                            if (Utility.dateOnlyCompare(ssForecastTransaction.getPlannedDate(),
                                    dbForecastTransaction.getPlannedDate()) != 0) {
                                // If the database forecast transaction was updated after it was sent to the external
                                // source:
                                overwrite = true;
                                if (Utility.dateOnlyCompare(ssForecastTransaction.getVersion(),
                                        dbForecastTransaction.getVersion()) < 0) {

                                    // Then ask the user if they want to overwrite the updated database value:
                                    view.say("\nThe date of an imported forecast transaction has " +
                                            "changed, but the version of the imported forecast transaction is prior to" +
                                            " the version in the database.");
                                    view.say("Imported " + ssForecastTransaction.toStringConcise());
                                    view.say("Database " + dbForecastTransaction.toStringConcise());
                                    if (view.selectFromMenu(
                                            "Which date do you want to use?",
                                            List.of("imported", "database"),
                                            DO_NOT_ALLOW_NONE, SHOW_CANCEL_QUIT_SKIP, DO_NOT_ALLOW_CANCEL,
                                            DO_NOT_ALLOW_QUIT, DO_NOT_ALLOW_SKIP)
                                            .equalsIgnoreCase("d")) {
                                        overwrite = false;
                                    }
                                }

                                // Overwrite the date in the database forecast transaction if appropriate:
                                if (overwrite) {
                                    view.say("Date modified for " +
                                            dbForecastTransaction.toStringConcise());
                                    view.say("New date is:  " +
                                            Utility.calendarDateToStringDate(ssForecastTransaction.getPlannedDate()));
                                    dbForecastTransaction.setPlannedDate(ssForecastTransaction.getPlannedDate());
                                    // Set the "override" flag on the forecast transaction to prevent it from
                                    // being deleted during the forecast update process:
                                    dbForecastTransaction.setOverridden(true);
                                }
                            }

                            // and if the remaining amount has been modified and the difference is not just rounding
                            // to the nearest dollar for display purposes::
                            if (Math.abs(ssForecastTransaction.getRemainingAmount() -
                                    dbForecastTransaction.getRemainingAmount()) > 0.50) {

                                overwrite = true;
                                if (Utility.dateOnlyCompare(ssForecastTransaction.getVersion(),
                                        dbForecastTransaction.getVersion()) < 0) {
                                    // Then ask the user if they want to overwrite the updated database value:
                                    view.say("\nThe amount of an imported forecast transaction has " +
                                            "changed, but the version of the imported forecast transaction is prior to" +
                                            " the version in the database.");
                                    view.say("Imported " + ssForecastTransaction.toStringConcise());
                                    view.say("Database " + dbForecastTransaction.toStringConcise());
                                    if (view.selectFromMenu(
                                            "Which amount do you want to use?",
                                            List.of("imported", "database"),
                                            DO_NOT_ALLOW_NONE, SHOW_CANCEL_QUIT_SKIP, DO_NOT_ALLOW_CANCEL,
                                            DO_NOT_ALLOW_QUIT, DO_NOT_ALLOW_SKIP
                                        ).equalsIgnoreCase("d")) {
                                        overwrite = false;
                                    }
                                }

                                // Overwrite the amount in the database forecast transaction if appropriate:
                                if (overwrite) {

                                    // then update the database transaction:
                                    view.say("Amount modified for " +
                                            dbForecastTransaction.toStringConcise());
                                    view.say("New amount is:  " +
                                            Utility.formatDollarAmount(ssForecastTransaction.getRemainingAmount()));
                                    dbForecastTransaction.setRemainingAmount(ssForecastTransaction.getRemainingAmount());
                                    // Set the "override" flag on the forecast transaction to prevent it from
                                    // being deleted during the forecast update process:
                                    dbForecastTransaction.setOverridden(true);
                                }
                            }

                            // and save the updated forecast transaction to the database:
                            updateForecastTransaction(dbForecastTransaction);

                        } else {
                            // No matching transaction was found in the database.
                            // This can happen when a user manually adds a row in the spreadsheet
                            // and Excel generates an ID (or the user copies a row with an existing ID).
                            // Treat this as a NEW forecast transaction creation rather than an error.
                            view.say("Forecast transaction has an ID but was not found in database - treating as new creation.");
                            view.say("  ID: " + ssForecastTransaction.getId());
                            view.say("  " + ssForecastTransaction.toStringConcise());

                            // Clear the ID so it will be treated as a new transaction
                            ssForecastTransaction.setId(null);

                            // Fall through to the creation logic below by NOT continuing
                            // This will allow the code at line 851+ to handle it as a new transaction
                        }
                    }

                    // Handle creation of new forecast transactions (ID is null or was cleared above)
                    if (ssForecastTransaction.getId() == null) {

                        // If there isn't already an instance of the forecast item for this forecast transaction in the forecast:
                        ForecastItem forecastItem = lookupForecastItemByName(forecast.getId(),
                                ssForecastTransaction.getForecastItem().getCategory(), ssForecastTransaction.getForecastItem().getPayee());
                        if (forecastItem == null) {

                            // then create a forecast item, so we have something to link the forecast transaction to:
                            // Get a list of budget items that match the entered payee:
                            List<BudgetItem> budgetItemsForPayee = lookupBudgetItemsByPayee(
                                    forecast.getBudget(),
                                    ssForecastTransaction.getForecastItem().getPayee());
                            BudgetItem budgetItem;
                            if (budgetItemsForPayee.size() > 1) {
                                budgetItem = budgetController.getUserSelectedBudgetItem(budgetItemsForPayee);
                            } else if (budgetItemsForPayee.size() == 1) {
                                budgetItem = budgetItemsForPayee.get(0);
                            } else {
                                budgetItem = null;
                            }

                            // TODO:  Handle if the budget item isn't found.
                            // If the budget item isn't found:

                            // then get the budget item from the user (adding a new one if required):

                            ssForecastTransaction.getForecastItem().setIdBudgetItem(budgetItem.getId());
                            insertForecastItem(ssForecastTransaction.getForecastItem());
                        } else {
                            ssForecastTransaction.setForecastItem(forecastItem);
                        }

                        // Fill out the forecast transaction:
                        ssForecastTransaction.setId(UUID.randomUUID());
                        ssForecastTransaction.setFound(true);
                        ssForecastTransaction.setOverridden(true);
                        insertForecastTransaction(ssForecastTransaction);

                        // Let the user know what we did:
                        view.say("The following forecast transaction was not in the forecast so it has been " +
                                "added to the forecast:  \n" + ssForecastTransaction.toStringConcise());

                    } // End if forecast transaction ID is null (new creation)
                } // End for each forecast transaction in the external source.

                // Set all the forecast transactions deleted from the spreadsheet to zero because the user zeroed them
                // out in the spreadsheet:
                ForecastTransactionController forecastTransactionController =
                        createForecastTransactionController();
                forecastTransactionController.zeroNotFound(forecast);

                // Close the external source of forecast transactions:
                externalSourceView.closeForecastTransactionSource(sourceName);

            } // End if we were able to open the external source.

            // TODO: Save the import event:

        } catch (BudgetException | RegisterException e) {
            ForecastException fe = new ForecastException("Error reading from the external source " +
                    "on forecast transaction " + i + ".");
            fe.initCause(e);
            throw (fe);
        }

        // Return the number of transactions updated:
        if (i > 0) {
            view.say("Successfully processed " + i + " forecast transactions from the external source.");
        } else {
            view.say("There are no forecast transactions in the external source to update from.");
        }

    } // End updateFromExternalSource(Connection dbConnection).

    /*
     * Protected factory/lookup methods used by updateFromExternalSource.
     * These methods exist to provide testability seams — they can be overridden in test subclasses
     * to supply mock objects without requiring static mocking of JDK classes or complex model classes.
     */

    /** Check if a file at the given path exists on disk. */
    protected boolean fileExists(String filePath) {
        return new File(filePath).exists();
    }

    /** Create an ExcelForecastView for reading forecast transactions from an Excel file. */
    protected AbstractForecastView createExcelForecastView(Forecast forecast) throws EntityException, SQLException {
        return new ExcelForecastView(forecast);
    }

    /** Create a CsvForecastView for reading forecast transactions from a CSV/TSV file. */
    protected AbstractForecastView createCsvForecastView(Forecast forecast) throws EntityException, SQLException {
        return new CsvForecastView(forecast);
    }

    /** Create a BudgetController for user interaction during import. */
    protected BudgetController createBudgetController() {
        return new BudgetController(sessionController);
    }

    /** Create a ForecastTransactionController for post-processing. */
    protected ForecastTransactionController createForecastTransactionController() {
        return new ForecastTransactionController(sessionController);
    }

    /** Look up a ForecastTransaction by its ID in the database. */
    protected ForecastTransaction lookupForecastTransactionById(UUID id)
            throws ForecastException, EntityException, SQLException {
        return ForecastTransaction.getById(id);
    }

    /** Mark all forecast transactions in the given forecast as found or not found. */
    protected void setAllFound(Forecast forecast, boolean found) throws EntityException, RegisterException {
        ForecastTransaction.setAllFound(forecast, found);
    }

    /** Look up a ForecastItem by name (category + payee) in the given forecast. */
    protected ForecastItem lookupForecastItemByName(UUID idForecast, String category, String payee)
            throws EntityException, BudgetException, SQLException, ForecastException {
        return ForecastItem.getByName(idForecast, category, payee);
    }

    /** Look up unexpired BudgetItems matching the given payee. */
    protected List<BudgetItem> lookupBudgetItemsByPayee(Budget budget, String payee) throws BudgetException {
        return BudgetItem.getUnexpiredByPayee(budget, payee);
    }

    /** Persist a forecast transaction update to the database. */
    protected void updateForecastTransaction(ForecastTransaction ft)
            throws EntityException, BudgetException, SQLException, RegisterException {
        ft.update();
    }

    /** Persist a new forecast transaction to the database. */
    protected void insertForecastTransaction(ForecastTransaction ft)
            throws ForecastException, BudgetException, EntityException, RegisterException, SQLException {
        ft.insert();
    }

    /** Persist a new forecast item to the database. */
    protected void insertForecastItem(ForecastItem fi)
            throws ForecastException, BudgetException, EntityException, RegisterException, SQLException {
        fi.insert();
    }

    /**
     * Updates the long-term forecast. This means to regenerate the portion of the forecast from the update start date
     * (usually the first day of the next month) to the end of the forecast window. The end of the forecast window
     * defaults to 12 months, which likely results in extending the forecast.
     *
     * @throws Exception if there are any errors during the update process.
     */
    /**
     * Update the forecast with user interaction to select the start date.
     * This method prompts the user for the start date and confirmation.
     *
     * @throws Exception if an error occurs during the update
     */
    public void updateForecast() throws Exception {

        // Get the starting date of the forecast to update.
        Calendar updateStartDate = null;
        boolean done = false;
        while (!done) {
            view.say("Updating the forecast. WARNING: Normally this should begin with the first of next month.");
            updateStartDate = askStartDate();
            Calendar nextMonth = Calendar.getInstance();
            nextMonth.add(MONTH, 1);
            if (updateStartDate.get(MONTH) != nextMonth.get(MONTH) || updateStartDate.get(Calendar.DATE) != 1) {
                done = view.getYesOrNo("You did not select the first of next month. Are you sure?");
            } else {
                done = true;
            }
        }

        // Call the non-interactive version with the user-selected start date
        updateForecast(updateStartDate);
    }

    /**
     * Update the forecast without user interaction using the specified start date.
     * This method is useful for automated operations like cleanup after deleting budget items.
     *
     * @param updateStartDate the date to start the forecast update from
     * @throws Exception if an error occurs during the update
     */
    public void updateForecast(Calendar updateStartDate) throws Exception {

        // Update the forecast start date:
        forecast.setStartDate(updateStartDate);

        // Ensure numberOfMonths is set to a reasonable value (handle old forecasts that may have 0)
        if (forecast.getNumberOfMonths() <= 0) {
            forecast.setNumberOfMonths(12);  // Default to 12 months (1 year)
        }

        // Update up the end date so that the forecast window will be the same number of months as it was originally
        // set to be.
        Calendar endDate = (Calendar) updateStartDate.clone();
        endDate.add(MONTH, forecast.getNumberOfMonths());
        forecast.setEndDate(endDate);

        // Update all the forecast items in the forecast from the current budget items.
        ForecastItem.updateForecastItemsFromBudgetItems(forecast);

        // Get a list of budget items that weren't included in the forecast because they didn't exist when the forecast
        // was created.
        String query = BudgetItem.getSelectQuery() + " " +
                "LEFT JOIN forecast_item fi ON bi.idBudgetItem = fi.BudgetItem_idBudgetItem " +
                "AND fi.Forecast_idForecast = uuid_to_bin('" + forecast.getId() + "') " +
                "WHERE bi.Budget_idBudget = uuid_to_bin('" + budget.getId() + "') " +
                "and fi.idForecastItem is null";
        ResultSet rs = getRS(query, "retrieving the budget items not included in the forecast");

        // Create forecast items for budget items that weren't previously included.
        ForecastItem forecastItem;
        while (rs.next()) {
            forecastItem = new ForecastItem(forecast, new BudgetItem(rs));
            forecastItem.save(INSERT);
        }

        // Expire any forecast items generated from budget items that no longer exist so they no longer generate new
        // forecast transactions.
        ForecastItem.expireOldForecastItems(forecast);

        // Expire forecast items whose corresponding budget items are expired
        // This handles cases where a budget item was moved to another budget and expired
        String expireForecastItemsQuery = "UPDATE forecast_item fi " +
                "INNER JOIN budget_item bi ON fi.BudgetItem_idBudgetItem = bi.idBudgetItem " +
                "SET fi.endDate = bi.endDate " +
                "WHERE fi.Forecast_idForecast = uuid_to_bin('" + forecast.getId() + "') " +
                "AND bi.endDate IS NOT NULL " +
                "AND (fi.endDate IS NULL OR fi.endDate > bi.endDate)";
        executeUpdate(expireForecastItemsQuery, "expiring forecast items with expired budget items");

        // Delete any expired forecast items that have no linked forecast transactions.
        ForecastItem.deleteExpiredUnusedForecastItems(forecast);

        // Delete all the forecast transactions that occur after the update start date, except for:
        // - Overridden ones (user manually modified)
        // - Reconciled ones (have splits assigned, which indicates reconciliation data exists)
        // Note: The 'found' flag is NOT used here - it's only for the "Update from External Source" process
        String deleteQuery = ForecastTransaction.getDeleteQuery() +
                "where " +
                    "ForecastItem_idForecastItem in (" +
                        "select " +
                            "idForecastItem " +
                        "from " +
                            "forecast_item " +
                        "where " +
                            "Forecast_idForecast = uuid_to_bin('" + forecast.getId() + "')" +
                    ") " +
                    "and plannedDate >= " + Utility.calendarDateToSqlDateString(updateStartDate) + " " +
                    "and not overridden " +
                    "and not exists (" +
                        "select 1 " +
                        "from " +
                            "forecast_transaction_split " +
                        "where " +
                            "forecast_transaction_split.ForecastTransaction_idForecastTransaction = " +
                                "forecast_transaction.idForecastTransaction" +
                    ")";
        executeUpdate(deleteQuery, "deleting all the forecast transactions after " +
                Utility.calendarDateToStringDate(updateStartDate));

        // Delete forecast transactions that are past their forecast item's expiration date
        // This handles cases where a budget item was expired but old forecast transactions still exist
        String deleteExpiredQuery = "DELETE ft FROM forecast_transaction ft " +
                "INNER JOIN forecast_item fi ON ft.ForecastItem_idForecastItem = fi.idForecastItem " +
                "WHERE fi.Forecast_idForecast = uuid_to_bin('" + forecast.getId() + "') " +
                "AND fi.endDate IS NOT NULL " +
                "AND ft.plannedDate > fi.endDate " +
                "AND NOT ft.overridden " +
                "AND NOT EXISTS (" +
                    "SELECT 1 FROM forecast_transaction_split fts " +
                    "WHERE fts.ForecastTransaction_idForecastTransaction = ft.idForecastTransaction" +
                ")";
        executeUpdate(deleteExpiredQuery, "deleting forecast transactions past their item's expiration date");

        // Generate the updated portion of the forecast starting on the update start date.
        forecast.setTransactions(new ForecastTransaction[forecast.getNumberOfMonths() * 31]);
        ForecastEngine forecastEngine = new ForecastEngine();
        forecastEngine.generateForecastTransactions(forecast, updateStartDate);

        // Save the updated portion of the forecast.
        forecast.saveForecastTransactions();

        // Check for duplicate forecast transactions and warn the user
        forecast.checkForDuplicateTransactions();

        // The forecast engine doesn't know that we are updating a forecast. It will have set the first occurrence
        // properly for a new forecast. Fix up the flags in the updated forecast.
        ForecastTransaction.cleanUpForecast(forecast);

        // Calculate and save running balances for all forecast transactions:
        calculateAndSaveRunningBalances(forecast);

        // Mark the forecast as in sync.
        forecast.setInSync(true);

        // Get a chronological list of all the non-zero forecast transactions in the forecast:
        ForecastTransactionIterator forecastTransactions = ForecastTransaction.getNonZeroForecastTransactions(forecast);

        // Get the first forecast transaction in the list:
        ForecastTransaction firstForecastTransaction = forecastTransactions.getNext();

        // If there are no forecast transactions, we can't update the forecast dates
        if (firstForecastTransaction == null) {
            view.say("No forecast transactions found in the forecast. Forecast dates not updated.");
            return;
        }

        // Reset the forecast start date to the date of the first non-zero forecast transaction:
        forecast.setStartDate(firstForecastTransaction.getPlannedDate());

        // Update the forecast object in the database with the new start and end dates.
        forecast.save();

    }
}
