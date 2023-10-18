package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.register.FinancialInstitutionInt;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.Transaction;

import java.security.InvalidParameterException;
import java.sql.SQLException;
import java.util.Calendar;

import static com.hixon.financialApp.utility.Utility.*;


public class DailyUpdate {

    /*
     * Constants:
     */
    private static final String CONTINUE_QUESTION = "Do you want to continue with the daily update?";


    /*
     * Fields:
     */
    Register register;
    FinancialInstitutionInt financialInstitution;
    Budget budget;
    Forecast forecast;


    /*
     * Getters and setters:
     */


    /*
     * Constructors:
     */
    public DailyUpdate(Register register, FinancialInstitutionInt financialInstitution, Budget budget, Forecast forecast)
            throws SQLException {
        if (register != null) {
            this.register = register;
        } else {
            throw new InvalidParameterException("Register must not be null.");
        }
        if (financialInstitution != null) {
            this.financialInstitution = financialInstitution;
        } else {
            throw new InvalidParameterException("Financial institution must not be null.");
        }
        if (budget != null) {
            this.budget = budget;
        } else {
            throw new InvalidParameterException("Budget must not be null.");
        }
        if (forecast != null) {
            this.forecast = forecast;
        } else {
            throw new InvalidParameterException("Forecast must not be null.");
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
            // Setup for the update run:
            Importer importer = new Importer();
            boolean inSync = true;

            // Process any transactions skipped in previous update runs:
            getResolver().say("\n\n===============================================================" +
                    "=========");
            getResolver().say("REPROCESS SKIPPED TRANSACTIONS");
            // If there are skipped transactions from previous runs:
            try {
                if (register.isSkippedTransactions(forecast)) {

                    // Then ask the user if they want to reprocess them now:
                    if (getResolver().getYesOrNo("\nThere are skipped transactions in the register." +
                            "Do you want to process them now?")) {
                        inSync = register.processUnreconciledTransactions(financialInstitution, register, forecast);
                        if (!inSync) {
                            forecast.updateForecast();
                        }
                        getResolver().say("\nThe skipped transactions were successfully updated.");
                    } else {
                        getResolver().say("\nThe skipped transactions were not processed.");
                    }
                } else {
                    getResolver().say("\nThe are no skipped transactions.");
                }
                getResolver().say("---------------------------------------------------------------" +
                        "---------");
            } catch (QuitException qe) {
                throw qe;
            } catch (Exception e) {
                if (!getResolver().askContinue("\nThe error '" + e + "' occurred while reprocessing the skipped " +
                        "transactions.")) {
                    throw e;
                }
            }

            // Import the cleared transactions from the register:
            try {
                getResolver().say("\n\n========================================================================");
                getResolver().say("IMPORT CLEARED TRANSACTIONS");

                if (getResolver().existsFileWithRetry(Transaction.CLEARED_TRANSACTIONS_FILE,
                        register.getTrxImportFilePath()))
                {
                    inSync = importer.importCsvRegisterTransactionFile(financialInstitution,
                            register, forecast);
                } else
                {
                    getResolver().say("Import of cleared transactions skipped at user's request.");
                }
                getResolver().say("------------------------------------------------------------------------");
            } catch (QuitException qe) {
                throw qe;
            } catch (Exception e) {
                if (!getResolver().askContinue("\nThe error '" + e + "' occurred while importing new transactions " +
                        "into the register.")) {
                    throw e;
                }
            }

            // Import the provisional transactions from the register:
            getResolver().say("\n\n===================================================================" +
                    "=====");
            getResolver().say("IMPORT PROVISIONAL TRANSACTIONS");
            try {
                if (getResolver().existsFileWithRetry(Transaction.PROVISIONAL_TRANSACTIONS_FILE,
                        register.getProvisionalTrxFileDirectory() + "\\" + register.getProvisionalTrxFileName()))
                {
                    // Then import them:
                    boolean inSyncProv = importer.importCsvProvisionalTransactionFile(financialInstitution,
                            register, forecast);
                    getResolver().say("\nThe provisional transactions were successfully imported.");
                    if (!inSyncProv) {
                        inSync = false;
                    }
                } else {
                    getResolver().say("\nImport of provisional transactions skipped at user's request.");
                }
                getResolver().say("------------------------------------------------------------------------");
            } catch (QuitException qe) {
                throw qe;
            } catch (Exception e) {
                if (!getResolver().askContinue("\nThe error '" + e + "' occurred while importing the provisional " +
                        " transactions into the register.")) {
                    throw e;
                }
            }

            // Update the forecast from the spreadsheet if the user made any updates to the spreadsheet:
            getResolver().say("\n\n========================================================================");
            getResolver().say("UPDATE THE FORECAST FROM AN EXTERNAL SOURCE.");
            try {
                if (getResolver().existsFileWithRetry(Forecast.FORECAST_TRANSACTIONS_FILE, Forecast.FORECAST_TRANSACTIONS_FILENAME)) {
                    getForecastView().updateFromExternalSource();
                } else {
                    getResolver().say("Update of the forecast from an external source skipped at user's request.");
                }
            } catch (QuitException qe) {
                throw qe;
            } catch (Exception e) {
                if (!getResolver().askContinue("\nThe error '" + e + "' occurred while updating the forecast from " +
                        "an external source.")) {
                    throw e;
                }
            }
            getResolver().say("------------------------------------------------------------------------");

            // Verify the register balance:
            getResolver().say("\n\n========================================================================");
            getResolver().say("VERIFY REGISTER BALANCE\n");
            try {
                if (!getRegisterView().verifyRegisterBalance(register)) {
                    getResolver().say("The balance of the register " + register.getName() + " was " +
                            "successfully updated.");
                }
            } catch (Exception e) {
                if (!getResolver().askContinue("\nThe error '" + e + "' occurred while verifying the register " +
                        "balance. ")) {
                    throw e;
                }
            }
            getResolver().say("------------------------------------------------------------------------");

            // If changes were made to one or more budget items during the importing of transactions:
            if (!inSync) {
                getResolver().say("\n\n========================================================================");
                getResolver().say("UPDATE THE FORECAST\n");

                // Ask the user if they want to update the forecast:
                if (getResolver().getYesOrNo("Budget items were changed.  Do you want to update the forecast?")) {
                    try {
                        forecast.updateForecast();
                        getResolver().say("\nThe long term forecast was successfully updated.");
                    } catch (QuitException qe) {
                        throw qe;
                    } catch (Exception e) {
                        if (!getResolver().askContinue("\nThe error '" + e + "' occurred while updating the " +
                                "forecast.")) {
                            throw e;
                        }
                    }
                } else {
                    getResolver().say("The forecast was not updated.");
                }
            }

            // Render the long term forecast:
            getResolver().say("\n\n========================================================================");
            getResolver().say("RENDER THE LONG TERM FORECAST\n");
            try {
                getForecastView().renderLongTermForecast(forecast);
                getResolver().say("Successfully rendered the long term forecast.");
            } catch (QuitException qe) {
                throw qe;
            } catch (Exception e) {
                if (!getResolver().askContinue("\nThe error '" + e + "' occurred while rendering the forecast.")) {
                    throw e;
                }
            }
            getResolver().say("------------------------------------------------------------------------");

            // Render the Spending Report for the current month:
            try {
                getResolver().say("\n\n========================================================================");
                getResolver().say("RENDER THE SPENDING REPORT\n");
                getBudgetView().renderSpendingReportForMonth(Calendar.getInstance());
                getResolver().say("The spending report was successfully rendered");
                getResolver().say("------------------------------------------------------------------------");
            } catch (Exception e) {
                if (!getResolver().askContinue("\nThe error '" + e + "' occurred while rendering the spending report.")) {
                    throw e;
                }
            }

            // Render the Items of Interest report:
            try {
                getResolver().say("\n\n========================================================================");
                getResolver().say("RENDERING THE ITEMS OF INTEREST REPORT\n");
                //getNotificationService().sendItemsOfInterestReport(forecast);
                getResolver().say("Successfully rendered the Items of Interest Report.");
                getResolver().say("------------------------------------------------------------------------");
             } catch (Exception e) {
                if (!getResolver().askContinue("\nThe error '" + e + "' occurred while Rendering of the Items of Interest " +
                        "report.")) {
                    throw e;
                }
            }

            // Render the Overdue and Upcoming Items Report:
            try {
                getResolver().say("\n\n========================================================================");
                getResolver().say("RENDERING THE OVERDUE AND UPCOMING ITEMS REPORT\n");
                getNotificationService().sendOverdueAndUpcomingItemsReport(forecast);
                getResolver().say("Successfully rendered the Overdue and Upcoming Items Report.");
                getResolver().say("------------------------------------------------------------------------");
            } catch (Exception e) {
                if (!getResolver().askContinue("\nThe error '" + e + "' occurred while rendering of the Overdue and Upcoming " +
                        "Items Report.")) {
                    throw e;
                }
            }

            // Render the New Transaction Summary report:
            try {
                getResolver().say("\n\n========================================================================");
                getResolver().say("RENDERING THE NEW TRANSACTION SUMMARY REPORT\n");
                getNotificationService().sendNewTransactionSummaryReport(register);
                getResolver().say("Successfully rendered the New Transaction Summary Report.");
            } catch (Exception e) {
                if (!getResolver().askContinue("\nThe error '" + e + "' occurred while rendering the New Transaction Summary " +
                        "Report.")) {
                    throw e;
                }
            }
        }

        // If an exception during the update process:
        catch (QuitException qe) {

            getResolver().say("\nAborting the daily update process at the user's request.");
            result = false;

        } catch (Exception e) {

            getResolver().say("\nAborting the daily update process because an exception occurred.");
            throw new RuntimeException(e);
        }

        return result;

    }  // End runUpdate().
} // End class DailyUpdate.
