package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.*;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.ViewException;
import com.hixon.financialApp.view.base.ViewInt;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static com.hixon.financialApp.controller.TerminationCondition.*;
import static com.hixon.financialApp.view.base.ViewInt.*;

public class TransactionSplitsController {

    /*
     * Fields for SplitsController:
     */
    private TerminationCondition terminationCondition;
    protected SessionController sessionController;
    protected Register register;
    protected Budget budget;
    protected Forecast forecast;
    protected ViewInt view;
    protected NotificationServiceInt notificationService;


    /*
     * Getters and setters for SplitsController:
     */
    public TerminationCondition getTerminationCondition() {
        return terminationCondition;
    }


    /**
     * Constructor for TransactionSplitsController with SessionController.
     *
     * @param sessionController The session controller for accessing register, budget, and forecast information
     */
    public TransactionSplitsController(SessionController sessionController) {
        terminationCondition = QUIT;
        this.sessionController = sessionController;
        this.register = sessionController.getRegister();
        this.budget = sessionController.getBudget();
        this.forecast = sessionController.getForecast();
        this.view = sessionController.getView();
        this.notificationService = sessionController.getNotificationService();
    }

    /**
     * Interact with the user to confirm or override the budget item amounts and then create splits for them.  Allow the
     * user to add new budget items and create splits for them as well.
     *
     * @param transaction            The transaction to get the splits for.
     * @param splits                 A list of splits that this function will add the splits to.
     * @param merchant               The merchant associated with this transaction.
     * @param budgetItemsForMerchant The budget items associated with the specified merchant.
     * @param skipAllowed            Is the user allowed to skip assigning splits to this transaction?
     * @param inquireAllowed         Is the user allowed to send an inquiry for clarification of this transaction?
     * @throws Exception If there is a problem with the input or the database.
     */
    public void getSplits(Transaction transaction, List<TransactionSplit> splits, Merchant merchant, Budget budget,
        List<BudgetItemMerchant> budgetItemsForMerchant, Boolean skipAllowed, Boolean inquireAllowed) throws Exception {

        // There should be at least one budget item.  If there isn't then throw an error:
        if (budgetItemsForMerchant.isEmpty()) {
            throw new ViewException("Must be at least one budget item assigned to a transaction to be able to get the " +
                    "splits for  it.");
        }

        // Calculate relevancy scores for each budget item based on the transaction
        List<Double> relevancyScores = calculateRelevancyScores(budgetItemsForMerchant, transaction);

        // Sort budget items by relevancy score in descending order (highest scores first)
        sortByRelevancyScore(budgetItemsForMerchant, relevancyScores);

        // Attempt to get a balanced set of splits, or terminate as a "skip" or "inquire".  Repeat as necessary:
        boolean done = false;
        BudgetController budgetController = new BudgetController(sessionController);
        while (!done) {

            // Assume we will get this done in one iteration:
            done = true;

            // Show the assigned budget items to the user:
            budgetController.showBudgetItemsForMerchant(budgetItemsForMerchant, relevancyScores, transaction.getAmount());

            /*
             * Figure out the amounts of the splits, e.g. how much of the transaction amount to allocate to each of the
             * budget items:
             */
            // Determine if all the amounts or percentages are pre-established in the budget item:
            boolean allFixed = budgetItemsForMerchant.stream().allMatch(
                    item -> item.getAmount() > 0 || item.getPercentage() > 0);

            // ── Single-item shortcut ───────────────────────────────────────────────
            // When there is exactly one budget item and the merchant still prompts
            // each time (askAlways=true), replace the full split-entry prompt with a
            // focused memo-only prompt.  The item is auto-selected; the user just
            // provides a memo (or presses Enter to accept the budget item's own memo
            // as the default).  Typing 'a' adds a budget item and re-enters the
            // normal multi-item loop on the next iteration; 's' skips the transaction.
            // This shortcut is skipped when amounts are pre-fixed — pressing Enter is
            // already optimal in that path.
            if (budgetItemsForMerchant.size() == 1 && merchant.isAskAlways() && !allFixed) {
                BudgetItemMerchant sole = budgetItemsForMerchant.get(0);
                String defaultMemo = getBudgetItemMemo(sole);
                String itemLabel;
                try {
                    itemLabel = sole.getBudgetItem().getPayee();
                } catch (Exception e) {
                    itemLabel = "item 1";
                }
                view.say("▸ Auto-selected: " + itemLabel);
                String memoInput = view.getResponseString(
                        "Memo (or 'a' to add a budget item, 's' to skip):",
                        defaultMemo, ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                        ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);

                if (memoInput != null && memoInput.equalsIgnoreCase("a")) {
                    try {
                        budgetController.assignBudgetItemsToMerchant(merchant, budgetItemsForMerchant);
                        done = false;  // re-loop to show updated list with the new item
                    } catch (SkipException se) {
                        view.say("Skipping this transaction.");
                        terminationCondition = SKIP;
                        // done stays true — loop will exit
                    } catch (CancelException ce) {
                        // User cancelled adding a budget item — return to the prompt without aborting the import.
                        view.say("Cancelled adding a budget item.");
                        done = false;
                    }
                    continue;
                } else if (memoInput != null && memoInput.equalsIgnoreCase("s")) {
                    if (skipAllowed) {
                        view.say("Skipping this transaction.");
                        terminationCondition = SKIP;
                    } else {
                        view.say("Skip not allowed at this time.");
                        done = false;
                    }
                    continue;
                }

                String memo = (memoInput == null || memoInput.isBlank()) ? null : memoInput;
                splits.add(new TransactionSplit(transaction.getAmount(), sole, transaction, memo));
                // done stays true — fall through to the "Always auto-assign?" question
                continue;
            }
            // ── End single-item shortcut ───────────────────────────────────────────

            String[] amounts;
            String prompt = "Enter the split amounts (or a - add, d - delete, i - inquire, s - skip)";

            // If all the amounts are pre-established:
            if (allFixed) {
                // Then give the user the option to just accept the displayed amounts and percentages:
                amounts = view.getAndParseCsvLine(prompt + ", or just return to accept displayed amounts and " +
                                "percentages:", budgetItemsForMerchant.size(), true, true);
            } else {
                // the amounts are not pre-established, so ask the user to enter them:
                amounts = view.getAndParseCsvLine(prompt, 0, false, true);
            }

            // Create the splits.  Process any user requests to edit the assigned budget items at the same time:
            // If the user entered nothing, then just accept the displayed amounts and percentages:
            if (amounts == null || amounts.length == 0) {

                // Process the fixed amounts first keeping track of the total amount assigned:
                double totalAmountAssigned = 0;
                for (BudgetItemMerchant budgetItemMerchant : budgetItemsForMerchant) {
                    if (budgetItemMerchant.getAmount() > 0) {
                        // Enhancement 5: default split memo from the budget item's memo field
                        String defaultMemo = getBudgetItemMemo(budgetItemMerchant);
                        splits.add(new TransactionSplit(budgetItemMerchant.getAmount(), budgetItemMerchant,
                                transaction, defaultMemo));
                        totalAmountAssigned += budgetItemMerchant.getAmount();
                    }
                }
                // Calculate the amount left to assign:
                double amountLeft = transaction.getAmount() - totalAmountAssigned;

                // Process the percentages as percentages of the amount left:
                for (BudgetItemMerchant budgetItemMerchant : budgetItemsForMerchant) {
                    if (budgetItemMerchant.getPercentage() > 0) {
                        String defaultMemo = getBudgetItemMemo(budgetItemMerchant);
                        splits.add(new TransactionSplit(budgetItemMerchant.getPercentage() / 100 * amountLeft,
                                budgetItemMerchant, transaction, defaultMemo));
                    }
                }
            }

            // Add a new budget item to the current Merchant:
            else if (amounts[0].equalsIgnoreCase("a")) {
                try {
                    budgetController.assignBudgetItemsToMerchant(merchant, budgetItemsForMerchant);
                    done = false;
                } catch (SkipException se) {
                    view.say("Skipping this transaction.");
                    terminationCondition = SKIP;
                } catch (CancelException ce) {
                    // User cancelled adding a budget item — return to the prompt without aborting the import.
                    view.say("Cancelled adding a budget item.");
                    done = false;
                }
            }
            // Delete one of the displayed budget items from the merchant for this transaction:
            else if (amounts[0].equalsIgnoreCase("d")) {
                try {
                    // Get the number of the budget item to delete:
                    int itemNumber = view.getResponseIntBetween("Enter the number of the budget item to delete:", 1,
                            budgetItemsForMerchant.size(), true, true, true);

                    BudgetItemMerchant.deleteBudgetItemFromMerchant(budgetItemsForMerchant.get(itemNumber - 1));
                    budgetItemsForMerchant.remove(itemNumber - 1);
                    done = false;
                } catch (SkipException se) {
                    view.say("Skipping this transaction.");
                    terminationCondition = SKIP;
                }
                done = false;

            // Send an inquiry to someone as to how to categorize this transaction:
            } else if (amounts[0].equalsIgnoreCase("i")) {
                if (inquireAllowed) {
                    view.say("Sending an inquiry.");
                    terminationCondition = INQUIRE;
                } else {
                    view.say("Inquiry function not allowed at this time.");
                    done = false;
                }

            // Skip this transaction for now:
            } else if (amounts[0].equalsIgnoreCase("s")) {
                if (skipAllowed) {
                    view.say("Skipping this transaction.");
                    terminationCondition = SKIP;
                } else {
                    view.say("Skip not allowed at this time.");
                    done = false;
                }

            // Create the splits from a sparse list of categories entered by the user as "payee_#:amount":
            } else if (amounts[0].matches("^[1-9][0-9]*\\s*:(.*)")) {
                // For each of the payee:amount combinations entered by the user:
                List<Integer> evenRemainders = new ArrayList<>();
                List<Integer> apportionedRemainders = new ArrayList<>();
                List<Integer> addTaxItems = new ArrayList<>();
                for (int i = 0; i < amounts.length; i++) {

                    // Remove leading and trailing blanks from the amount:
                    amounts[i] = amounts[i].trim();

                    // Validate that the current amount is indeed a sparse list amount:
                    if (!amounts[i].matches("^[1-9][0-9]*\\s*:(.*)")) {

                        // The user didn't enter "split#:".  Inform them and ask them to re-enter the values:
                        view.say("The amount " + amounts[i] + " does not start with a number followed by " +
                                "a colon.  Please re-enter the values");
                        done = false;
                        break;
                    }

                    // Get the number of the budget item from the user entered value:
                    String itemNumberString = amounts[i].substring(0, amounts[i].indexOf(':')).trim();
                    int itemNumber = 0;
                    try {
                        itemNumber = Integer.parseInt(itemNumberString);
                        if (itemNumber <= 0 || itemNumber > budgetItemsForMerchant.size()) {
                            throw new NumberFormatException();
                        }

                    } catch (NumberFormatException nfe) {

                        // The user didn't enter a valid integer.  Inform them and ask them to re-enter the values:
                        view.say("The payee number " + itemNumberString + "is not a valid number from the " +
                                "list.  " + nfe.getMessage() + "  Please re-enter the values");
                        done = false;
                        break;
                    }

                    // If the user specified payee number is not in the list of payees:
                    if (itemNumber <= 0 || itemNumber > budgetItemsForMerchant.size()) {

                        // Then inform the user and ask them to re-enter the values:
                        view.say("The payee number " + itemNumberString + "is not in the list.  Please " +
                                "re-enter the values");
                        done = false;
                        break;
                    }

                    // Get the amount to be assigned to the transaction split for this payee:
                    String itemAmountString = amounts[i].substring(amounts[i].indexOf(':') + 1).trim();

                    // If there is a memo after the amount:
                    String memo = null;
                    if (itemAmountString.contains(" ")) {

                        // then copy it into the memo variable:
                        memo = itemAmountString.substring(itemAmountString.indexOf(" ") + 1).trim();

                        // and remove it from the item amount string:
                        itemAmountString = itemAmountString.substring(0, itemAmountString.indexOf(" "));
                    }

                    // Assign the amount to the split:
                    double itemAmount = 0;

                    // If the amount is a remainder split:
                    if (itemAmountString.substring(itemAmountString.length() - 1).equalsIgnoreCase("e")) {

                        // then add this item to the even remainders list:
                        evenRemainders.add(i);

                        // and trim the 'e' off the end of the amount:
                        itemAmountString = itemAmountString.substring(0, itemAmountString.length() - 1);

                    } // else if the amount is an apportionment split:
                    else if (itemAmountString.substring(itemAmountString.length() - 1).equalsIgnoreCase("a")) {

                        // then add this item to the apportionment list:
                        apportionedRemainders.add(i);

                        // and trim the 'a' off the end of the amount:
                        itemAmountString = itemAmountString.substring(0, itemAmountString.length() - 1);

                    } // else if we are supposed to add tax to the amount:
                    else if (itemAmountString.substring(itemAmountString.length() - 1).equalsIgnoreCase("t")) {

                        // then add this item to the add tax list:
                        addTaxItems.add(i);

                        // and trim the 't' off the end of the amount:
                        itemAmountString = itemAmountString.substring(0, itemAmountString.length() - 1);

                    }

                    // If there is anything in the amount string assume it is an amount to be assigned to this item:
                    if (itemAmountString.length() > 0) {

                        // Convert the amount string to a number:
                        try {
                            itemAmount = Utility.parseDollarAmount(itemAmountString);

                        } catch (NumberFormatException nfe) {

                            // The user didn't enter a valid dollar amount.  Inform them and ask them to re-enter the values:
                            view.say("The item amount " + itemAmountString + "is not a valid dollar amount.  " +
                                    nfe.getMessage() + "  Please re-enter the values");
                            done = false;
                            break;
                        }
                    }

                    // Add a split for this budget item:
                    splits.add(new TransactionSplit(itemAmount, budgetItemsForMerchant.get(itemNumber - 1),
                            transaction, memo));
                }

                // If there was an error in the format of the sparse category string, the have the user re-enter it:
                if (!done) {
                    splits.clear();
                    continue;
                }

                // If there is a remainder to split evenly or apportion across the items, or we need to add tax:
                if (evenRemainders.size() > 0 || apportionedRemainders.size() > 0 || addTaxItems.size() > 0) {
                    TransactionSplit.splitRemainder(transaction.getAmount(), evenRemainders, apportionedRemainders,
                            addTaxItems, splits);
                }

                // Verify that the amounts from the sparse list of payees add up to the transaction total:
                double totalSplitsAmount = 0;
                for (TransactionSplit split : splits) {
                    totalSplitsAmount += split.getAmount();
                }
                if (!Utility.isEqualCurrency(transaction.getAmount(), totalSplitsAmount)) {

                    // The user didn't enter a valid dollar amount.  Inform them and ask them to re-enter the values:
                    view.say("The total of the list of splits entered (" + totalSplitsAmount + ") does not " +
                            "equal the amount of the transaction (" + transaction.getAmount() + ").    Please re-enter the " +
                            "values");
                    splits.clear();
                    done = false;
                    continue;
                }

                // E10: Text input — use the entered string as a budget-item search seed.
                // The user can find and pick a budget item without having to first type 'a'.
            } else if (amounts[0].matches("[a-zA-Z][a-zA-Z0-9 '()-\\+]+")) {
                try {
                    view.say("Searching for budget item matching '" + amounts[0] + "'...");
                    BudgetItem selectedBudgetItem = budgetController.getBudgetItemByNameFullText(amounts[0]);

                    if (selectedBudgetItem != null) {
                        // Ask whether to permanently associate this budget item with the merchant,
                        // or use it just once for this transaction.
                        String associate = view.getResponseString(
                                "Permanently associate '" + selectedBudgetItem.getDisplayString() +
                                        "' with merchant '" + merchant.getName() + "'? (y/n) [n]:",
                                "n", ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                                ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);

                        if (associate.equalsIgnoreCase("y")) {
                            // Add to merchant permanently and re-display updated budget items
                            budgetController.assignBudgetItemsToMerchant(merchant, budgetItemsForMerchant);
                            done = false;  // Re-loop to show updated list and ask for amounts
                        } else {
                            // One-time use: create a transient BudgetItemMerchant (unsaved) and add split
                            BudgetItemMerchant tempBim = new BudgetItemMerchant(merchant, selectedBudgetItem);
                            splits.add(new TransactionSplit(transaction.getAmount(), tempBim, transaction, null));
                        }
                    }
                } catch (CancelException | SkipException e) {
                    throw e;
                } catch (QuitException e) {
                    throw e;
                } catch (Exception e) {
                    view.say("Error searching for budget item: " + e.getMessage());
                    done = false;
                }

                // else if the response is a number selection and a memo:
            } else if (amounts[0].matches("^[1-9][0-9]*[\\s]+[^,]*") && amounts.length == 1) {
                String itemNumberString = amounts[0].substring(0, amounts[0].indexOf(' '));
                int itemNumber = Integer.parseInt(itemNumberString);
                String memo = amounts[0].substring(amounts[0].indexOf(' ') + 1);
                if (itemNumber <= budgetItemsForMerchant.size()) {
                    splits.add(new TransactionSplit(transaction.getAmount(), budgetItemsForMerchant.get(itemNumber - 1),
                            transaction, memo));
                }

                // else if the response is just a number selection:
            } else if (amounts[0].matches("[1-9][0-9]*") && amounts.length == 1) {
                int itemNumber = Integer.parseInt(amounts[0]);
                if (itemNumber <= budgetItemsForMerchant.size()) {
                    splits.add(new TransactionSplit(transaction.getAmount(), budgetItemsForMerchant.get(itemNumber - 1),
                            transaction, null));
                }
            } else if (!amounts[0].matches("^-?[0-9]+(\\.[0-9]+)?$")) {
                // Invalid input - not a valid command or number
                view.say("Invalid input: '" + amounts[0] + "'. Valid options are:");
                view.say("  • a - add a new budget item");
                view.say("  • d - delete a budget item");
                view.say("  • i - inquire (send for review)");
                view.say("  • s - skip this transaction");
                view.say("  • A number (1-" + budgetItemsForMerchant.size() + ") to select a budget item for the whole amount");
                view.say("  • '<number> <memo>' to select a budget item and add a memo (e.g. '3 birthday gift')");
                view.say("  • Text to search for a budget item by name (e.g. 'groceries')");
                view.say("  • Comma-separated amounts to split across ALL listed budget items in order");
                view.say("      (e.g. '-30.00, -20.00' for the first two items)");
                view.say("  • '<number>:<amount>' pairs (comma-separated) to assign amounts to specific items");
                view.say("      (e.g. '1:-50.00, 3:-25.00 dog treats' — text after the amount becomes the memo)");
                view.say("  • Amount suffixes for '<number>:<amount>' pairs:");
                view.say("      e = give this item everything else (the remainder), split evenly if used on more than one item");
                view.say("          (e.g. '1:-17.50, 3:e' assigns the leftover to item 3)");
                view.say("      a = give this item an apportioned (proportional) share of the remainder");
                view.say("      t = add sales tax to this item's amount");
                view.say("Please try again.");
                done = false;
            } else {
                // Allocate the splits as directed:
                boolean useEnteredAmounts = amounts.length != 1 || amounts[0].length() != 0;
                for (int i = 0; i < budgetItemsForMerchant.size(); i++) {

                    double enteredAmount = (useEnteredAmounts) ?
                            view.getResponseDouble(amounts[i]) : 0;

                    // Don't create a split if the user entered zero for this budget item:
                    if (!useEnteredAmounts || enteredAmount != 0) {

                        // If the splits are not based on percentages, then use amounts:
                        if (budgetItemsForMerchant.get(i).getPercentage() == 0) {
                            splits.add(new TransactionSplit(
                                    (useEnteredAmounts) ? enteredAmount : budgetItemsForMerchant.get(i).getAmount(),
                                    budgetItemsForMerchant.get(i), transaction,
                                    null)
                            );
                        } else  // use the percentages:
                        {
                            splits.add(new TransactionSplit((useEnteredAmounts) ?
                                    (Integer.parseInt(amounts[i]) / 100) * transaction.getAmount() :
                                    (budgetItemsForMerchant.get(i).getPercentage() / 100) * transaction.getAmount(),
                                    budgetItemsForMerchant.get(i), transaction, null)
                            );
                        }
                    }
                }
            }
        }

        // Enhancement 5: After successful split assignment, offer to lock this merchant
        // into auto-assign mode when merchant has askAlways=true and exactly one budget item.
        if (!splits.isEmpty() && merchant.isAskAlways() && budgetItemsForMerchant.size() == 1) {
            try {
                String setDefault = view.getResponseString(
                        "Always auto-assign '" + merchant.getName() + "' to this budget item? (y/n):",
                        "n", ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                        ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);
                if (setDefault.equalsIgnoreCase("y")) {
                    merchant.setAskAlways(false);
                    merchant.save();
                    view.say("✓ '" + merchant.getName() + "' will be auto-assigned silently next time.");
                }
            } catch (CancelException | QuitException | SkipException e) {
                // User cancelled the "set as default" prompt — continue without changing
            }
        }
    }

