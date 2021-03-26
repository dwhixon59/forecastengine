package com.hixon.financialApp.view.base;

import com.hixon.financialApp.model.budget.*;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.model.register.TransactionSplit;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.ViewException;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static com.hixon.financialApp.utility.Utility.getResolver;
import static com.hixon.financialApp.utility.Utility.setToLastBusinessDayBefore;

public abstract class AbstractBudgetView extends AbstractView implements BudgetViewInt {

    private final Budget budget;

    public AbstractBudgetView(Budget budget) {
        this.budget = budget;
    }

    /*
     *  Helper methods:
     */
    public abstract void openSpendingReportOutput() throws FileNotFoundException, UnsupportedEncodingException, ViewException;

    public abstract void renderSpendingReportFrontMatter();

    protected abstract void renderTimePeriodRow(Calendar startDate, Calendar endDate);

    protected abstract void renderHeaderRow();

    public abstract void renderBudgetItem(BudgetItem budgetItem, Calendar startDate, Calendar endDate, double plannedAmount,
                                          double actualAmount) throws ForecastException, EntityException, BudgetException;

    public abstract void renderTransactionSplit(TransactionSplit split, boolean hide) throws EntityException, SQLException, RegisterException;

    public abstract void renderTotalRow(double budgetedIncome, double actualIncome, double budgetedSpending, double actualSpending);

    public abstract void renderSpendingReportBackMatter();

    public abstract void closeSpendingReportOutput();

    protected abstract void openBudgetSummaryReportOutput() throws FileNotFoundException, UnsupportedEncodingException, ViewException;

    protected abstract void renderBudgetSummaryReportFrontMatter();

    protected abstract void renderBudgetSummaryReportTitleRow(Calendar startDate, Calendar endDate);

    protected abstract void renderBudgetSummaryReportHeaderRow();

    protected abstract void renderBudgetSummaryReportTotalsRow(double totalBudgetedIncome, double totalActualIncome,
                                                               double totalBudgetedSpending, double totalActualSpending);


    /*
     *  Main methods:
     */

    /**
     * Create and render a spending report for a given month as an XML spreadsheet file that can be imported into a
     * spreadsheet.
     *
     * @param month The month to report on.
     * @throws FileNotFoundException
     * @throws UnsupportedEncodingException
     * @throws EntityException
     * @throws SQLException
     * @throws BudgetException
     * @throws RegisterException
     * @throws ForecastException
     * @throws ViewException
     */
    @Override
    public void renderSpendingReportForMonth(Calendar month) throws FileNotFoundException, UnsupportedEncodingException,
            EntityException, SQLException, BudgetException, RegisterException, ForecastException, ViewException {

        // Insulate the parameter from side effects:
        Calendar startDate = (Calendar) month.clone();

        // Set the start and end dates to be the last business days of the previous month and the day before the last day
        // of the requested month since that is when I get paid:
        startDate.set(Calendar.DATE, 1);
        Calendar endDate = (Calendar) month.clone();
        endDate.add(Calendar.MONTH, 1);
        setToLastBusinessDayBefore(startDate);
        setToLastBusinessDayBefore(endDate);
        endDate.add(Calendar.DATE, -1);

        // Render the report:
        renderPlannedVsActualReport(startDate, endDate);
    }


