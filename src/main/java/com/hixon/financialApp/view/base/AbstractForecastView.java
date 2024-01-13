package com.hixon.financialApp.view.base;

import com.hixon.financialApp.controller.ControllerException;
import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.entity.Entity;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.forecast.*;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.model.user.UserResource;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.ViewException;
import com.hixon.financialApp.view.text.OverdueItemsReport;
import com.hixon.financialApp.view.text.TrackingItemsOfInterestReport;
import com.hixon.financialApp.view.text.UpcomingItemsOfInterestReport;
import com.hixon.financialApp.view.text.UpcomingItemsReport;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.*;

import static com.hixon.financialApp.model.forecast.Forecast.SignificantEvents.daysBelowMinimumBalance;
import static com.hixon.financialApp.utility.Utility.*;

/**
 * The AbstractForecastView class implements the {@link ForecastViewInt}, provides default implementations of its
 * methods where appropriate, and contains the logic common to the different methods of user interaction with the
 * forecast model.  For example all the code for interacting with the forecast database, for the purpose of rendering
 * a forecast for the user, is here.  Code specific to a particular type of rendering of the forecast (spreadsheet, html,
 * etc.) is contained in the concrete ForecastView classes in the packages associated with the particular type of view
 * (text, JSON, XML, spreadsheet, HTML, etc.).
 */
public abstract class AbstractForecastView extends AbstractView implements ForecastViewInt {

    /*
     * Fields:
     */
    protected Forecast forecast;


    /*
     * Getters and setters:
     */
    public Forecast getForecast() {
        return forecast;
    }

    public void setForecast(Forecast forecast) {
        this.forecast = forecast;
    }


    /*
     * Constructors:
     */

    public AbstractForecastView(Forecast forecast) {
        this.forecast = forecast;
    }

    /*
     * Helper methods:
     */
    protected abstract void openLongTermForecastOutput() throws FileNotFoundException, UnsupportedEncodingException;

    protected abstract void renderLongTermForecastFrontMatter();

    protected abstract void renderMonthHeader(Calendar plannedDate, double runningBalance);

    protected abstract void renderForecastTransaction(ForecastTransaction forecastTransaction, double credit, double debit)
            throws EntityException,
            SQLException, ForecastException, BudgetException;

    protected abstract void renderLongTermForecastBackMatter();

    protected abstract void closeLongTermForecastOutput();

    protected abstract void closeForecastTransactionSource() throws ViewException;

    protected abstract List<ForecastTransaction> openForecastTransactionSource() throws IOException, ControllerException, BudgetException;

    protected abstract TrackingItemsOfInterestReport getTrackingItemsOfInterestReport(User user, List<Entity> items,
                                                                                      File reportFile) throws FileNotFoundException;

    protected abstract UpcomingItemsOfInterestReport getUpcomingItemsOfInterestReport(User user, List<Entity> items,
                                                                                      File reportFile) throws FileNotFoundException;

    protected abstract OverdueItemsReport getOverdueItemsReport(Forecast forecast, List<Entity> items, File reportFile) throws FileNotFoundException;

    protected abstract UpcomingItemsReport getUpcomingItemsReport(Forecast forecast, List<Entity> items, File reportFile) throws FileNotFoundException;


    /*
     * Main methods:
     */
    @Override
    public boolean renderShortTermForecast(Forecast forecast) throws Exception, EntityException, BudgetException {

        this.forecast = forecast;

        getResolver().say("\n\nRender the short term forecast.");

        // To clue the user into what things to look for in the spreadsheet, run the forecast summary routine
        // requesting below minimum balance events:
        Forecast.SignificantEvents[] events = {daysBelowMinimumBalance};
        forecast.summarize();

        // Print out the starting and ending balances:
        getResolver().say("The starting balance is: " + Utility.formatDollarAmount(forecast.getStartingBalance()));
        getResolver().say("The ending balance is:   " + Utility.formatDollarAmount(forecast.getEndingBalance()));
        getResolver().say("The savings rate is:   " + Utility.formatDollarAmount(forecast.getEndingBalance() /
                forecast.getNumberOfMonths()) + " per month.");

        // TODO:  Render the short term forecast (whatever that means . . . .).
        System.out.println("The short term forecast was successfully rendered.");

        // and print out the significant events list:
        ForecastTransaction forecastTransaction = forecast.getFirstSignificantEvent();
        while (forecastTransaction != null) {
            getResolver().say("The balance on " + Utility.calendarDateToStringDate(forecastTransaction.getPlannedDate()) +
                    " is $" + forecastTransaction.getRunningBalance());
            if (forecastTransaction.getRunningBalance() < forecast.getMinimumBalance()) {
                getResolver().say("Balance below minimum balance!");
            }
            forecastTransaction = forecastTransaction.getNextSignificantEvent();
        }
        return true;
    }


