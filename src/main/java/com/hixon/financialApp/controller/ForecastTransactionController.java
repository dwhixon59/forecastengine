package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.TransactionSplit;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.model.forecast.ForecastTransactionIterator;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.base.UserResponse;
import com.hixon.financialApp.view.base.ViewInt;

import java.sql.ResultSet;
import java.sql.SQLException;

import static com.hixon.financialApp.model.entity.EntityInt.SaveMethod.INSERT_ON_DUPLICATE_UPDATE;
import static com.hixon.financialApp.model.entity.EntityInt.SaveMethod.UPDATE;
import static com.hixon.financialApp.model.entity.EntityInt.executeUpdate;
import static com.hixon.financialApp.model.forecast.ForecastTransactionSplit.SplitDisposition.ASSIGN;
import static com.hixon.financialApp.model.forecast.ForecastTransactionSplit.SplitDisposition.IGNORE;
import static com.hixon.financialApp.utility.Utility.getView;

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
