package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.view.base.ViewInt;

import java.security.InvalidParameterException;
import java.util.Calendar;


public class DailyUpdateController {

    /*
     * Statics and Constants:
     */


    /*
     * Fields:
     */
    private SessionController sessionController;


    /*
     * Getters and setters:
     */


    /*
     * Constructors:
     */
    public DailyUpdateController(SessionController sessionController) {
        if (sessionController != null) {
            this.sessionController = sessionController;
        } else {
            throw new InvalidParameterException("Session controller must not be null.");
        }
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
            // Get session objects for convenience
            Register register = sessionController.getRegister();
            Budget budget = sessionController.getBudget();
            Forecast forecast = sessionController.getForecast();
            FinancialInstitutionInt financialInstitution = sessionController.getFinancialInstitution();
            ViewInt view = sessionController.getView();
            NotificationServiceInt notificationService = sessionController.getNotificationService();

            // Setup for the update run:
            ImportController importController = new ImportController(register, financialInstitution, budget, forecast,
                    view, notificationService);
            RegisterController registerController = new RegisterController(register, financialInstitution, budget,
                    forecast, view, notificationService);
            ForecastController forecastController = new ForecastController(register, budget, forecast,
                    view, notificationService);
            boolean inSync = true;

            // Update the forecast from the spreadsheet if the user made any updates to the spreadsheet:
            view.say("\n\n========================================================================");
            view.say("UPDATE THE FORECAST FROM AN EXTERNAL SOURCE.");
            try {
                forecastController.updateFromExternalSource();
            } catch (Exception e) {
                if (!view.askContinue("\nThe error '" + e + "' occurred while updating the forecast from " +
                        "an external source.")) {
                    throw e;
                }
            }
            view.say("------------------------------------------------------------------------");

            // Process any transactions skipped in previous update runs:
           view.say("\n\n===============================================================" +
                    "=========");
           view.say("REPROCESS SKIPPED TRANSACTIONS");
            // If there are skipped transactions from previous runs:
            try {
                if (register.isSkippedTransactions(forecast)) {

                    // Then ask the user if they want to reprocess them now:
                    if (view.getYesOrNo("\nThere are skipped transactions in the register.  " +
                            "Do you want to process them now?")) {
                        inSync = registerController.processUnreconciledTransactions();
                        if (!inSync) {
                            forecastController.updateForecast();
                        }
                       view.say("\nThe skipped transactions were successfully updated.");
                    } else {
                       view.say("\nThe skipped transactions were not processed.");
                    }
                } else {
                   view.say("\nThe are no skipped transactions.");
                }
               view.say("---------------------------------------------------------------" +
                        "---------");
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
               view.say("\n\n========================================================================");
               view.say("IMPORT CLEARED TRANSACTIONS");

                if (view.existsFileWithRetry(Transaction.CLEARED_TRANSACTIONS_FILE,
                        register.getTrxImportFilePath()))
                {
                    inSync = importController.importCsvRegisterTransactionFile();
                } else
                {
                   view.say("Import of cleared transactions skipped at user's request.");
                }
               view.say("------------------------------------------------------------------------");
            } catch (QuitException qe) {
                throw qe;
            } catch (Exception e) {
                if (!view.askContinue("\nThe error '" + e + "' occurred while importing new transactions " +
                        "into the register.")) {
                    throw e;
                }
            }

            // Import the provisional transactions from the register:
           view.say("\n\n===================================================================" +
                    "=====");
           view.say("IMPORT PROVISIONAL TRANSACTIONS");
            try {
                if (view.existsFileWithRetry(Transaction.PROVISIONAL_TRANSACTIONS_FILE,
                        register.getProvisionalTrxFileDirectory() + "\\" + register.getProvisionalTrxFileName()))
                {
                    // Then import them:
                    boolean inSyncProv = importController.importCsvProvisionalTransactionFile();
                   view.say("\nThe provisional transactions were successfully imported.");
                    if (!inSyncProv) {
                        inSync = false;
                    }
                } else {
                   view.say("\nImport of provisional transactions skipped at user's request.");
                }
               view.say("------------------------------------------------------------------------");
            } catch (QuitException qe) {
                throw qe;
            } catch (Exception e) {
                if (!view.askContinue("\nThe error '" + e + "' occurred while importing the provisional " +
                        " transactions into the register.")) {
                    throw e;
                }
            }

            // Verify the register balance:
           view.say("\n\n========================================================================");
           view.say("VERIFY REGISTER BALANCE\n");
            try {
                 if (!registerController.verifyRegisterBalance(register)) {
                   view.say("The balance of the register " + register.getName() + " was " +
                            "successfully updated.");
                }
            } catch (Exception e) {
                if (!view.askContinue("\nThe error '" + e + "' occurred while verifying the register " +
                        "balance. ")) {
                    throw e;
                }
            }
           view.say("------------------------------------------------------------------------");

            // If changes were made to one or more budget items during the importing of transactions:
            if (!inSync) {
               view.say("\n\n========================================================================");
               view.say("UPDATE THE FORECAST\n");

                // Ask the user if they want to update the forecast:
                if (view.getYesOrNo("Budget items were changed.  Do you want to update the forecast?")) {
                    try {
                        forecastController.updateForecast();
                       view.say("\nThe long term forecast was successfully updated.");
                    } catch (QuitException qe) {
                        throw qe;
                    } catch (Exception e) {
                        if (!view.askContinue("\nThe error '" + e + "' occurred while updating the " +
                                "forecast.")) {
                            throw e;
                        }
                    }
                } else {
                   view.say("The forecast was not updated.");
                }
            }

            // Render the long term forecast:
           view.say("\n\n========================================================================");
           view.say("RENDER THE LONG TERM FORECAST\n");
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
           view.say("------------------------------------------------------------------------");

            // Render the Spending Report for the current month:
            try {
               view.say("\n\n========================================================================");
               view.say("RENDER THE SPENDING REPORT\n");
               sessionController.getBudgetView().renderSpendingReportForMonth(Calendar.getInstance(), budget);
               view.say("The spending report was successfully rendered");
               view.say("------------------------------------------------------------------------");
            } catch (Exception e) {
                if (!view.askContinue("\nThe error '" + e + "' occurred while rendering the spending report.")) {
                    throw e;
                }
            }

            // Render the Items of Interest report:
            try {
               view.say("\n\n========================================================================");
               view.say("RENDERING THE ITEMS OF INTEREST REPORT\n");
               notificationService.sendItemsOfInterestReport(forecast);
               view.say("Successfully rendered the Items of Interest Report.");
               view.say("------------------------------------------------------------------------");
             } catch (Exception e) {
                if (!view.askContinue("\nThe error '" + e + "' occurred while Rendering of the Items of Interest " +
                        "report.")) {
                    throw e;
                }
            }

            // Render the Overdue and Upcoming Items Report:
            try {
               view.say("\n\n========================================================================");
               view.say("RENDERING THE OVERDUE AND UPCOMING ITEMS REPORT\n");
               notificationService.sendOverdueAndUpcomingItemsReport(forecast);
               view.say("Successfully rendered the Overdue and Upcoming Items Report.");
               view.say("------------------------------------------------------------------------");
            } catch (Exception e) {
                if (!view.askContinue("\nThe error '" + e + "' occurred while rendering of the Overdue and Upcoming " +
                        "Items Report.")) {
                    throw e;
                }
            }

            // Render the New Transaction Summary report:
            try {
               view.say("\n\n========================================================================");
               view.say("RENDERING THE NEW TRANSACTION SUMMARY REPORT\n");
                notificationService.sendNewTransactionSummaryReport(register);
               view.say("Successfully rendered the New Transaction Summary Report.");
            } catch (Exception e) {
                if (!view.askContinue("\nThe error '" + e + "' occurred while rendering the New Transaction Summary " +
                        "Report.")) {
                    throw e;
                }
            }
        }

        // If an exception during the update process:
        catch (QuitException qe) {

           sessionController.getView().say("\nAborting the daily update process at the user's request.");
            result = false;

        } catch (Exception e) {

           sessionController.getView().say("\nAborting the daily update process because an exception occurred.");
            throw new RuntimeException(e);
        }

        return result;

    }  // End runUpdate().
} // End class DailyUpdate.
