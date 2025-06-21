package com.hixon.financialApp.controller;

import com.hixon.financialApp.controller.ImportController.TerminationCondition;
import com.hixon.financialApp.model.budget.*;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.entity.IndependentEntity;
import com.hixon.financialApp.model.entity.MatchQuery;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.ViewException;
import com.hixon.financialApp.view.base.ViewInt;

import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static com.hixon.financialApp.controller.ImportController.TerminationCondition.QUIT;
import static com.hixon.financialApp.model.budget.BudgetItemMerchant.isBudgetItemInList;
import static com.hixon.financialApp.utility.Utility.formatDollarAmount;
import static com.hixon.financialApp.utility.Utility.stringDateDashToCalendarDate;
import static java.util.Calendar.YEAR;

/**
 * This class is the controller for budget related business logic interface.
 */
public class BudgetController {

    /*
     * Fields for BudgetController:
     */
    private ImportController.TerminationCondition terminationCondition;
    Register register;
    Budget budget;
    Forecast forecast;
    protected ViewInt view;
    protected NotificationServiceInt notificationService;


    /*
     * Getters and setters for BudgetController:
     */
    public TerminationCondition getTerminationCondition() {
        return terminationCondition;
    }


    /**
     * Constructors and destructor for BudgetController:
     */
    public BudgetController(Register register, Budget budget, Forecast forecast, ViewInt view, NotificationServiceInt
            notificationService) {
        terminationCondition = QUIT;
        this.register = register;
        this.budget = budget;
        this.forecast = forecast;
        this.view = view;
        this.notificationService = notificationService;
    }


    /**
     * Main methods for BudgetController:
     */
    /**
     * Get a budget item from the budget items in the database using full text searching.  If none of the budget items
     * in the database are what the user is looking for, then allow the user to create a new budget item.
     *
     * @param seedName a string to start the full text search with, or null
     * @throws SQLException
     * @throws EntityException
     * @throws QuitException
     * @throws SkipException
     */
    public BudgetItem getBudgetItemByNameFullText(String seedName) throws Exception, QuitException, SkipException {

        BudgetItem selectedBudgetItem = null;
        // Use the SelectionController to select a budget item from the database using natural language queries:
        SelectionController selectionController = new SelectionController(view);
        selectedBudgetItem = selectionController.getByNameFullText(
                seedName,
                budget,
                ViewInt.DO_NOT_ALLOW_NONE,
                ViewInt.ALLOW_CREATE,
                ViewInt.ALLOW_CANCEL,
                ViewInt.ALLOW_QUIT,
                ViewInt.ALLOW_SKIP,
                BudgetItem.getPrintableTypeName_static(),
                BudgetItem::getDisplayString,
                new MatchQuery(BudgetItem.getSelectQuery() + " WHERE bi.Budget_idBudget = uuid_to_bin('" +
                        budget.getId() + "') AND (endDate is null OR endDate > CURRENT_DATE) AND ", "bi.payee",
                        "bi.category, bi.payee, bi.memo"),
                rs -> {
                    try {
                        return new BudgetItem(rs);
                    } catch (BudgetException e) {
                        throw new RuntimeException(e);
                    }
                },
                (IndependentEntity budgetObj, String newName) -> new BudgetItem((Budget) budgetObj, newName));

        // If the budget item is new, then fill it out and save it:
        if (selectedBudgetItem.isDirty()) {
            selectedBudgetItem = getBudgetItemFromUser();
            selectedBudgetItem.save(EntityInt.SaveMethod.INSERT);
        }

        return selectedBudgetItem;
    }

