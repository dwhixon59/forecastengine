package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.financialinstitution.FinancialInstitutionInt;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.view.base.ViewInt;

import java.util.Calendar;


public class DailyUpdateController {

    /*
     * Statics and Constants:
     */


    /*
     * Fields:
     */
    protected SessionController sessionController;
    // Get session objects for convenience
    protected Register register;
    protected Budget budget;
    protected Forecast forecast;
    protected FinancialInstitutionInt financialInstitution;
    protected ViewInt view;
    protected NotificationServiceInt notificationService;


    /*
     * Getters and setters:
     */


    /*
     * Constructors:
     */
    public DailyUpdateController(SessionController sessionController) {
        this.sessionController = sessionController;
        this.register = sessionController.getRegister();
        this.budget = sessionController.getBudget();
        this.forecast = sessionController.getForecast();
        this.financialInstitution = sessionController.getFinancialInstitution();
        this.view = sessionController.getView();
        this.notificationService = sessionController.getNotificationService();
    }


    /*
     *  Helper methods:
     */


    /*
     * Main methods:
     */

    /**
     * Run the daily update to import and classify transactions from financial institutions.
     *
     * @return True if the updated succeeded.  False if an error was encountered or the user aborted.
     */
    public boolean run() throws QuitException, RuntimeException {

        boolean result = true;
        try {

            // Setup for the update run:
            ImportController importController = new ImportController(sessionController);
            RegisterController registerController = new RegisterController(sessionController);
            ForecastController forecastController = new ForecastController(sessionController);
            boolean inSync = true;


            // Check if user has modified the external forecast file since last render:
           view.sayH2("CHECK FOR EXTERNAL FORECAST CHANGES");
            try {
                if (forecastController.isExternalForecastFileNewer()) {
                   view.say("The external forecast file has been modified since it was last rendered.");
                    if (view.getYesOrNo("Do you want to import the changes from the external file?")) {
                       view.sayH2("IMPORT FORECAST CHANGES FROM EXTERNAL SOURCE");
                        try {
                            forecastController.updateFromExternalSource();
                           view.sayH4("The forecast was successfully updated from the external source.");
                        } catch (Exception e) {
                            if (!view.askContinue("\nThe error '" + e + "' occurred while importing forecast " +
                                    "changes from external source.")) {
                                throw e;
                            }
                        }
                    } else {
                       view.sayH4("External forecast changes not imported.");
                    }
                } else {
                   view.sayH4("External forecast file has not been modified since last render.");
                }
            } catch (QuitException qe) {
                throw qe;
            } catch (Exception e) {
                if (!view.askContinue("\nThe error '" + e + "' occurred while checking for external " +
                        "forecast changes.")) {
                    throw e;
                }
            }


            // Process any transactions skipped in previous update runs:
           view.sayH2("REPROCESS SKIPPED TRANSACTIONS");
            // If there are skipped transactions from previous runs:
            try {
                if (register.isSkippedTransactions(forecast)) {

                    // Then ask the user if they want to reprocess them now:
                    if (view.getYesOrNo("There are skipped transactions in the register.  " +
                            "Do you want to process them now?")) {
                        inSync = registerController.processUnreconciledTransactions();
                        if (!inSync) {
                            forecastController.updateForecast();
                        }
                       view.sayH4("The skipped transactions were successfully updated.");
                    } else {
                       view.sayH4("The skipped transactions were not processed.");
                    }
                } else {
                   view.sayH4("The are no skipped transactions.");
                }
            } catch (QuitException qe) {
                throw qe;
            } catch (Exception e) {
                if (!view.askContinue("\nThe error '" + e + "' occurred while reprocessing the skipped " +
                        "transactions.")) {
                    throw e;
                }
            }

            // Import the cleared transactions from the register:
            try {
               view.sayH2("IMPORT CLEARED TRANSACTIONS");
               inSync = importController.importRegisterTransactionFile();
             } catch (QuitException qe) {
                throw qe;
            } catch (java.io.FileNotFoundException fnfe) {
                // Provide a user-friendly message for missing files
                String message = "\nThe cleared transaction file could not be found.\n" +
                        "This can happen if:\n" +
                        "  • You haven't downloaded the file yet\n" +
                        "  • The file is in a different location\n" +
                        "  • The filename has changed (e.g., includes a date)\n\n" +
                        "Expected file: " + fnfe.getMessage() + "\n\n" +
                        "Skip importing cleared transactions and continue with the daily update?";
                if (!view.askContinue(message)) {
                    // User chose not to continue - abort the daily update
                   sessionController.getView().sayH4("Aborting the daily update process at the user's request.");
                    return false;
                }
                // User chose to continue - skip cleared transaction import and proceed
               view.say("Import of cleared transactions skipped.");
            } catch (Exception e) {
                if (!view.askContinue("\nAn error occurred while importing cleared transactions:\n" +
                        e.getClass().getSimpleName() + ": " + e.getMessage() + "\n\n" +
                        "Skip this step and continue with the daily update?")) {
                    throw e;
                }
               view.say("Import of cleared transactions skipped.");
            }

            // Import the provisional transactions from the register:
            // Only attempt to import if a provisional transaction filename is configured
            String provisionalFileName = register.getProvisionalTrxFileName();
            if (provisionalFileName != null && !provisionalFileName.trim().isEmpty()) {
               view.sayH2("IMPORT PROVISIONAL TRANSACTIONS");
                try {
                    if (view.existsFileWithRetry(Transaction.PROVISIONAL_TRANSACTIONS_FILE,
                            register.getProvisionalTrxFileDirectory() + "\\" + provisionalFileName))
                    {
                        // Import using format-agnostic method (detects CSV/TSV automatically)
                        boolean inSyncProv = importController.importProvisionalTransactionFile();
                       view.sayH4("The provisional transactions were successfully imported.");
                        if (!inSyncProv) {
                            inSync = false;
                        }
                    } else {
                       view.say("Import of provisional transactions skipped at user's request.");
                    }
                } catch (QuitException qe) {
                    throw qe;
                } catch (Exception e) {
                    String message = "\nAn error occurred while importing provisional transactions:\n" +
                            e.getClass().getSimpleName() + ": " + e.getMessage() + "\n\n" +
                            "Would you like to skip this step and continue?";
                    if (!view.askContinue(message)) {
                        throw e;
                    }
                   view.say("Import of provisional transactions skipped.");
                }
            }

            // Verify the register balance:
           view.sayH2("VERIFY REGISTER BALANCE");
            try {
                 if (!registerController.verifyRegisterBalance(register)) {
                   view.sayH4("The balance of the register " + register.getName() + " was " +
                            "successfully updated.");
                }
            } catch (Exception e) {
                if (!view.askContinue("The error '" + e + "' occurred while verifying the register " +
                        "balance. ")) {
                    throw e;
                }
            }

            // If changes were made to one or more budget items during the importing of transactions:
            if (!inSync) {
               view.sayH2("UPDATE THE FORECAST");

                // Ask the user if they want to update the forecast:
                if (view.getYesOrNo("Budget items were changed.  Do you want to update the forecast?")) {
                    try {
                        forecastController.updateForecast();
                       view.sayH4("The long term forecast was successfully updated.");
                    } catch (QuitException qe) {
                        throw qe;
                    } catch (Exception e) {
                        if (!view.askContinue("The error '" + e + "' occurred while updating the " +
                                "forecast.")) {
                            throw e;
                        }
                    }
                } else {
                   view.say("The forecast was not updated.");
                }
            }

            // Render the long term forecast:
           view.sayH2("RENDER THE LONG TERM FORECAST");
            try {
                sessionController.getForecastView().renderLongTermForecast(forecast);
               view.say("\nSuccessfully rendered the long term forecast.");
            } catch (QuitException qe) {
                throw qe;
            } catch (Exception e) {
                if (!view.askContinue("\nThe error '" + e + "' occurred while rendering the forecast.")) {
                    throw e;
                }
            }

            // Open the forecast in Excel for review:
           view.sayH2("OPEN THE FORECAST FOR REVIEW");
            try {
                sessionController.getForecastView().editLongTermForecast();
            } catch (QuitException qe) {
                throw qe;
            } catch (Exception e) {
                if (!view.askContinue("\nThe error '" + e + "' occurred while opening the forecast in Excel.")) {
                    throw e;
                }
               view.say("Skipped opening forecast in Excel.");
            }

            // If the user made changes to the forecast, import them:
            if (view.getYesOrNo("Did you make any changes to the forecast in Excel that you want to import?")) {
               view.sayH2("UPDATE THE FORECAST FROM AN EXTERNAL SOURCE");
                try {
                    forecastController.updateFromExternalSource();
                   view.sayH4("The forecast was successfully updated from the external source.");

                    // Re-render the forecast to reflect the changes
                   view.sayH2("RE-RENDER THE FORECAST");
                    try {
                        sessionController.getForecastView().renderLongTermForecast(forecast);
                       view.say("\nSuccessfully re-rendered the long term forecast with your changes.");
                    } catch (Exception re) {
                        if (!view.askContinue("\nThe error '" + re + "' occurred while re-rendering the forecast.")) {
                            throw re;
                        }
                    }
                } catch (Exception e) {
                    if (!view.askContinue("\nThe error '" + e + "' occurred while updating the forecast from " +
                            "an external source.")) {
                        throw e;
                    }
                }
            } else {
               view.say("Forecast changes not imported.");
            }

            // Render the Spending Report for the current month:
            try {
               view.sayH2("RENDER THE SPENDING REPORT");
               sessionController.getBudgetView().renderSpendingReportForMonth(Calendar.getInstance(), budget);
               view.sayH4("The spending report was successfully rendered");
            } catch (Exception e) {
                if (!view.askContinue("The error '" + e + "' occurred while rendering the spending report.")) {
                    throw e;
                }
            }

            // Render the Items of Interest report:
            try {
               view.sayH2("RENDERING THE ITEMS OF INTEREST REPORT");
               notificationService.sendItemsOfInterestReport(forecast);
               view.sayH4("Successfully rendered the Items of Interest Report.");
             } catch (Exception e) {
                if (!view.askContinue("\nThe error '" + e + "' occurred while Rendering of the Items of Interest " +
                        "report.")) {
                    throw e;
                }
            }

            // Render the Overdue and Upcoming Items Report:
            try {
               view.sayH2("RENDERING THE OVERDUE AND UPCOMING ITEMS REPORT");
               notificationService.sendOverdueAndUpcomingItemsReport(forecast);
               view.sayH4("Successfully rendered the Overdue and Upcoming Items Report.");
            } catch (Exception e) {
                if (!view.askContinue("The error '" + e + "' occurred while rendering of the Overdue and Upcoming " +
                        "Items Report.")) {
                    throw e;
                }
            }

            // Render the New Transaction Summary report:
            try {
               view.sayH2("RENDERING THE NEW TRANSACTION SUMMARY REPORT");
                notificationService.sendNewTransactionSummaryReport(register);
               view.sayH4("Successfully rendered the New Transaction Summary Report.");
            } catch (Exception e) {
                if (!view.askContinue("The error '" + e + "' occurred while rendering the New Transaction Summary " +
                        "Report.")) {
                    throw e;
                }
            }

            // Check for duplicate forecast transactions:
            try {
               view.sayH2("CHECKING FOR DUPLICATE FORECAST TRANSACTIONS");
               forecast.checkForDuplicateTransactions();
               view.sayH4("Duplicate forecast transaction check complete.");
            } catch (Exception e) {
                if (!view.askContinue("The error '" + e + "' occurred while checking for duplicate forecast " +
                        "transactions.")) {
                    throw e;
                }
            }
        }

        // If an exception during the update process:
        catch (QuitException qe) {

           sessionController.getView().sayH4("Aborting the daily update process at the user's request.");
            result = false;

        }
        catch (Exception e) {

           sessionController.getView().sayH4("Aborting the daily update process because an exception occurred.");
            throw new RuntimeException(e);
        }

        return result;

    }  // End runUpdate().
} // End class DailyUpdate.