    /**
     * Enhancement 5: Returns the memo from the budget item associated with the given BudgetItemMerchant,
     * or null if the budget item has no memo or cannot be retrieved.
     *
     * @param budgetItemMerchant the budget item merchant association
     * @return the budget item's memo, or null
     */
    private String getBudgetItemMemo(BudgetItemMerchant budgetItemMerchant) {
        try {
            BudgetItem budgetItem = budgetItemMerchant.getBudgetItem();
            if (budgetItem != null) {
                String memo = budgetItem.getMemo();
                return (memo != null && !memo.isBlank()) ? memo : null;
            }
        } catch (Exception e) {
            // Silently ignore — memo defaulting is a best-effort feature
        }
        return null;
    }

    /**
     * Calculate relevancy scores for budget items based on how well they match the transaction.
     * Scores are based on:
     * 1. Amount similarity (0-50 points): How close the transaction amount is to the budget item amount
     * 2. Period match (0-30 points): Whether the transaction date aligns with the budget item's period
     * 3. Category priority (0-20 points): Based on item importance and type
     *
     * @param budgetItemsForMerchant List of budget items to score
     * @param transaction The transaction to match against
     * @return List of relevancy scores (0.0 to 100.0) corresponding to each budget item
     */
    private List<Double> calculateRelevancyScores(List<BudgetItemMerchant> budgetItemsForMerchant,
                                                    Transaction transaction) {
        List<Double> scores = new ArrayList<>();
        double transactionAmount = Math.abs(transaction.getAmount());
        Calendar transactionDate = transaction.getDate();

        for (BudgetItemMerchant budgetItemMerchant : budgetItemsForMerchant) {
            double score = 0.0;
            BudgetItem budgetItem = budgetItemMerchant.getBudgetItem();

            // 1. Amount Similarity Score (0-60 points) - increased weight
            double budgetItemAmount = Math.abs(budgetItem.getAmount());
            if (budgetItemAmount > 0) {
                double amountDifference = Math.abs(transactionAmount - budgetItemAmount);
                double percentDifference = amountDifference / Math.max(transactionAmount, budgetItemAmount);

                // Perfect match = 60 points, decreasing as difference increases
                // 0% difference = 60, 10% = 54, 25% = 45, 50% = 30, 100%+ = 0
                score += Math.max(0, 60 * (1 - percentDifference));
            } else {
                // On-demand items get a baseline score of 30
                score += 30;
            }

            // 2. Period/Date Proximity Score (0-20 points) - based on distance from next expected occurrence
            if (budgetItem.getPeriod() != null && budgetItem.getStartDate() != null) {
                try {
                    // Determine the period length in days
                    int periodDays = getPeriodDays(budgetItem.getPeriod());

                    if (periodDays > 0) {
                        // Calculate days between transaction date and budget start date
                        long transactionTime = transactionDate.getTimeInMillis();
                        long startTime = budgetItem.getStartDate().getTimeInMillis();
                        long daysSinceStart = (transactionTime - startTime) / (1000 * 60 * 60 * 24);

                        // Find the next expected occurrence (could be in past, present, or future)
                        long periodsPassed = daysSinceStart / periodDays;
                        long daysIntoCurrentPeriod = daysSinceStart % periodDays;

                        // Calculate distance to nearest expected occurrence (either current or next period)
                        long daysToNextOccurrence = (daysIntoCurrentPeriod <= periodDays / 2)
                            ? -daysIntoCurrentPeriod  // Closer to current period's occurrence (negative = past)
                            : periodDays - daysIntoCurrentPeriod;  // Closer to next period's occurrence (positive = future)

                        long absDaysFromExpected = Math.abs(daysToNextOccurrence);

                        // Scoring based on absolute distance from expected date
                        // 0-3 days = 20 points, 4-7 days = 15 points, 8-14 days = 10 points,
                        // 15-30 days = 5 points, >30 days = 0 points
                        double proximityScore;
                        if (absDaysFromExpected <= 3) {
                            proximityScore = 20.0;
                        } else if (absDaysFromExpected <= 7) {
                            // Linear scale from 20 to 15 points
                            proximityScore = 20.0 - ((absDaysFromExpected - 3) * 1.25);
                        } else if (absDaysFromExpected <= 14) {
                            // Linear scale from 15 to 10 points
                            proximityScore = 15.0 - ((absDaysFromExpected - 7) * 0.714);
                        } else if (absDaysFromExpected <= 30) {
                            // Linear scale from 10 to 5 points
                            proximityScore = 10.0 - ((absDaysFromExpected - 14) * 0.3125);
                        } else {
                            // Exponential decay after 30 days
                            proximityScore = 5.0 * Math.exp(-(absDaysFromExpected - 30) / 30.0);
                        }

                        score += Math.max(0, proximityScore);
                    } else {
                        // On-demand items get baseline score
                        score += 10;
                    }
                } catch (Exception e) {
                    // If date calculation fails, give neutral score
                    score += 10;
                }
            } else {
                // Items without period info get baseline score
                score += 10;
            }

            // 3. Category Priority Score (0-20 points)
            // More important items rank higher
            if (budgetItem.getHowImportant() != null) {
                switch (budgetItem.getHowImportant()) {
                    case VARIABLE_ESSENTIAL:
                        score += 20;
                        break;
                    case FIXED_ESSENTIAL:
                        score += 18;
                        break;
                    case DISCRETIONARY_ESSENTIAL:
                        score += 15;
                        break;
                    case VARIABLE_NONESSENTIAL:
                        score += 12;
                        break;
                    case FIXED_NONESSENTIAL:
                        score += 8;
                        break;
                    case DISCRETIONARY_NONESSENTIAL:
                        score += 5;
                        break;
                    default:
                        score += 10;
                }
            } else {
                score += 10;
            }

            // Ensure score is in valid range
            score = Math.max(0, Math.min(100, score));
            scores.add(score);
        }

        return scores;
    }

