package com.hixon.financialApp.view.base;

import com.hixon.financialApp.controller.ControllerException;
import com.hixon.financialApp.controller.QuitException;
import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.entity.Entity;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.*;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.model.user.UserResource;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.ViewException;
import com.hixon.financialApp.view.text.ItemsOfInterestReport;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.*;

import static com.hixon.financialApp.model.forecast.Forecast.SignificantEvents.daysBelowMinimumBalance;
import static com.hixon.financialApp.utility.Utility.getResolver;

public abstract class AbstractForecastView extends AbstractView implements ForecastViewInt {

    /*
     * Fields:
     */
    protected Forecast forecast = null;


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


    /*
     * Helper methods:
     */
    protected abstract void openLongTermForecastOutput() throws FileNotFoundException, UnsupportedEncodingException;

    protected abstract void renderLongTermForecastFrontMatter();

    protected abstract void renderMonthHeader(Calendar plannedDate, double runningBalance);

    protected abstract void renderForecastTransaction(ForecastTransaction forecastTransaction, int credit, int debit)
            throws EntityException, SQLException, ForecastException, BudgetException;

    protected abstract void renderLongTermForecastBackMatter();

    protected abstract void closeLongTermForecastOutput();

    protected abstract void closeForecastTransactionSource() throws ViewException;

    protected abstract List<ForecastTransaction> openForecastTransactionSource() throws IOException, ControllerException, BudgetException;