    /**
     * Assign budget items to an existing list of budget items for a merchant.  There does not need to be any budget
     * items in the list that is provided.
     *
     * @param merchant
     * @param budgetItemsForMerchant
     * @throws Exception
     * @throws QuitException
     * @throws SkipException
     */
    public void assignBudgetItemsToMerchant(Merchant merchant, List<BudgetItemMerchant>
            budgetItemsForMerchant) throws Exception, CancelException, QuitException, SkipException {

        try {
            boolean firstTime = true;
            boolean done = false;
            int percentage = 0;
            double amount = 0.0;
            BudgetItem firstSelectedBudgetItem = null;
            BudgetItem selectedBudgetItem = null;
            while (!done) {

                // Get a budget item that the user wants to associate with this merchant:
                selectedBudgetItem = getBudgetItemByNameFullText(null);
                BudgetItemMerchant budgetItemMerchant = new BudgetItemMerchant(merchant, selectedBudgetItem);

                // then if the budget item isn't already associated with this merchant:
                if (!isBudgetItemInList(selectedBudgetItem, budgetItemsForMerchant)) {

                    // then if the user wants to add this budget item to the list of budget items for the merchant:
                    if (
                            !firstTime || // Later iterations don't make sense if we don't add them to the list:
                                    view.getYesOrNo("Do you want to add this budget item \"" +
                                            selectedBudgetItem.getPayee() + "\" to the list of budget items for the merchant \""
                                            + merchant.getName() + "\"?")
                    ) {
                        firstTime = false;

                        // then if the user wants to assign a fixed amount, or percentage, to this budget item when
                        // associated with this particular merchant:
                        boolean resp = view.getYesOrNo("Do you want to assign a fixed amount, or percentage, " +
                                "to this budget item when it is associated with this particular merchant");
                        if (resp) {
                            String input = view.getResponseString("Enter the fixed amount or percentage (e.g. 100 " +
                                    "or 10%):");
                            if (input.endsWith("%")) {
                                budgetItemMerchant.setPercentage(Integer.parseInt(input.substring(0, input.length() - 1)));
                            } else {
                                budgetItemMerchant.setAmount(Double.parseDouble(input));
                            }
                        }

                        // Associate the budget item with the merchant in the database:
                        budgetItemMerchant.save();
                    }

                    // Add the budget item to the list of budget items passed in:
                    budgetItemsForMerchant.add(budgetItemMerchant);
                } else {
                    // Tell the user that this budget item is already associated with this merchant:
                    view.say("The budget item you selected \"" + selectedBudgetItem.getPayee() + "\" is already " +
                            "associated with the merchant \"" + merchant.getName() + "\".");
                }

                // Ask the user if they are done:
                done = !view.getYesOrNo("Assign another budget item to merchant " + merchant.getName());

            } // End while there are budget items to enter.

        } catch (CancelException | QuitException | SkipException e) {
            throw e;

        } catch (Exception e) {
            ViewException ve = new ViewException("Exception occurred trying to import this transaction: " +
                    merchant + ".", e);
            throw ve;
        }
    }

    public List<TransactionSplit> assignAmountsToBudgetItems(Transaction transaction, Merchant merchant, Budget
            budget, List<BudgetItemMerchant> budgetItemMerchants)
            throws Exception {

        // If we need to ask the user to enter the splits:
        List<TransactionSplit> splits = new ArrayList<>();
        if (
            merchant.isAskAlways() || // If this is a merchant that the user wants to be asked about every time,
            (
                // or there is more than one budget item:
                (budgetItemMerchants.size() > 1) &&
                // and they are not fixed amounts:
                ((budgetItemMerchants.get(0).getAmount() == 0) && (budgetItemMerchants.get(0).getPercentage() == 0)))
        ) {
            // then ask the user to enter the splits:
            TransactionSplitsController transactionSplitsController = new TransactionSplitsController(register, budget,
                    forecast, view, notificationService);
            transactionSplitsController.getSplits(transaction, splits, merchant, budget, budgetItemMerchants, true, true);
        }
        else {
            // Track the total of the splits so that we can ensure they splits balance in the end:
            double transactionAmount = transaction.getAmount();

            // Iterate over the splits one at a time assigning amounts to each one:
            TransactionSplit transactionSplit;
            for (BudgetItemMerchant budgetItemMerchant : budgetItemMerchants
            ) {

                // If this split is for a fixed amount:
                if (budgetItemMerchant.getAmount() > 0) {
                    transactionSplit = new TransactionSplit(budgetItemMerchant.getAmount(),
                            budgetItemMerchant.getIdBudgetItem(), transaction.getId(), null);
                    transactionAmount = transactionAmount - budgetItemMerchant.getAmount();
                }
                // else if this split is for a fixed percentage of the transaction amount:
                else {
                    if (budgetItemMerchant.getPercentage() > 0) {
                        transactionSplit = new TransactionSplit(((double) budgetItemMerchant.getPercentage() /
                                100) * transaction.getAmount(), budgetItemMerchant.getIdBudgetItem(), transaction.getId(),
                                null);
                        transactionAmount = transactionAmount - ((double) budgetItemMerchant.getPercentage() /
                                100) * transaction.getAmount();
                    }
                    // else there is only one budget item, so allocate the whole transaction amount to it:
                    else {
                        transactionSplit = new TransactionSplit(transaction.getAmount(),
                                budgetItemMerchant.getIdBudgetItem(), transaction.getId(), null);
                        transactionAmount = transactionAmount - transaction.getAmount();
                    }
                }
                splits.add(transactionSplit);
            }
            if (transactionAmount != 0) {
                view.say("Automatic splits don't add up to the transaction amount, please enter them manually.");
                TransactionSplit.deleteSplitsForTransaction(transaction.getId());
                TransactionSplitsController transactionSplitsController = new TransactionSplitsController(register, budget, forecast, view, notificationService);
                transactionSplitsController.getSplits(transaction, splits, merchant, budget, budgetItemMerchants, true, true);
            }
        }
        return (splits.isEmpty()) ? null : splits;
    }

