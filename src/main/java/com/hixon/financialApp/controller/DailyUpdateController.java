package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.BudgetItemUtilities;
import com.hixon.financialApp.model.financialinstitution.FinancialInstitutionInt;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.base.ViewInt;

import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


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

    /**
     * Whether this run left the register and the forecast exactly as it found them.
     *
     * <p>That is the ordinary outcome of re-importing a statement:  a bank names a download after its
     * start date, so a re-download arrives under the name the last one had, and every charge in it is
     * already held.  On 09-20-2026 the Citi statement qdl20260911.QFX was imported for the second time,
     * all seven of its charges were recognised, and the daily update went on to re-render the forecast
     * and open it in Excel with not one figure different from the render two days earlier.
     *
     * <p>Every way this run could have changed something has to be accounted for here, since the answer
     * decides whether the forecast is rendered at all.  {@code inSync} covers the changes that reach the
     * forecast through the import itself -- a pending charge that fell off, a split assigned late -- and
     * the flags cover the ones the user made.
     *
     * @param newlyImported                    how many transactions the import took into the register
     * @param recategorized                    whether the import summary recategorized anything
     * @param balanceUpdated                   whether the register balance was corrected
     * @param skippedTransactionsProcessed     whether transactions skipped earlier were reprocessed
     * @param externalForecastChangesImported  whether the spreadsheet's changes were read in at the start
     * @param forecastUpdated                  whether the forecast was regenerated
     * @param inSync                           whether the forecast is still in step with the register
     * @return true when nothing changed
     */
    static boolean nothingChangedThisRun(int newlyImported, boolean recategorized, boolean balanceUpdated,
                                         boolean skippedTransactionsProcessed,
                                         boolean externalForecastChangesImported, boolean forecastUpdated,
                                         boolean inSync) {
        return newlyImported == 0 && !recategorized && !balanceUpdated && !skippedTransactionsProcessed
                && !externalForecastChangesImported && !forecastUpdated && inSync;
    }

    /**
     * The session budget's items as they stand now, for {@link ForecastChangeReasons}.
     *
     * @return the snapshot, or null if the budget could not be read -- a missing snapshot must not make
     *         every item look added or removed
     */
    private Map<String, ForecastChangeReasons.ItemState> snapshotBudget() {
        try {
            Budget sessionBudget = sessionController.getBudget();
            return sessionBudget == null ? null
                    : ForecastChangeReasons.snapshot(BudgetItemUtilities.getAllBudgetItemsForBudget(sessionBudget));
        } catch (Exception e) {
            return null;
        }
    }


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

            // Remember how the budget and forecast stood before anything ran, so that the offer to update
            // the forecast can say what made it necessary:
            boolean forecastStaleAtStart = forecast != null && !forecast.getInSync();
            Map<String, ForecastChangeReasons.ItemState> budgetBefore = snapshotBudget();
            boolean recategorized = false;
            Set<UUID> recategorizedItems = new java.util.HashSet<>();

            // What this run actually changed.  When it changed nothing -- the ordinary outcome of
            // re-importing a statement the register already holds -- there is nothing for a new render of
            // the forecast to show, and nothing to review in Excel.  See nothingChangedThisRun.
            boolean externalForecastChangesImported = false;
            boolean skippedTransactionsProcessed = false;
            boolean balanceUpdated = false;
            boolean forecastUpdated = false;


            // Check if user has modified the external forecast file since last render:
           view.sayH2("CHECK FOR EXTERNAL FORECAST CHANGES");
            try {
                if (forecastController.isExternalForecastFileNewer()) {
                   view.say("The external forecast file has been modified since it was last rendered.");
                    if (view.getYesOrNo("Do you want to import the changes from the external file?")) {
                       view.sayH2("IMPORT FORECAST CHANGES FROM EXTERNAL SOURCE");
                        try {
                            forecastController.updateFromExternalSource();
                            externalForecastChangesImported = true;
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

                    // Enhancement 7: Auto-default reprocess skipped transactions to yes
                    // during a daily update, since the user almost always wants to process them.
                    view.say("There are skipped transactions in the register. Auto-reprocessing...");
                    skippedTransactionsProcessed = true;
                    inSync = registerController.processUnreconciledTransactions();
                    if (!inSync) {
                        // Only the budget items that changed:  see ForecastChangeReasons.updateScope.
                        forecastController.updateForecast(ForecastChangeReasons.updateScope(forecastStaleAtStart,
                                ForecastChangeReasons.changedItemIds(budgetBefore, snapshotBudget()), false, null));
                    }
                   view.sayH4("The skipped transactions were successfully updated.");
                } else {
                   view.sayH4("There are no skipped transactions.");
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

            // Show import summary and allow the user to recategorize transactions:
            view.sayH2("REVIEW IMPORTED TRANSACTIONS");
            try {
                ImportSummaryController importSummaryController =
                        new ImportSummaryController(sessionController, importController.getImportLog());
                boolean recatChanged = importSummaryController.showSummaryAndRecategorize();
                recategorized = recatChanged;
                recategorizedItems = importSummaryController.getRecategorizedBudgetItems();
                if (recatChanged && inSync) {
                    inSync = false;
                }
            } catch (QuitException qe) {
                throw qe;
            } catch (Exception e) {
                if (!view.askContinue("\nThe error '" + e + "' occurred while reviewing imported transactions.")) {
                    throw e;
                }
            }

            // Verify the register balance:
            view.sayH2("VERIFY REGISTER BALANCE");
            try {
                 if (!registerController.verifyRegisterBalance(register)) {
                    balanceUpdated = true;
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

                // Say why the forecast is out of date, then ask the user if they want to update it.  Changes
                // to on-demand and unplanned items are not reasons -- they generate no occurrences -- so when
                // nothing else changed there is nothing to update and nothing to ask.
                Map<String, ForecastChangeReasons.ItemState> budgetAfter = snapshotBudget();
                List<String> reasons = ForecastChangeReasons.describe(budgetBefore, budgetAfter,
                        recategorized, forecastStaleAtStart);
                if (reasons.isEmpty()) {
                    view.say("Nothing that changed affects the forecast, so it does not need updating.");
                } else {
                    view.say("The forecast is out of date because:");
                    for (String reason : reasons) {
                        view.say("  - " + reason);
                    }
                }
                if (!reasons.isEmpty() && view.getYesOrNo("Do you want to update the forecast?")) {
                    try {
                        // Regenerate only what the reasons name, so a forecast spreadsheet rendered earlier
                        // keeps working for everything else.
                        forecastController.updateForecast(ForecastChangeReasons.updateScope(forecastStaleAtStart,
                                ForecastChangeReasons.changedItemIds(budgetBefore, budgetAfter), recategorized,
                                recategorizedItems));
                        forecastUpdated = true;
                       view.sayH4("The long term forecast was successfully updated.");
                    } catch (QuitException qe) {
                        throw qe;
                    } catch (Exception e) {
                        if (!view.askContinue("The error '" + e + "' occurred while updating the " +
                                "forecast.")) {
                            throw e;
                        }
                    }
                } else if (!reasons.isEmpty()) {
                   view.say("The forecast was not updated.");
                }
            }

            // Nothing changed, so there is nothing for a new render or a new set of reports to show.  Say
            // so and offer them rather than assuming:  the files on disk are the ones the last run
            // produced, and the user may still want to look at them.
            boolean renderOutput = true;
            int newlyImported = importController.getImportLog().countNewlyImported();
            if (nothingChangedThisRun(newlyImported, recategorized, balanceUpdated, skippedTransactionsProcessed,
                    externalForecastChangesImported, forecastUpdated, inSync)) {
               view.sayH2("RENDER THE LONG TERM FORECAST");
                view.say("Nothing new in this statement, and nothing else changed, so the forecast is " +
                        "unchanged since it was last rendered" +
                        ((forecast == null || forecast.getLastRenderedDate() == null) ? "" :
                                " on " + Utility.calendarDateToStringDate(forecast.getLastRenderedDate())) + ".");
                renderOutput = view.getYesOrNo("Render the forecast and the reports anyway?");
                if (!renderOutput) {
                    view.sayH4("The forecast and the reports were left as they were.");
                }
            }

            if (renderOutput) {
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
            }

            // If the user made changes to the forecast, import them.  Only worth asking when the forecast
            // was opened for them to change.
            if (renderOutput &&
                    view.getYesOrNo("Did you make any changes to the forecast in Excel that you want to import?")) {
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
            } else if (renderOutput) {
               view.say("Forecast changes not imported.");
            }

            // The reports say what the register and the forecast hold, so a run that changed neither would
            // write the same four reports out to everyone's iCloud folders again.  They are rendered on the
            // same answer as the forecast:  see nothingChangedThisRun.
            if (renderOutput) {

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

            // Check for budget item payees that differ only by capitalisation (they behave as two
            // separate items everywhere, which is almost never what was intended):
            try {
               view.sayH2("CHECKING FOR BUDGET ITEMS THAT DIFFER ONLY BY CAPITALISATION");
               List<List<Budget.CaseVariantPayee>> caseVariants = budget.checkForCaseVariantPayees();
               if (caseVariants.isEmpty()) {
                   view.sayH4("No budget item payees differ only by capitalisation.");
               } else {
                   view.say("These payees are spelled more than one way, so each spelling is a " +
                           "separate budget item with its own merchants and its own forecast line:");
                   for (List<Budget.CaseVariantPayee> variants : caseVariants) {
                       StringBuilder line = new StringBuilder("   ");
                       for (Budget.CaseVariantPayee variant : variants) {
                           if (line.length() > 3) {
                               line.append("   vs   ");
                           }
                           line.append("'").append(variant.payee()).append("'")
                                   .append(" (").append(variant.category()).append(", ")
                                   .append(variant.count())
                                   .append(variant.count() == 1 ? " item)" : " items)");
                       }
                       view.say(line.toString());
                   }
                   // Reported, not repaired.  Merging means choosing which spelling wins and moving
                   // every merchant assignment, split and forecast item behind the loser -- not a
                   // decision to take on the user's behalf in the middle of a daily update.
                   view.say("Rename or merge them from manageData when convenient.");
               }
            } catch (Exception e) {
                if (!view.askContinue("The error '" + e + "' occurred while checking for budget items " +
                        "that differ only by capitalisation.")) {
                    throw e;
                }
            }

            // Check for orphan unplanned/on-demand forecast transactions (zero remaining amount and no
            // linked split - these serve no purpose and are almost certainly left-over data):
            try {
               view.sayH2("CHECKING FOR ORPHAN UNPLANNED FORECAST TRANSACTIONS");
               List<String> orphanIds = forecast.checkForOrphanUnplannedTransactions();
               if (!orphanIds.isEmpty()) {
                   if (view.getYesOrNo("Delete these " + orphanIds.size() + " orphan forecast transaction(s)? " +
                           "They have a zero remaining amount and no linked split, so they serve no purpose.")) {
                       int deleted = forecast.deleteForecastTransactionsByIds(orphanIds);
                       view.sayH4("Deleted " + deleted + " orphan forecast transaction(s).");
                   } else {
                       view.say("Left the orphan forecast transactions in place.");
                   }
               }
               view.sayH4("Orphan unplanned forecast transaction check complete.");
            } catch (Exception e) {
                if (!view.askContinue("The error '" + e + "' occurred while checking for orphan unplanned " +
                        "forecast transactions.")) {
                    throw e;
                }
            }

            // Check for forecast transactions planned after their budget item's end date (stale projections
            // left behind when an item is given an end date - these keep expired items showing a future date):
            try {
               view.sayH2("CHECKING FOR FORECAST TRANSACTIONS AFTER THE BUDGET ITEM END DATE");
               List<String> afterEndDateIds = forecast.checkForForecastTransactionsAfterEndDate();
               if (!afterEndDateIds.isEmpty()) {
                   if (view.getYesOrNo("Delete these " + afterEndDateIds.size() + " forecast transaction(s) planned " +
                           "after their budget item's end date? They are stale projections with no linked split.")) {
                       int deleted = forecast.deleteForecastTransactionsByIds(afterEndDateIds);
                       view.sayH4("Deleted " + deleted + " forecast transaction(s) planned after the end date.");
                   } else {
                       view.say("Left the after-end-date forecast transactions in place.");
                   }
               }
               view.sayH4("After-end-date forecast transaction check complete.");
            } catch (Exception e) {
                if (!view.askContinue("The error '" + e + "' occurred while checking for forecast transactions " +
                        "after the budget item end date.")) {
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