    protected abstract ItemsOfInterestReport getItemsOfInterestReport(User user, List<Entity> items, File file);

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
            QuitException, RegisterException {

        this.forecast = forecast;

        // Get the first day of the forecast rendering:
        Calendar startDate = Utility.askStartDate();

        // Get the starting balance.  Take if from the first register associated with the budget for now:
        List<Register> registers = forecast.getBudget().getRegisters();
        double runningBalance = registers.get(0).getBalance();

        // Verify the starting balance.  Update it if required:
        if (Utility.getResolver().getYesOrNo("Computed current balance of the " +
                registers.get(0).getRegisterName() + " is " + Utility.formatDollarAmount(runningBalance) +
                "  Do you want to update it?")) {
            runningBalance = Utility.getResolver().getDollarAmount();
            registers.get(0).setBalance(runningBalance);
            registers.get(0).update();
        }

        // Open and initialize the forecast rendering output file:
        openLongTermForecastOutput();
        renderLongTermForecastFrontMatter();

        // Iterate over all the forecast transactions in chronological order beginning on the start date:
        ForecastTransactionIterator forecastTransactions =
                ForecastTransaction.getForecastTransactionsStartingOn(this.forecast, startDate);
        ForecastTransaction forecastTransaction = forecastTransactions.getNext();
        int currentMonth = -1;
        while (forecastTransaction != null) {

            // If the month changed, write out a header line with the name of the month:
            if (forecastTransaction.getPlannedDate().get(Calendar.MONTH) != currentMonth) {
                renderMonthHeader(forecastTransaction.getPlannedDate(), runningBalance);
                currentMonth = forecastTransaction.getPlannedDate().get(Calendar.MONTH);
            }

            runningBalance += forecastTransaction.getRemainingAmount();
            forecastTransaction.setRunningBalance(runningBalance);

            int credit;
            int debit;
            if (Utility.doubleToInt(forecastTransaction.getRemainingAmount()) > 0) {
                credit = Utility.doubleToInt(forecastTransaction.getRemainingAmount());
                debit = 0;
            } else {
                credit = 0;
                debit = -Utility.doubleToInt(forecastTransaction.getRemainingAmount());
            }

            // Write out the forecast line:
            renderForecastTransaction(forecastTransaction, credit, debit);

            // Move to the next transaction:
            forecastTransaction = forecastTransactions.getNext();
        }

        // Finish up and closeout the forecast rendering:
        renderLongTermForecastBackMatter();
        closeLongTermForecastOutput();

        // TODO: To clue the user into what things to look for in the spreadsheet, run the forecast summary routine
        // requesting below minimum balance events:
/*
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

        // Print out the starting and ending balances:
        System.out.println("The starting balance is: " + Utility.formatDollarAmount(this.forecast.getStartingBalance()));
        System.out.println("The ending balance is:   " + Utility.formatDollarAmount(this.forecast.getEndingBalance()));
        System.out.println("The savings rate is:   " + Utility.formatDollarAmount(this.forecast.getEndingBalance() /
                this.forecast.getNumberOfMonths()) + " per month.");

        return true;
    }


    /*
     * Update the forecast from a list of forecast transactions from some external source:
     */
    public void updateFromExternalSource() throws ControllerException, ForecastException, EntityException, SQLException, ViewException, IOException {

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
                        System.out.println("Current forecast transaction:  " + ssForecastTransaction);
                        ForecastTransaction dbForecastTransaction = ForecastTransaction.getById(ssForecastTransaction.getId());

                        // and if a matching forecast transaction was found in the database:
                        if (dbForecastTransaction != null) {

                            // then mark the transaction as found:
                            dbForecastTransaction.setFound(true);

                            // then if the forecast planned date has been modified then update the database transaction:
                            if (ssForecastTransaction.getPlannedDate().compareTo(dbForecastTransaction.getPlannedDate()) != 0) {
                                Utility.copyDate(ssForecastTransaction.getPlannedDate(), dbForecastTransaction.getPlannedDate());
                            }

                            // and if the remaining amount has been modified, then update the database transaction:
                            if (ssForecastTransaction.getRemainingAmount() != dbForecastTransaction.getRemainingAmount()) {
                                dbForecastTransaction.setRemainingAmount(ssForecastTransaction.getRemainingAmount());
                            }

                            // and save the updated forecast transaction to the database:
                            dbForecastTransaction.update();

                        } else { // No matching transaction was found meaning that it has been deleted from the database:
                            getResolver().say("The following forecast transaction was updated, but it falls outside of " +
                                    "your short term horizon and has been invalidated by the last forecast update.  You will have " +
                                    "to remake this change" + "\n" + ssForecastTransaction);
                        }
                    } else { // the forecast transaction does not have an ID (the create case), so create one:

                        // If there isn't already an instance of the forecast item for this forecast transaction in the forecast:
                        ForecastItem forecastItem = ForecastItem.getByName(ssForecastTransaction.getForecastItem().getForecast().getId(),
                                ssForecastTransaction.getForecastItem().getCategory(), ssForecastTransaction.getForecastItem().getPayee());
                        if (forecastItem == null) {

                            // then create a forecast item so we have something to link the forecast transaction split to:
                            BudgetItem budgetItem = BudgetItem.getByPayee(ssForecastTransaction.getForecastItem().getPayee());
                            // TODO:  Handle if the budget item isn't found.
                            ssForecastTransaction.getForecastItem().setIdBudgetItem(budgetItem.getId());
                            ssForecastTransaction.getForecastItem().insert();
                        } else {
                            ssForecastTransaction.setForecastItem(forecastItem);
                        }

                        // Create the forecast transaction:
                        ssForecastTransaction.setId(UUID.randomUUID());
                        ssForecastTransaction.setFound(true);
                        ssForecastTransaction.insert();

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
            getResolver().say("Successfully updated " + i + " forecast transactions in the forecast.");
        } else {
            getResolver().say("There were no forecast transactions in the external source to update from.");
        }

    } // End updateFromExternalSource(Connection dbConnection).


    public void renderItemsOfInterestReport(File itemsOfInterestReport) {

    }


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
        List<Entity> items = Collections.unmodifiableList(ForecastTransaction.getForecastTransactionsOfInterest(user));

        // Render an items of interest report for those items:
        boolean result = false;
        if (items.size() > 0) {
            ItemsOfInterestReport report = getItemsOfInterestReport(user, items, file);
            Renderer<ItemsOfInterestReport> renderer = new Renderer<>(report);
            renderer.renderReport();
            result = true;
        }
        return result;
    }
} // End class AbstractForecastView.