    /**
     * Create and render a planned vs. actual spending report for a given date range as an XML spreadsheet file that can
     * be imported into a spreadsheet.
     *
     * @param startDate The starting date of the reporting period.
     * @param endDate   Then ending date of the reporting period.
     * @throws FileNotFoundException
     * @throws UnsupportedEncodingException
     * @throws EntityException
     * @throws SQLException
     * @throws BudgetException
     * @throws RegisterException
     * @throws ForecastException
     * @throws ViewException
     */
    @Override
    public void renderPlannedVsActualReport(Calendar startDate, Calendar endDate)
            throws FileNotFoundException, UnsupportedEncodingException, EntityException, SQLException, BudgetException,
            RegisterException, ForecastException, ViewException {

        // Open the output and output the header:
        openSpendingReportOutput();
        renderSpendingReportFrontMatter();

        // Render the time period and header rows:
        renderTimePeriodRow(startDate, endDate);
        renderHeaderRow();

        // For each budget item in the budget:
        ResultSet rsbi = BudgetItem.getAllUnexpiredBudgetItems(startDate);
        BudgetItem budgetItem;

        // Amounts for this item in this period:
        double totalBudgetedForThisItem = 0;
        double totalActualAmountForThisItem = 0;

        // Overall income for this period:
        double totalBudgetedIncome = 0;
        double totalActualIncome = 0;

        // Overall spending for this period:
        double totalBudgetedSpending = 0;
        double totalActualSpending = 0;

        // Iterate over each item in thd budget and output spending on it:
        while (rsbi.next()) {

            // Create a budget item from the database row:
            budgetItem = new BudgetItem(rsbi);

            // Get the total amount budgeted to spend on this item in the specified perior:
            totalBudgetedForThisItem = budgetItem.getBudgetedAmountInPeriod(startDate, endDate);

            // Get all the transaction splits for the current budget item:
            List<TransactionSplit> splits = TransactionSplit.getSplitsListForBudgetItemInPeriod(budgetItem, startDate, endDate);

            // Total the splits to find out how much was spent on this budget item in the specified period.  Do this here
            // rather than call a function because we need the list of splits later on and from an efficiency perspective
            // we should only retrieve them once:
            totalActualAmountForThisItem = 0;
            for (TransactionSplit split : splits
            ) {
                totalActualAmountForThisItem += split.getAmount();
            }

            // Only include items that either occurred, or were expected to occur in the specified period:
            if (totalBudgetedForThisItem != 0 || totalActualAmountForThisItem != 0) {

                /*
                 * Keep track the total amount budgeted and spent in the period over all items for the report summary line:
                 */
                // If this budget item is an expense:
                if (budgetItem.getAmount() < 0) {

                    // then add it to the total amount budgeted to spend in the period:
                    totalBudgetedSpending -= totalBudgetedForThisItem;
                    totalActualSpending -= totalActualAmountForThisItem;

                    // else if this item is income:
                } else {

                    // then add it ot the total amount of income expected in the period:
                    totalBudgetedIncome += totalBudgetedForThisItem;
                    totalActualIncome += totalActualAmountForThisItem;
                }

                // Render the budget item:
                renderBudgetItem(budgetItem, startDate, endDate, totalBudgetedForThisItem,
                        totalActualAmountForThisItem);

                // Render the splits for the budget item:
                for (TransactionSplit split : splits
                ) {
                    renderTransactionSplit(split, false);
                }
            }
        }

        // Render the totals row:double budgetedIncome, double actualIncome, double budgetedSpending, double actualSpending
        renderTotalRow(totalBudgetedIncome, totalActualIncome, totalBudgetedSpending, totalActualSpending);

        // Render any trailer matter:
        renderSpendingReportBackMatter();

        // Close the output file:
        closeSpendingReportOutput();
        getResolver().say("MTD Spending Report successfully rendered.");
    }