    /**
     * {@inheritDoc}
     */
    public List<String> generateDisplayableBudgetItemList(List<BudgetItem> budgetItems) throws Exception {

        view.say("The budget items are:");
        List<String> budgetItemNames = new ArrayList<>();
        for (BudgetItem budgetItem : budgetItems
        ) {
            String line = "";
            line += budgetItem.getPayee();
            line += " (";
            line += budgetItem.getCategory();
            line += ", ";
            if (budgetItem.getAmount() != 0) {
                line += Utility.formatRoundedDollarAmount(budgetItem.getAmount());
                line += " ";
            }
            line += Item.generatePeriodType(budgetItem.getPeriod());
            if (budgetItem.getPeriod() != Item.PeriodType.ON_DEMAND) {
                line += ", ";
                line += Utility.calendarDateToStringDate(
                        ForecastTransaction.getApplicableForecastTransaction(
                                budgetItem.getId(), Calendar.getInstance()).getPlannedDate());
            }
            if (budgetItem.getMemo() != null &&
                    !budgetItem.getMemo().isEmpty()) {
                line += ", " + budgetItem.getMemo();
            }
            line += ")";
            budgetItemNames.add(line);
        }
        return budgetItemNames;
    }

    public BudgetItem getBudgetItemFromUser() throws BudgetException, SQLException, EntityException, ParseException {
        // read in a new budget item for this:
        view.say("Enter the budget item in this order: category, payee, memo, period type, amount, " +
                "running balance, start date, number of payments, end date, item type, how important, " +
                "how occurs, how paid, budget name:");
        String line = view.getResponseString();
        BudgetItem budgetItem = BudgetItem.loadFromUserCSV(line);
        return budgetItem;
    }

    public Calendar getSpendingReportMonth() throws QuitException {

        view.say("\nWhat month do you want to report on?  \n" +
                "\tl - last month\n" +
                "\tt or just <enter> - this month\n" +
                "\t1 - 12 January - December in the last 12 months\n" +
                "\tSpecific month (mm-yy)\n" +
                "Enter your selection:  ");

        boolean done = false;
        Calendar month = Calendar.getInstance();
        month.set(Calendar.DATE, 1);
        while (!done) {
            done = true;
            String line = view.getResponseString();
            switch (line) {
                case "l":
                    month.add(Calendar.MONTH, -1);
                    break;

                case "t":
                case "":
                    break;

                case "1":
                case "2":
                case "3":
                case "4":
                case "5":
                case "6":
                case "7":
                case "8":
                case "9":
                case "10":
                case "11":
                case "12":
                    month.set(Calendar.MONTH, Integer.parseInt(line) - 1);

                    //  If the selected month is in the future, then change the date to that month a last year:
                    Calendar now = Calendar.getInstance();
                    if (now.compareTo(month) < 0) {
                        month.add(YEAR, -1);
                    }
                    break;

                case "quit":
                    throw new QuitException("Quitting render spending report action.");

                default:
                    try {
                        month = stringDateDashToCalendarDate(line);
                    } catch (ParseException e) {
                        view.say("Please enter l, <enter>, t, 1-12 c, or quit.");
                        done = false;
                    }
            }
        }
        return month;
    }

