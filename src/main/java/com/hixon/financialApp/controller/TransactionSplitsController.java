package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.BudgetItemMerchant;
import com.hixon.financialApp.model.budget.TransactionSplit;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.ViewException;
import com.hixon.financialApp.view.base.ViewInt;

import java.util.ArrayList;
import java.util.List;

import static com.hixon.financialApp.controller.ImportController.TerminationCondition.*;

public class TransactionSplitsController {

    /*
     * Fields for SplitsController:
     */
    private ImportController.TerminationCondition terminationCondition;
    Register register;
    Budget budget;
    Forecast forecast;
    protected ViewInt view;
    protected NotificationServiceInt notificationService;


    /*
     * Getters and setters for SplitsController:
     */
    public ImportController.TerminationCondition getTerminationCondition() {
        return terminationCondition;
    }


    /**
     * Constructors and destructor for SplitsController:
     */
    public TransactionSplitsController(Register register, Budget budget, Forecast forecast, ViewInt view, NotificationServiceInt
            notificationService) {
        terminationCondition = QUIT;
        this.register = register;
        this.budget = budget;
        this.forecast = forecast;
        this.view = view;
        this.notificationService = notificationService;
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
    public void getSplits(Transaction transaction, List<TransactionSplit> splits, Merchant merchant, Budget
                                  budget,
                          List<BudgetItemMerchant> budgetItemsForMerchant, Boolean skipAllowed, Boolean inquireAllowed)
            throws Exception {

        // There should be at least one budget item.  If there isn't then throw an error:
        if (budgetItemsForMerchant.isEmpty()) {
            throw new ViewException("Must be at least one budget item assigned to a transaction to be able to get the " +
                    "splits for  it.");
        }

        // Attempt to get a balanced set of splits, or terminate as a "skip" or "inquire".  Repeat as necessary:
        boolean done = false;
        BudgetController budgetController = new BudgetController(register, budget, forecast, view, notificationService);
        while (!done) {

            // Assume we will get this done in one iteration:
            done = true;

            // Show the assigned budget items to the user:
            budgetController.showBudgetItemsForMerchant(budgetItemsForMerchant, transaction.getAmount());

            /*
             * Figure out the amounts of the splits, e.g. how much of the transaction amount to allocate to each of the
             * budget items:
             */
            // Determine if all the amounts or percentages are pre-established in the budget item:
            boolean allFixed = budgetItemsForMerchant.stream().allMatch(
                    item -> item.getAmount() > 0 || item.getPercentage() > 0);

            String[] amounts;
            String prompt = "Enter the split amounts (or a - add, d - delete, i - inquire, s - skip)";

            // If all the amounts are pre-established:
            if (allFixed) {
                // Then give the user the option to just accept the displayed amounts and percentages:
                amounts = view.getAndParseCsvLine(prompt + ", or just return to accept displayed amounts and " +
                                "percentages:", budgetItemsForMerchant.size(), true, true);
            } else {
                // the amounts are not pre-established, so ask the user to enter them:
                amounts = view.getAndParseCsvLine(prompt + ":  ", 0, false, true);
            }

            // Create the splits.  Process any user requests to edit the assigned budget items at the same time:
            // If the user entered nothing, then just accept the displayed amounts and percentages:
            if (amounts == null || amounts.length == 0) {

                // Process the fixed amounts first keeping track of the total amount assigned:
                double totalAmountAssigned = 0;
                for (BudgetItemMerchant budgetItemMerchant : budgetItemsForMerchant) {
                    if (budgetItemMerchant.getAmount() > 0) {
                        splits.add(new TransactionSplit(budgetItemMerchant.getAmount(), budgetItemMerchant,
                                transaction, null));
                        totalAmountAssigned += budgetItemMerchant.getAmount();
                    }
                }
                // Calculate the amount left to assign:
                double amountLeft = transaction.getAmount() - totalAmountAssigned;

                // Process the percentages as percentages of the amount left:
                for (BudgetItemMerchant budgetItemMerchant : budgetItemsForMerchant) {
                    if (budgetItemMerchant.getPercentage() > 0) {
                        splits.add(new TransactionSplit(budgetItemMerchant.getPercentage() / 100 * amountLeft,
                                budgetItemMerchant, transaction, null));
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
                }
            }
            // Delete one of the displayed budget items from the merchant for this transaction:
            else if (amounts[0].equalsIgnoreCase("d")) {
                try {
                    // Get the number of the budget item to delete:
                    int itemNumber = view.getNumberBetween("Enter the number of the budget item to delete:", 1,
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

                        // The user didn't enter "payee_#:".  Inform them and ask them to re-enter the values:
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

                // else if the response is a single use category:
            } else if (amounts[0].matches("[a-zA-Z][a-zA-Z0-9 '()-\\+]+")) {
                view.say("The allocate, but don't add, function has not been implemented yet.");
                //String payee = amount.substring(0, amount.indexOf(':') - 1);
                done = false;

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
            } else {
                // Allocate the splits as directed:
                boolean useEnteredAmounts = amounts.length != 1 || amounts[0].length() != 0;
                for (int i = 0; i < budgetItemsForMerchant.size(); i++) {

                    double enteredAmount = (useEnteredAmounts) ?
                            view.getDouble(amounts[i], "Must be a dollar amount.") : 0;

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
    }
}