    /**
     * @inheritDoc
     */
    @Override
    public void renderBudgetSummaryReport() throws FileNotFoundException, UnsupportedEncodingException, ViewException,
            EntityException, SQLException, BudgetException, ForecastException, RegisterException {

        // Open the output and output the header:
        openBudgetSummaryReportOutput();
        renderBudgetSummaryReportFrontMatter();

        // Set the period of time for reporting to be the last 12 months (ending on the last day of last month).  This
        // is so that we will get a full 12 months of actual spending.  Since this is a full year of data, it does not
        // seem important to adjust the start and end dates to be aligned with pay days.
        Calendar startDate = Calendar.getInstance();
        startDate.add(Calendar.MONTH, -12);
        startDate.set(Calendar.DATE, 1);
        Calendar endDate = Calendar.getInstance();
        endDate.add(Calendar.MONTH, -1);
        endDate.set(Calendar.DATE, endDate.getActualMaximum(Calendar.DATE));

        renderBudgetSummaryReportTitleRow(startDate, endDate);
        renderBudgetSummaryReportHeaderRow();

        // For each budget item in the budget:
        ResultSet budgetItemsWithSplits = BudgetItem.getBudgetItemsWithSplits(startDate);

        // Amounts for this item in this period:
        double totalBudgetedForThisItem = 0;
        double totalActualAmountForThisItem = 0;

        // Overall income for this period:
        double totalBudgetedIncome = 0;
        double totalActualIncome = 0;

        // Overall spending for this period:
        double totalBudgetedSpending = 0;
        double totalActualSpending = 0;

        // Iterate over each row in the item + split + transaction result set and create a list of objects:
        BudgetTotalsReportRow budgetTotalsReportRow = new BudgetTotalsReportRow();
        List<ReportRow> reportRows = new ArrayList<>();
        BudgetItem budgetItem = null;
        Boolean done = !budgetItemsWithSplits.next();
        while (!done) {

            // Create a category object from the database row and add it to the list:
            BudgetCategory budgetCategory = new BudgetCategory(budgetItemsWithSplits);
            BudgetCategoryReportRow budgetCategoryReportRow = new BudgetCategoryReportRow(budgetCategory, budgetTotalsReportRow);
            reportRows.add(budgetCategoryReportRow);

            // Add each budget item within that category to the list:
            while (budgetItemsWithSplits.getString("bi.category").equalsIgnoreCase(budgetCategory.getName())) {

                // Keep track of the number of items in this category:
                budgetCategoryReportRow.incrementIncludedItems();

                // Create a budget item from the database row:
                budgetItem = new BudgetItem(budgetItemsWithSplits);

                // Keep the user updated with what is going on:
                Utility.getResolver().say("Processing budget item " + budgetItem.getCategory() + ", " +
                        budgetItem.getPayee() + ".");

                // Create a BudgetItemReportRow for this budget item and add it to the list:
                BudgetItemReportRow budgetItemReportRow = new BudgetItemReportRow(budgetItem,budgetCategoryReportRow);
                reportRows.add(budgetItemReportRow);

                // Compute the total amount budgeted to spend on this budget item in the specified period:
                budgetItemReportRow.setPlannedAmount(budgetItem.getBudgetedAmountInPeriod(startDate, endDate));

                // Keep track of the total planned spending (or income) on this budget category:
                budgetCategoryReportRow.incrementPlannedAmount(budgetItemReportRow.getPlannedAmount());

                // Keep track of the total planned spending or income for the period:
                if (budgetItemReportRow.getBudgetItem().isIncome()) {
                    budgetTotalsReportRow.incrementTotalBudgetedIncome(budgetItemReportRow.getPlannedAmount());
                } else {
                    budgetTotalsReportRow.incrementTotalBudgetedSpending(budgetItemReportRow.getPlannedAmount());
                }

                // Add all the transaction splits for the current budget item:
                while (!done && budgetItemsWithSplits.getString("bi.payee").equalsIgnoreCase(
                        budgetItem.getPayee())) {

                    // Keep track of the number of splits in this budget item:
                    budgetItemReportRow.incrementIncludedSplits();

                    // Create a transaction split and a transaction from the database row:
                    TransactionSplit transactionSplit = new TransactionSplit(budgetItemsWithSplits);
                    Transaction transaction = new Transaction(budgetItemsWithSplits);

                    // Create a TransactionSplitReportRow for this row in the ResultSet and add it to the list:
                    TransactionSplitReportRow transactionSplitReportRow = new TransactionSplitReportRow(transactionSplit,
                            transaction);
                    reportRows.add(budgetItemReportRow);

                    // Keep track of the total planned spending on this budget category:
                    budgetCategoryReportRow.incrementActualAmount(transactionSplitReportRow.getTransactionSplit().getAmount());

                    // Keep track of the total actual spending or income for the period:
                    if (budgetItemReportRow.getBudgetItem().isIncome()) {
                        budgetTotalsReportRow.incrementTotalActualSpending(budgetItemReportRow.getActualAmount());
                    } else {
                        budgetTotalsReportRow.incrementTotalActualIncome(budgetItemReportRow.getActualAmount());
                    }

                    if (!budgetItemsWithSplits.next()) {
                        done = true;
                    }
                }
            }
        }

        // Iterate over each item in the budget and output spending on it:
        while (budgetItemsWithSplits.next()) {

            // Only include items that either occurred, or were expected to occur in the specified period:
            if (totalBudgetedForThisItem != 0 || totalActualAmountForThisItem != 0) {

                // Render the budget item:
                renderBudgetItem(budgetItem, startDate, endDate, totalBudgetedForThisItem,
                        totalActualAmountForThisItem);

                // Render the splits for the budget item:
//                for (TransactionSplit split : splits
//                ) {
//                    renderTransactionSplit(split, true);
//                }
            }

            // Create a budget item from the database row:
            budgetItem = new BudgetItem(budgetItemsWithSplits);

            // Keep the user updated with what is going on:
            Utility.getResolver().say("Processing budget item " + budgetItem.getCategory() + ", " +
                    budgetItem.getPayee() + ".");

            // Get the total amount budgeted to spend on this item in the specified perior:
            totalBudgetedForThisItem = budgetItem.getBudgetedAmountInPeriod(startDate, endDate);

            // Get all the transaction splits for the current budget item:
            List<TransactionSplit> splits = TransactionSplit.getSplitsListForBudgetItemInPeriod(budgetItem, startDate, endDate);

            // Total the splits to find out how much was spent on this budget item in the specified period.  Do this here
            // rather than call a function because we need the list of splits later on and from an efficiency perspective
            // we should only retrieve them once:
            totalActualAmountForThisItem = 0;
            for (TransactionSplit split : splits
            ) {
                totalActualAmountForThisItem += split.getAmount();
            }

            // Only include items that either occurred, or were expected to occur in the specified period:
            if (totalBudgetedForThisItem != 0 || totalActualAmountForThisItem != 0) {

                /*
                 * Keep track the total amount budgeted and spent in the period over all items for the report summary line:
                 */
                // If this budget item is an expense:
                if (budgetItem.getAmount() < 0) {

                    // then add it to the total amount budgeted to spend in the period:
                    totalBudgetedSpending -= totalBudgetedForThisItem;
                    totalActualSpending -= totalActualAmountForThisItem;

                    // else if this item is income:
                } else {

                    // then add it ot the total amount of income expected in the period:
                    totalBudgetedIncome += totalBudgetedForThisItem;
                    totalActualIncome += totalActualAmountForThisItem;
                }

                // Render the budget item:
                renderBudgetItem(budgetItem, startDate, endDate, totalBudgetedForThisItem,
                        totalActualAmountForThisItem);

                // Render the splits for the budget item:
                for (TransactionSplit split : splits
                ) {
                    renderTransactionSplit(split, true);
                }
            }
        }

        // Render the totals row:double budgetedIncome, double actualIncome, double budgetedSpending, double actualSpending
        renderBudgetSummaryReportTotalsRow(totalBudgetedIncome, totalActualIncome, totalBudgetedSpending, totalActualSpending);

        // Render any trailer matter:
        renderSpendingReportBackMatter();

        // Close the output file:
        closeSpendingReportOutput();
        getResolver().say("Budget Summary Report successfully rendered.");

    }
}