    @Override
    public boolean renderLongTermForecast(Forecast forecast) throws Exception, EntityException, BudgetException,
            RegisterException {

        this.forecast = forecast;

        // Get the first day of the forecast rendering:
        Calendar startDate = forecast.getStartDate();

        // Get the starting balance.  Take if from the first register associated with the budget for now:
        List<Register> registers = forecast.getBudget().getRegisters();
        double startingBalance = registers.get(0).getBalance();
        double runningBalance = startingBalance;

        // Variables to save significant events over the period of the forecast and the date on which they occurred:
        double lowestBalance = startingBalance;
        Calendar dateOfLowestBalance = null;
        double highestBalance = startingBalance;
        Calendar dateOfHighestBalance = null;
        double firstNegativeBalance = 0.0;
        Calendar dateOfFirstNegativBalance = null;
        double totalIncome = 0.0;
        double totalExpense = 0.0;
        double totalSavings = 0.0;
        double totalDebtExpense = 0.0;

        // Variables to hold the date  of the first first-of-the-month and balance on that date.  This is used to
        // calculate whether the forecast is solvent over the period of the forecast, and also the required amount of
        // float to keep the forecast solvent.
        Calendar firstFirstOfMonth = getNextFirstOfMonth(Calendar.getInstance());
        double firstFirstOfMonthBalance = 0.0;

        // Open and initialize the forecast rendering output file:
        openLongTermForecastOutput();
        renderLongTermForecastFrontMatter();

        // Set all the running balances to zero in the database:
        ForecastTransaction.zeroRunningBalances();

        // Iterate over all the forecast transactions in chronological order beginning on the start date:
        ForecastTransactionIterator forecastTransactions =
                ForecastTransaction.getForecastTransactionsStartingOn(forecast, startDate);
        ForecastTransaction forecastTransaction = forecastTransactions.getNext();
        ForecastTransaction firstForecastTransaction = forecastTransaction;
        ForecastTransaction lastForecastTransaction = null;
        int currentMonth = -1;
        boolean firstTime = true;
        while (forecastTransaction != null) {

            // If the month changed, write out a header line with the name of the month:
            if (forecastTransaction.getPlannedDate().get(Calendar.MONTH) != currentMonth) {
                renderMonthHeader(forecastTransaction.getPlannedDate(), runningBalance);
                currentMonth = forecastTransaction.getPlannedDate().get(Calendar.MONTH);
                if (dateOnlyCompare(firstFirstOfMonth, forecastTransaction.getPlannedDate()) == 0) {
                    firstFirstOfMonthBalance = runningBalance;
                    lowestBalance = runningBalance;
                    dateOfLowestBalance = firstFirstOfMonth;
                }
            }

            // Update the running balance:
            runningBalance += forecastTransaction.getRemainingAmount();
            forecastTransaction.setRunningBalance(runningBalance);

            // Record the first negative balance and the date on which it occurred:
            if (runningBalance < 0 && firstTime) {
                firstTime = false;
                firstNegativeBalance = runningBalance;
                dateOfFirstNegativBalance = forecastTransaction.getPlannedDate();
            }

            // If we are within the forecast summary period (from the firstFirstOfMonth data to the end of the forecast),
            // then update the summary:
            if (dateOnlyCompare(forecastTransaction.getPlannedDate(), firstFirstOfMonth) >= 0) {
                if (runningBalance < lowestBalance) {
                    lowestBalance = runningBalance;
                    dateOfLowestBalance = forecastTransaction.getPlannedDate();
                }

                if (runningBalance > highestBalance) {
                    highestBalance = runningBalance;
                    dateOfHighestBalance = forecastTransaction.getPlannedDate();
                }

                // Record the total income and the date on which it occurred:
                if (forecastTransaction.getRemainingAmount() > 0) {
                    totalIncome += forecastTransaction.getRemainingAmount();
                }

                // Record the total expense:
                if (forecastTransaction.getRemainingAmount() < 0) {
                    totalExpense += forecastTransaction.getRemainingAmount();
                }

                // Record the total savings:
                if (forecastTransaction.getForecastItem().getPayee().equalsIgnoreCase("Savings")) {
                    totalSavings -= forecastTransaction.getRemainingAmount();
                }

                // Record the total debt expense:
                if (forecastTransaction.getForecastItem().getCategory().substring(0, 4).equalsIgnoreCase("Debt")) {
                    totalDebtExpense += forecastTransaction.getRemainingAmount();
                }
            }

            // Save off the forecast transaction with tne updated running balance:
            forecastTransaction.save(EntityInt.SaveMethod.UPDATE);

            double credit;
            double debit;
            if (Utility.doubleToInt(forecastTransaction.getRemainingAmount()) > 0) {
                credit = forecastTransaction.getRemainingAmount();
                debit = 0;
            } else {
                credit = 0;
                debit = -forecastTransaction.getRemainingAmount();
            }

            // Write out the forecast line:
            renderForecastTransaction(forecastTransaction, credit, debit);

            // Move to the next transaction:
            lastForecastTransaction = forecastTransaction;
            forecastTransaction = forecastTransactions.getNext();
        }

        // Finish up and closeout the forecast rendering:
        renderLongTermForecastBackMatter();
        closeLongTermForecastOutput();

/*
      // requesting below minimum balance events:
      LongTermForecast.SignificantEvents[] events = {daysBelowMinimumBalance};
      longTermForecast.summarize(events);

      // and print out the significant events list:
      forecastTransaction = longTermForecast.getFirstSignificantEvent();
      while (forecastTransaction != null) {
         System.out.println("The balance on " + Utility.calendarDateToStringDate(forecastTransaction.getPlannedDate()) +
                 " is $" + forecastTransaction.getRunningBalance());
         if (forecastTransaction.getRunningBalance() < longTermForecast.getMinimumBalance()) {
            System.out.println("Balance below minimum balance!");
         }
         forecastTransaction = forecastTransaction.getNextSignificantEvent();
      }
*/

        // Print out the forecast summary:
        getResolver().say("\nForecast Summary:");

        // Display the forecast summary period:
        int numberOfMonthsInForecast = Utility.monthsBetweenDatesInclusive(firstFirstOfMonth,
                lastForecastTransaction.getPlannedDate());
        getResolver().say(new StringBuilder().append("The forecast summary period is the ").
                append(numberOfMonthsInForecast).append(" month period from ").
                append(Utility.calendarDateToStringDate(firstFirstOfMonth)).append(" to ").
                append(Utility.calendarDateToStringDate(
                        Utility.getLastDayOfMonth(lastForecastTransaction.getPlannedDate()))).append(".").toString());

        // Display the starting balance:
        getResolver().say(new StringBuilder().append("The starting balance is: ").
                append(Utility.formatRoundedDollarAmount(firstFirstOfMonthBalance)).toString());

        // Display the ending balance:
        getResolver().say(new StringBuilder().append("The ending balance is: ").
                append(Utility.formatRoundedDollarAmount(runningBalance)).append(".").toString());

        // Display the net change in balance:
        double netChangeInBalance = runningBalance - firstFirstOfMonthBalance;
        getResolver().say(new StringBuilder().append("The net change in balance is: ").
                append(Utility.formatRoundedDollarAmount(netChangeInBalance)).append(".").toString());

        // Display the average monthly change in balance:
        double rateOfChangeInBalance = netChangeInBalance / numberOfMonthsInForecast;
        if (netChangeInBalance > 0) {
            getResolver().say(new StringBuilder().append("The average accumulation rate is: ").
                    append(Utility.formatRoundedDollarAmount(rateOfChangeInBalance)).
                    append(" per month.").toString());
        } else {
            getResolver().say(new StringBuilder().append("The average depletion rate is: ").
                    append(Utility.formatRoundedDollarAmount(rateOfChangeInBalance)).append(".").toString());
        }

        // Display the highest balance and the date on which it occurred:
        getResolver().say(new StringBuilder().append("The highest balance is: ").
                append(Utility.formatRoundedDollarAmount(highestBalance)).append(" on ").
                append(Utility.calendarDateToStringDate(dateOfHighestBalance)).append(".").toString());

        // Display the lowest balance and the date on which it occurred
        getResolver().say(new StringBuilder().append("The lowest balance is: ").
                append(Utility.formatRoundedDollarAmount(lowestBalance)).append(" on ").
                append(Utility.calendarDateToStringDate(dateOfLowestBalance)).append(".").toString());

        // If there are one or more negative balances, display the first negative balance and the date on which it
        // occurred:
        if (firstNegativeBalance < 0) {
            getResolver().say(new StringBuilder().append("The first negative balance is: ").
                    append(Utility.formatRoundedDollarAmount(firstNegativeBalance)).append(" on ").
                    append(Utility.calendarDateToStringDate(dateOfFirstNegativBalance)).append(".").toString());
        }

        // Display the total amount of income:
        getResolver().say(new StringBuilder().append("The total amount of income is: ").
                append(Utility.formatRoundedDollarAmount(totalIncome)).toString());

        // Display the total amount of expenses:
        getResolver().say(new StringBuilder().append("The total amount of expense is: ").
                append(Utility.formatRoundedDollarAmount(totalExpense)).toString());

        // Display the total amount of savings:
        getResolver().say(new StringBuilder().append("The total amount of savings is: ").
                append(Utility.formatRoundedDollarAmount(totalSavings)).toString());

        // Display the total amount of debt expense:
        getResolver().say(new StringBuilder().append("The total amount of debt expense is: ").
                append(Utility.formatRoundedDollarAmount(-totalDebtExpense)).toString());

        // Display the monthly amount of debt expense:
        getResolver().say(new StringBuilder().append("The monthly amount of debt expense is: ").
                append(Utility.formatRoundedDollarAmount(-totalDebtExpense / numberOfMonthsInForecast)).toString());

        // Display the debt to expense ratio:
        double debtToExpenseRatio = totalDebtExpense / totalExpense;
        double debtToIncomeRatio = -totalDebtExpense / totalIncome;
        getResolver().say(new StringBuilder().append("Debt expense comprises ").append(Math.round(debtToIncomeRatio * 100)).
                        append("% of total income and ").append(Math.round(debtToExpenseRatio * 100)).
                        append("% of total expense.").toString());

        // Print out the forecast analysis:
        getResolver().say("\nForecast Analysis:");

        // If the forecast is out of balance:
        double outOfBalanceAmount = runningBalance - firstFirstOfMonthBalance;
        double outOfBalanceMonthlyAmount = outOfBalanceAmount / numberOfMonthsInForecast;
        if (outOfBalanceAmount < 0) {

            getResolver().say(new StringBuilder().append("The forecast is out of balance by ").
                    append(Utility.formatRoundedDollarAmount(-outOfBalanceAmount)).
                    append(".").toString());

            // Tell the user how much they need to reduce spending or increase income to get the forecast back in
            // balance on a monthly basis:
            getResolver().say(new StringBuilder().append("You need to reduce spending or increase income by ").
                    append(Utility.formatRoundedDollarAmount(-outOfBalanceMonthlyAmount) + " per month.").toString());
        } else {
            getResolver().say("The forecast is balanced.  No action is required.");
        }

        // If there are any negative balances, then the float is insufficient.  Calculate the required float and let
        // the user know how much they need to deposit to fix the float issue:
        if (lowestBalance < 0) {

            // Recompute the required float by recomputing the running balances assuming that the user fixes the
            // out-of-balance issue:
            forecastTransactions = ForecastTransaction.getForecastTransactionsStartingOn(forecast, firstFirstOfMonth);
            forecastTransaction = forecastTransactions.getNext();
            runningBalance = 0;
            lowestBalance = 0;
            currentMonth = -1;
            while (forecastTransaction != null) {
                if (forecastTransaction.getPlannedDate().get(Calendar.MONTH) != currentMonth) {
                    runningBalance -= outOfBalanceMonthlyAmount;
                    currentMonth = forecastTransaction.getPlannedDate().get(Calendar.MONTH);
                }
                runningBalance += forecastTransaction.getRemainingAmount();
                if (runningBalance < lowestBalance) {
                    lowestBalance = runningBalance;
                    dateOfLowestBalance = forecastTransaction.getPlannedDate();
                }
                forecastTransaction = forecastTransactions.getNext();
            }
            getResolver().say(new StringBuilder().append("The required float on the date of the lowest balance (").
                    append(Utility.calendarDateToStringDate(dateOfLowestBalance)).append(") taking into account the " +
                            "effect of fixing the out-of-balance issue is ").
                    append(Utility.formatRoundedDollarAmount(-lowestBalance)).toString());

            // Tell the user how much they need to deposit to fix the float issue:
            if (lowestBalance + firstFirstOfMonthBalance < 0) {
                getResolver().say(new StringBuilder().append("To ensure you have no negative balances, you need to deposit ").
                        append(Utility.formatRoundedDollarAmount(-lowestBalance - firstFirstOfMonthBalance)).
                        append(" to the ").append(forecast.getBudget().getRegisters().get(0).getName()).append(" account.").
                        toString());
            } else if ((lowestBalance + firstFirstOfMonthBalance) > -1 && (lowestBalance + firstFirstOfMonthBalance < 1)) {
                getResolver().say(new StringBuilder().append("You have sufficient float to ensure no negative balances.").
                        append(" in the ").append(forecast.getBudget().getRegisters().get(0).getName()).
                        append(" account.").toString());
            } else {
                getResolver().say(new StringBuilder().append("You have excess float in the amount of ").
                        append(Utility.formatRoundedDollarAmount(lowestBalance + firstFirstOfMonthBalance)).
                        append(" in the ").append(forecast.getBudget().getRegisters().get(0).getName()).
                        append(" account.").toString());
            }
        }

        return true;
    }


