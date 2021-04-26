package com.hixon.financialApp.view.base;

import com.hixon.financialApp.model.budget.*;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.register.Merchant;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.model.register.TransactionSplit;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.ViewException;
import org.jetbrains.annotations.NotNull;

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

    public abstract void renderBudgetCategoryReportRow(BudgetCategoryReportRow budgetCategoryReportRow);

    public abstract void renderBudgetItemReportRow(BudgetItemReportRow budgetItemReportRow);

    public abstract void renderTransactionSplitReportRow(TransactionSplitReportRow transactionSplitReportRow) throws EntityException, SQLException, RegisterException;

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

    protected abstract void renderBudgetSummaryReportSummary(BudgetTotalsReportRow budgetTotalsReportRow);


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

        // Iterate over each item in the budget and output spending on it:
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

        // Get a list of all the items in the budget joined with their associated transaction splits and transactions:
        ResultSet budgetItemsWithSplits = BudgetItem.getBudgetItemsWithSplits(startDate, endDate);

        // If there are items in the result set:
        if (budgetItemsWithSplits.next()) {

            // Create a list of rows in the report and add all the category, budget item and transaction split rows to the list:
            BudgetTotalsReportRow budgetTotalsReportRow = new BudgetTotalsReportRow();
            List<ReportRow> reportRows = new ArrayList<>();
            addCategoriesToSummaryReport(budgetTotalsReportRow, reportRows, budgetItemsWithSplits, startDate,
                    endDate);

            // Iterate over each category, budget and transaction split rows in the list of rows and output them to the
            // report using the appropriate method for that type of row:
            BudgetCategoryReportRow budgetCategoryReportRow = null;
            BudgetItemReportRow budgetItemReportRow = null;
            TransactionSplitReportRow transactionSplitReportRow = null;
            for (ReportRow reportRow : reportRows
            ) {

                // If the current row in the report is a Budget Category:
                if (reportRow.getClass() == BudgetCategoryReportRow.class) {
                    budgetCategoryReportRow = (BudgetCategoryReportRow) reportRow;

                    // then render the budget category:
                    renderBudgetCategoryReportRow(budgetCategoryReportRow);

                } else if (reportRow.getClass() == BudgetItemReportRow.class) {
                    budgetItemReportRow = (BudgetItemReportRow) reportRow;

                    // then render the budget item:
                    renderBudgetItemReportRow(budgetItemReportRow);

                } else if (reportRow.getClass() == TransactionSplitReportRow.class) {
                    transactionSplitReportRow = (TransactionSplitReportRow) reportRow;

                    // then render the transaction split:
                    renderTransactionSplitReportRow(transactionSplitReportRow);

                } else {
                    throw new ViewException("Unrecognized report row type encounterd while generating the Budget Summary " +
                            "Report.");
                }
            }

            // Render the totals section of the report:
            renderBudgetSummaryReportSummary(budgetTotalsReportRow);

        } else {
            Utility.getResolver().say("[WARN]  There were no items in the budget to report on.");
        }

        // Render any trailer matter:
        renderSpendingReportBackMatter();

        // Close the output file:
        closeSpendingReportOutput();
        getResolver().say("Budget Summary Report successfully rendered.");
    }


    /**
     * Add the categories to the report rows list and for each category, call a method to add the budget items in that
     * category to the report rows list.
     *
     * @param budgetTotalsReportRow The totals row for the report.  This method will update it with the sum of all the
     *                              categories in the report.
     * @param reportRows A list of categories, budget item and transaction split rows tha will be included in the report.
     * @param budgetItemsWithSplits Result set on the database that contains all the information that will be included
     *                             in the report.
     * @param startDate The beginning date of the reporting period.
     * @param endDate The ending date of the reporting period.
     * @return boolean indicator that indicates if the result set from the database is exhausted.
     * @throws BudgetException
     * @throws SQLException
     */
    private boolean addCategoriesToSummaryReport(BudgetTotalsReportRow budgetTotalsReportRow, List<ReportRow> reportRows,
         ResultSet budgetItemsWithSplits, Calendar startDate, Calendar endDate) throws BudgetException, SQLException,
            RegisterException, ForecastException {

        boolean done = false;
        while (!done) {

            // Create a category object from the database row and add it to the list:
            BudgetCategory budgetCategory = new BudgetCategory(budgetItemsWithSplits);
            BudgetCategoryReportRow budgetCategoryReportRow = new BudgetCategoryReportRow(budgetCategory,
                    budgetTotalsReportRow);
            reportRows.add(budgetCategoryReportRow);

            // Add the budget items within this category to the list of rows in the report:
            done = addBudgetItems(budgetCategoryReportRow, reportRows, budgetItemsWithSplits, startDate, endDate);

            // Update the totals report row with the totals spent on this category:
            if (budgetCategory.isIncome()) {
                budgetTotalsReportRow.incrementTotalForecastIncome(budgetCategoryReportRow.getForecastAnnualAmount());
                budgetTotalsReportRow.incrementTotalPlannedIncome(budgetCategoryReportRow.getPlannedAmountInPeriod());
                budgetTotalsReportRow.incrementTotalActualIncome(budgetCategoryReportRow.getActualAmountInPeriod());
            } else {
                budgetTotalsReportRow.incrementTotalForecastSpending(budgetCategoryReportRow.getForecastAnnualAmount());
                budgetTotalsReportRow.incrementTotalPlannedSpending(budgetCategoryReportRow.getPlannedAmountInPeriod());
                budgetTotalsReportRow.incrementTotalActualSpending(budgetCategoryReportRow.getActualAmountInPeriod());
            }
        }
        return true;
    }

    /**
     * Add the budget items to the report rows list and for each budget item, call a method to add the transaction splits
     * associated with that budget item.
     *
     * @param budgetCategoryReportRow The category row for the list of budget items that we are processing.  This method
     *                                will update it with the sum of all the budget items in the category.
     * @param reportRows A list of categories, budget item and transaction split rows tha will be included in the report.
     * @param budgetItemsWithSplits Result set on the database that contains all the information that will be included
     *                             in the report.
     * @param startDate The beginning date of the reporting period.
     * @param endDate The ending date of the reporting period.
     * @return true if the result set from the database is exhausted.
     * @throws BudgetException
     * @throws SQLException
     * @throws ForecastException
     * @throws RegisterException
     */
    @NotNull
    private Boolean addBudgetItems(BudgetCategoryReportRow budgetCategoryReportRow, List<ReportRow> reportRows,
       ResultSet budgetItemsWithSplits, Calendar startDate, Calendar endDate) throws SQLException, BudgetException,
            ForecastException, RegisterException {

        // Add each budget item within the specified category to the list:
        BudgetItem budgetItem;
        boolean done = false;
        while (!done && budgetItemsWithSplits.getString(
                "bi.category").equalsIgnoreCase(budgetCategoryReportRow.getBudgetCategory().getName())) {

            // Keep track of the number of items in this category:
            budgetCategoryReportRow.incrementIncludedItems();

            // Create a budget item from the database row:
            budgetItem = new BudgetItem(budgetItemsWithSplits);

            // Keep the user updated with what is going on:
            Utility.getResolver().say("Processing budget item " + budgetItem.getCategory() + ", " +
                    budgetItem.getPayee() + ".");

            // Create a BudgetItemReportRow for this budget item and add it to the list:
            BudgetItemReportRow budgetItemReportRow = new BudgetItemReportRow(budgetItem, budgetCategoryReportRow);
            reportRows.add(budgetItemReportRow);

            // Set the total amount budgeted to spend on this budget item over the next year into the budget item row:
            budgetItemReportRow.setForecastAnnualAmount(budgetItem.getForecastAnnualAmount());

            // Set the total amount budgeted to spend on this budget item last year into the budget item row:
            budgetItemReportRow.setPlannedAmountInPeriod(budgetItem.getBudgetedAmountInPeriod(startDate, endDate));

            // Add the transaction splits to the list (which will set the total amount spent on this budget item):
            done = addTransactionSplitsToSummaryReport(budgetItemReportRow, reportRows, budgetItemsWithSplits);

            // Increment the budget category totals with the amounts from this budget item:
            budgetCategoryReportRow.incrementForecastAnnualAmount(budgetItemReportRow.getForecastAnnualAmount());
            budgetCategoryReportRow.incrementPlannedAmount(budgetItemReportRow.getPlannedAmountInPeriod());
            budgetCategoryReportRow.incrementActualAmount(budgetItemReportRow.getActualAmountInPeriod());

            // Now if there were any splits, the in the process of adding them we will have moved to the next budget
            // item (or exhausted the ResultSet), so we are already on the next budget item and we do not need to
            // advance to it.  However, if there were no transaction splits then we are still sitting on budget item
            // row that we just processed and we need to move to the next one:
            if (budgetItemReportRow.getIncludedSplits() == 0 && !budgetItemsWithSplits.next()) {
                done = true;
            }
        }
        return done;
    }


    /**
     * Add the budget items to the report rows list and for each budget item, call a method to add the transaction splits
     * associated with that budget item.
     *
     * @param budgetItemReportRow The category row for the list of budget items that we are processing.  This method
     *                                will update it with the sum of all the budget items in the category.
     * @param reportRows A list of categories, budget item and transaction split rows tha will be included in the report.
     * @param budgetItemsWithSplits Result set on the database that contains all the information that will be included
     *                             in the report.
     * @return true if the result set from the database is exhausted.
     * @throws BudgetException
     * @throws SQLException
     * @throws ForecastException
     * @throws RegisterException
     */
    @NotNull
    private boolean addTransactionSplitsToSummaryReport(BudgetItemReportRow budgetItemReportRow,
                List<ReportRow> reportRows, ResultSet budgetItemsWithSplits) throws SQLException, RegisterException {

        // The ResultSet is not exhausted:
        boolean done = false;

        // If there are any splits:
        budgetItemsWithSplits.getString("ts.idTransaction");
        if (!budgetItemsWithSplits.wasNull()) {

            // then for or each split for the current budget item:
            while (!done && budgetItemsWithSplits.getString("bi.payee").equalsIgnoreCase(
                    budgetItemReportRow.getBudgetItem().getPayee())) {

                // Create a transaction split, transaction and a Merchant from the database row:
                TransactionSplit transactionSplit = new TransactionSplit(budgetItemsWithSplits);
                Transaction transaction = new Transaction(budgetItemsWithSplits);
                Merchant merchant = new Merchant(budgetItemsWithSplits);

                // Create a TransactionSplitReportRow for this row in the ResultSet and add it to the list:
                TransactionSplitReportRow transactionSplitReportRow = new TransactionSplitReportRow(transactionSplit,
                        transaction, merchant);
                reportRows.add(transactionSplitReportRow);

                // Keep track of the number of splits in this budget item:
                budgetItemReportRow.incrementIncludedSplits();

                // Put the actual amount spent on this budget item last year into the budget item row:
                budgetItemReportRow.incrementActualAmount(transactionSplit.getAmount());

                if (!budgetItemsWithSplits.next()) {
                    done = true;
                }
            }
        }
        return done;
    }
}