    /**
     * {@inheritDoc}
     */
    public BudgetItem getUserSelectedBudgetItem(List<BudgetItem> budgetItems) throws Exception {
        if (budgetItems.isEmpty()) {
            return null;
        }

        // Create a list of budget item names:
        List<String> displayableBudgetItemsList = generateDisplayableBudgetItemList(budgetItems);

        // Ask the user to select one of the budget items:
        int index = view.selectFromNumberedList("Multiple budget items found.  Please select one:",
                displayableBudgetItemsList, false);

        // Return the selected budget item:
        return budgetItems.get(index);
    }

    // Show a list of the assigned budget items for a transaction, and the amount of the transaction:
    public void showAssignedBudgetItems(List<BudgetItemMerchant> budgetItems, double amount) {

        view.say("The assigned budget items and amounts (if specified) for this merchant are:");
        int i = 1;
        for (BudgetItemMerchant budgetItem : budgetItems
        ) {
            String lineEnd = "";
            if (budgetItem.getAmount() > 0) {
                lineEnd = ", " + formatDollarAmount(budgetItem.getBudgetItem().getAmount()) + ", 0";
            } else {
                if (budgetItem.getPercentage() > 0) {
                    lineEnd = ", 0, " + budgetItem.getPercentage() + "%";
                }
            }
            view.say("   " + i++ + ".  " + budgetItem.getBudgetItem().getPayee() + lineEnd);
        }
    }

    /**
     * Displays a list of budget items and amounts (if specified) for a given merchant.
     *
     * @param budgetItemMerchants the list of assigned budget items for the merchant
     * @param amount              the amount of the transaction
     * @param relevancyScores
     * @throws Exception if an error occurs during the display process
     */
    public void showBudgetItemsForMerchant(List<BudgetItemMerchant> budgetItemMerchants, List<Double> relevancyScores,
                                            double amount) throws Exception {
        view.say("The assigned budget items and amounts (if specified) for this merchant are:");
        int i = 1;
        for (BudgetItemMerchant budgetItemMerchant : budgetItemMerchants) {
            String line = "   " + i + ".  ";
            line += budgetItemMerchant.getBudgetItem().getDisplayString();  // Using the new method
            if (budgetItemMerchant.getAmount() > 0) {
                line += ", " + Utility.formatDollarAmount(budgetItemMerchant.getAmount()) + ", 0";
            } else {
                if (budgetItemMerchant.getPercentage() > 0) {
                    line += ", 0, " + budgetItemMerchant.getPercentage() + "%";
                }
            }

            // If relevancy scores are provided, append them to the line.  If the user added a budget item to the list,
            // then there won't be a relevancy score for it, so we check if the index is valid:
            // Note: The relevancyScores list is expected to be one less than the budgetItemMerchants list
            if (relevancyScores != null && (i - 1) < relevancyScores.size() && relevancyScores.get(i - 1) != null) {
                line += ", Relevancy Score: " + relevancyScores.get(i - 1);
            }

            view.say(line);
            i++;
        }
    }

    public void renewBudgetItems(List<BudgetItemMerchant> expiredBudgetItemMerchants) throws Exception {

        // If there are more than one expired budget items:
        BudgetItem budgetItem = null;
        if (expiredBudgetItemMerchants.size() > 1) {

            // then create a list of the expired budget items:
            List <BudgetItem> expiredBudgetItems = new ArrayList<>();
            for (BudgetItemMerchant budgetItemMerchant : expiredBudgetItemMerchants) {
                expiredBudgetItems.add(budgetItemMerchant.getBudgetItem());
            }

            // and ask the user to select one:
            view.say("Multiple expired budget items found.  Please select one:");
            budgetItem = getUserSelectedBudgetItem(expiredBudgetItems);
        }

        // and if they did select one:
        if (budgetItem != null) {

            // Then un-expire it:
            budgetItem.renew();
        }
    }
}