    /*
     * Update the forecast from a list of forecast transactions from some external source:
     */
    public void updateFromExternalSource()
            throws Exception {

        // Keep a count of the forecast transactions from the external source for debugging purposes:
        int i = 0;

        try {
            // Open the external source and get a list of forecast transactions in it:
            List<ForecastTransaction> forecastTransactions = openForecastTransactionSource();

            // If we were able to open the external source:
            if (forecastTransactions != null) {

                // Mark all the forecast transactions in the forecast as not found:
                ForecastTransaction.setAllFound(false);

                // For each forecast transaction from the external source:
                for (ForecastTransaction ssForecastTransaction : forecastTransactions) {

                    // Keep track of the list item number for debugging purposes:
                    i++;

                    // If the current spreadsheet forecast transaction has an ID (the update case):
                    if (ssForecastTransaction.getId() != null) {

                        // then get the matching forecast transaction from the database:
                        ForecastTransaction dbForecastTransaction =
                                ForecastTransaction.getById(ssForecastTransaction.getId());

                        // and if a matching forecast transaction was found in the database:
                        if (dbForecastTransaction != null) {

                            // then mark the transaction as found:
                            dbForecastTransaction.setFound(true);

                            // and since the spreadsheet does not contain the budgeted amount we can add that now:
                            ssForecastTransaction.getForecastItem().setAmount(
                                    dbForecastTransaction.getForecastItem().getAmount()
                            );

                            // then if the forecast planned date has been modified then update the database transaction:
                            boolean overwrite;
                            if (ssForecastTransaction.getPlannedDate().compareTo(dbForecastTransaction.getPlannedDate()) != 0) {
                                // If the database forecast transaction was updated after it was sent to the external
                                // source:
                                overwrite = true;
                                if (Utility.dateOnlyCompare(ssForecastTransaction.getVersion(),
                                        dbForecastTransaction.getVersion()) < 0) {

                                    // Then ask the user if they want to over write the updated database value:
                                    Utility.getResolver().say("\nThe date of an imported forecast transaction has " +
                                            "changed, but the version of the imported forecast transaction is prior to" +
                                            " the version in the database.");
                                    Utility.getResolver().say("Imported " + ssForecastTransaction.toStringConcise());
                                    Utility.getResolver().say("Database " + dbForecastTransaction.toStringConcise());
                                    if (Utility.getResolver().selectFromFirstLetterList(
                                            "Which date do you want to use? (i - imported, d - database)",
                                            "i,d").equalsIgnoreCase("d")) {
                                        overwrite = false;
                                    }
                                }

                                // Overwrite the date in the database forecast transaction if appropriate:
                                if (overwrite) {
                                    Utility.getResolver().say("Date modified for " +
                                            dbForecastTransaction.toStringConcise());
                                    Utility.getResolver().say("New date is:  " +
                                            Utility.calendarDateToStringDate(ssForecastTransaction.getPlannedDate()));
                                    dbForecastTransaction.setPlannedDate(ssForecastTransaction.getPlannedDate());
                                    // TODO:  Set the "override" flag on the forecast transaction to prevent it from
                                    // being deleted during the forecast update process:
                                }
                            }

                            // and if the remaining amount has been modified and the difference is not just rounding
                            // to the nearest dollar for display purposes::
                            if (Math.abs(ssForecastTransaction.getRemainingAmount() -
                                    dbForecastTransaction.getRemainingAmount()) > 0.50) {

                                overwrite = true;
                                if (ssForecastTransaction.getVersion().compareTo(dbForecastTransaction.getVersion()) < 0) {
                                    // Then ask the user if they want to overwrite the updated database value:
                                    Utility.getResolver().say("\nThe amount of an imported forecast transaction has " +
                                            "changed, but the version of the imported forecast transaction is prior to" +
                                            " the version in the database.");
                                    Utility.getResolver().say("Imported " + ssForecastTransaction.toStringConcise());
                                    Utility.getResolver().say("Database " + dbForecastTransaction.toStringConcise());
                                    if (Utility.getResolver().selectFromFirstLetterList(
                                            "Which amount do you want to use (i - imported, d - database)?",
                                            "i,d").equalsIgnoreCase("d")) {
                                        overwrite = false;
                                    }
                                }

                                // Overwrite the amount in the database forecast transaction if appropriate:
                                if (overwrite) {

                                    // then update the database transaction:
                                    Utility.getResolver().say("Amount modified for " +
                                            dbForecastTransaction.toStringConcise());
                                    Utility.getResolver().say("New amount is:  " +
                                            Utility.formatDollarAmount(ssForecastTransaction.getRemainingAmount()));
                                    dbForecastTransaction.setRemainingAmount(ssForecastTransaction.getRemainingAmount());
                                    // TODO:  Set the "override" flag on the forecast transaction to prevent it from
                                    // being deleted during the forecast update process:
                                }
                            }

                            // and save the updated forecast transaction to the database:
                            dbForecastTransaction.update();

                        } else { // No matching transaction was found meaning that it has been deleted from the database:
                            getResolver().say("The following forecast transaction was updated, but it falls outside of " +
                                    "your short term horizon and has been invalidated by the last forecast update.  You " +
                                    "will have to remake this change" + "\n" + ssForecastTransaction);
                        }
                    } else { // the forecast transaction does not have an ID (the creation case), so create one:

                        // If there isn't already an instance of the forecast item for this forecast transaction in the forecast:
                        ForecastItem forecastItem = ForecastItem.getByName(ssForecastTransaction.getForecastItem().getForecast().getId(),
                                ssForecastTransaction.getForecastItem().getCategory(), ssForecastTransaction.getForecastItem().getPayee());
                        if (forecastItem == null) {

                            // then create a forecast item, so we have something to link the forecast transaction to:
                            // Get a list of budget items that match the entered payee:
                            List<BudgetItem> budgetItemsForPayee = BudgetItem.getUnexpiredByPayee(
                                    ssForecastTransaction.getForecastItem().getForecast().getBudget(),
                                    ssForecastTransaction.getForecastItem().getPayee());
                            BudgetItem budgetItem;
                            if (budgetItemsForPayee.size() > 1) {
                                budgetItem = getResolver().getUserSelectedBudgetItem(budgetItemsForPayee);
                            } else if (budgetItemsForPayee.size() == 1) {
                                budgetItem = budgetItemsForPayee.get(0);
                            } else {
                                budgetItem = null;
                            }

                            // TODO:  Handle if the budget item isn't found.
                            // If the budget item isn't found:

                            // then get the budget item from the user (adding a new one if required):

                            ssForecastTransaction.getForecastItem().setIdBudgetItem(budgetItem.getId());
                            ssForecastTransaction.getForecastItem().insert();
                        } else {
                            ssForecastTransaction.setForecastItem(forecastItem);
                        }

                        // Fill out the forecast transaction:
                        ssForecastTransaction.setId(UUID.randomUUID());
                        ssForecastTransaction.setFound(true);
                        ssForecastTransaction.setOverridden(true);
                        ssForecastTransaction.insert();

                        // Let the user know what we did:
                        getResolver().say("The following forecast transaction was not in the forecast so it has been " +
                                "added to the forecast:  \n" + ssForecastTransaction.toStringConcise());

                    } // End else the forecast transaction does not have an ID.
                } // End for each forecast transaction in the external source.

                // Set to zero in the forecast all the forecast transactions that were deleted from the spreadsheet (not found):
                ForecastTransaction.zeroNotFound();

                // Close the external source of forecast transactions:
                closeForecastTransactionSource();

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
            getResolver().say("\nSuccessfully processed " + i + " forecast transactions from the external source.");
        } else {
            getResolver().say("\nThere are no forecast transactions in the external source to update from.");
        }

    } // End updateFromExternalSource(Connection dbConnection).

    @Override
    public List<UserResource> renderItemsOfInterestReport() throws EntityException, Exception, BudgetException,
            ViewException, RegisterException {

        // Create a holder for the individual user reports:
        List<UserResource> reports = new ArrayList<>();

        // Get a list of users:
        List<User> users = User.getAllUsers();

        // For each user:
        for (User user : users
        ) {
            // Render an items of interest report for the current user:
            UserResource userResource = renderItemsOfInterestReport(user);
            if (userResource != null) {
                reports.add(userResource);
            }
        }
        return reports;
    }

    @Override
    public UserResource renderItemsOfInterestReport(User user) throws EntityException, Exception, BudgetException,
            ViewException, RegisterException {

        UserResource userResource = null;
        File itemsOfInterestReportFile = File.createTempFile("ItemsOfInterestReport_" + user.getFirstName() + "_",
                ".txt");
        if (renderItemsOfInterestReport(user, itemsOfInterestReportFile)) {
            userResource = new UserResource(user, UserResource.ResourceType.ItemsOfInterestReport,
                    itemsOfInterestReportFile);
        } else {
            itemsOfInterestReportFile.delete();
        }
        return userResource;
    }

    @Override
    public boolean renderItemsOfInterestReport(User user, File file) throws EntityException, Exception, BudgetException,
            ViewException, RegisterException {

        // Get a set of the items of interest of the current user:
        List<Entity> items = Collections.unmodifiableList(ForecastTransaction.getTrackingForecastTransactionsOfInterest(user));

        // Render an items of interest report for those items:
        boolean result = false;
        if (items.size() > 0) {
            TrackingItemsOfInterestReport trackingReport = getTrackingItemsOfInterestReport(user, items, file);
            Renderer<TrackingItemsOfInterestReport> renderer = new Renderer<>(trackingReport);
            renderer.renderReport();
            result = true;
        }

        if (result) {
            result = false;

            // Get a set of the items of interest of the current user:
            items = Collections.unmodifiableList(ForecastTransaction.getUpcomingForecastTransactionsOfInterest(user));

            // Render an items of interest report for those items:
            result = false;
            if (items.size() > 0) {
                UpcomingItemsOfInterestReport upcomingReport = getUpcomingItemsOfInterestReport(user, items, file);
                Renderer<UpcomingItemsOfInterestReport> renderer = new Renderer<>(upcomingReport);
                renderer.renderReport();
                result = true;
            }
        }
        return result;
    }


    @Override
    public List<UserResource> renderUpcomingItemsReport(Forecast forecast) throws EntityException, ViewException, Exception, BudgetException, RegisterException {

        // Create a holder for the individual user reports:
        List<UserResource> reports = new ArrayList<>();

        // Get a list of users:
        List<User> users = User.getAllUsers();

        // For each user:
        for (User user : users) {
            // Render an upcoming items report for the current user:
            UserResource userResource = renderUpcomingItemsReport(forecast, user);
            reports.add(userResource);
        }
        return reports;
    }

    @Override
    public UserResource renderUpcomingItemsReport(Forecast forecast, User user) throws EntityException, ViewException,
            Exception, BudgetException, RegisterException {

        UserResource userResource = null;
        File upcomingItemsReportFile = File.createTempFile("UpcomingItemsReport_" + user.getFirstName() + "_",
                ".txt");
        if (renderUpcomingItemsReport(forecast, user, upcomingItemsReportFile)) {
            userResource = new UserResource(user, UserResource.ResourceType.upcomingItemsReport,
                    upcomingItemsReportFile);
        } else {
            upcomingItemsReportFile.delete();
        }
        return userResource;
    }

    @Override
    public boolean renderUpcomingItemsReport(Forecast forecast, User user, File file) throws EntityException, ViewException,
            Exception, BudgetException, RegisterException {

        // Calculate the end date.  The algorithm is that the end date is always the end of a pay period.  For the 25th
        // through the 10th of the month, the end date is the 14th.  For the 10th through the 25th it is the end of the
        // month.
        Calendar endDate = Calendar.getInstance();
        if (endDate.get(Calendar.DATE) >= 25) {
            endDate.add(Calendar.MONTH, 1);
            endDate.set(Calendar.DATE, 14);
        } else if (endDate.get(Calendar.DATE) < 10) {
            endDate.set(Calendar.DATE, 14);
        } else {
            endDate.set(Calendar.DATE, endDate.getActualMaximum(Calendar.DATE));
        }

        // Get a list of upcoming items through the end date:
        List<Entity> items = Collections.unmodifiableList(ForecastTransaction.getItemsUpToDate(forecast, endDate));

        // Render an upcoming items report for those items:
        boolean result = false;
        if (items.size() > 0) {
            UpcomingItemsReport report = getUpcomingItemsReport(forecast, items, file);
            Renderer<UpcomingItemsReport> renderer = new Renderer<>(report);
            renderer.renderReport();
            result = true;
        }
        return result;
    }


    @Override
    public List<UserResource> renderOverdueItemsReport(Forecast forecast) throws EntityException, ViewException,
            Exception, BudgetException, RegisterException {

        // Create a holder for the individual user reports:
        List<UserResource> reports = new ArrayList<>();

        // Get a list of users:
        List<User> users = User.getAllUsers();

        // For each user:
        for (User user : users) {
            // Render an items of interest report for the current user:
            UserResource userResource = renderOverdueItemsReport(forecast, user);
            reports.add(userResource);
        }
        return reports;
    }

    @Override
    public UserResource renderOverdueItemsReport(Forecast forecast, User user) throws EntityException, ViewException,
            Exception, BudgetException, RegisterException {

        UserResource userResource = null;
        File overdueItemsReportFile = File.createTempFile("OverdueItemsReport" + user.getFirstName() + "_",
                ".txt");
        if (renderOverdueItemsReport(forecast, user, overdueItemsReportFile)) {
            userResource = new UserResource(user, UserResource.ResourceType.overdueItemsReport,
                    overdueItemsReportFile);
        } else {
            overdueItemsReportFile.delete();
        }
        return userResource;
    }

    @Override
    public boolean renderOverdueItemsReport(Forecast forecast, User user, File file) throws EntityException,
            ViewException, Exception,
            RegisterException, BudgetException {

        // Get a list of the overdue items:
        List<Entity> items = Collections.unmodifiableList(ForecastTransaction.getOverdueItems(user));

        // Render an overdue items report for those items:
        boolean result = false;
        if (items.size() > 0) {
            OverdueItemsReport report = getOverdueItemsReport(forecast, items, file);
            Renderer<OverdueItemsReport> renderer = new Renderer<>(report);
            renderer.renderReport();
            result = true;
        }
        return result;
    }

} // End class AbstractForecastView.