    /**
     * Sort budget items by their relevancy scores in descending order (highest scores first).
     * Sorts both the budget items list and scores list in parallel to maintain correspondence.
     *
     * @param budgetItemsForMerchant List of budget items to sort
     * @param scores List of relevancy scores corresponding to the budget items
     */
    private void sortByRelevancyScore(List<BudgetItemMerchant> budgetItemsForMerchant, List<Double> scores) {
        // Create a list of indices for sorting
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < scores.size(); i++) {
            indices.add(i);
        }

        // Sort indices by scores in descending order
        indices.sort((i1, i2) -> Double.compare(scores.get(i2), scores.get(i1)));

        // Create temporary lists with sorted order
        List<BudgetItemMerchant> sortedItems = new ArrayList<>();
        List<Double> sortedScores = new ArrayList<>();
        for (Integer index : indices) {
            sortedItems.add(budgetItemsForMerchant.get(index));
            sortedScores.add(scores.get(index));
        }

        // Replace original lists with sorted versions
        budgetItemsForMerchant.clear();
        budgetItemsForMerchant.addAll(sortedItems);
        scores.clear();
        scores.addAll(sortedScores);
    }

    /**
     * Get the number of days in a period type.
     *
     * @param period The period type
     * @return Number of days, or 0 for on-demand
     */
    private int getPeriodDays(Item.PeriodType period) {
        if (period == null) return 0;

        switch (period) {
            case DAILY:
                return 1;
            case WEEKLY:
                return 7;
            case BIWEEKLY:
                return 14;
            case THREE_WEEKS:
                return 21;
            case FOUR_WEEKS:
                return 28;
            case SEMIMONTHLY:
                return 15;
            case SCHOOL_YEAR_SEMIMONTHLY:
                return 15;
            case MONTHLY:
                return 30;
            case SIX_WEEKS:
                return 42;
            case BIMONTHLY:
                return 60;
            case QUARTERLY:
                return 90;
            case FOUR_MONTHS:
                return 120;
            case SEMIANNUALLY:
                return 182;
            case ANNUALLY:
                return 365;
            case ON_DEMAND:
            default:
                return 0;
        }
    }
}
