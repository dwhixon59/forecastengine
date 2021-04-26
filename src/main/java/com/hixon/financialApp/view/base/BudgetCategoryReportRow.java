package com.hixon.financialApp.view.base;

import com.hixon.financialApp.model.budget.BudgetCategory;

/**
 * This class represents a row in a tabular report that represents a budget item category.  It contains some meta-data
 * about that category, such as the number of items in that category.  The reason for the meta-data is to help the
 * reporting classes plan the layout of the portion of the report that contains the category and it's sub items.
 */
public class BudgetCategoryReportRow extends ReportRow {

    /*
     * Fields:
     */
    int includedItems;
    double averageAnnualAmount;
    double plannedAmountInPeriod;
    double actualAmountInPeriod;
    BudgetCategory budgetCategory;
    BudgetTotalsReportRow budgetTotalsReportRow;


    /*
     * Getters and setters:
     */
    public int getIncludedItems() {
        return includedItems;
    }
    public void setIncludedItems(int includedItems) {
        this.includedItems = includedItems;
    }
    public void incrementIncludedItems() {
        includedItems++;
    }

    public double getPlannedAmountInPeriod() {
        return plannedAmountInPeriod;
    }
    public void setPlannedAmountInPeriod(double plannedAmountInPeriod) {
        this.plannedAmountInPeriod = plannedAmountInPeriod;
    }
    public void incrementPlannedAmount(double plannedAmount) {
        this.plannedAmountInPeriod += plannedAmount;
    }

    public double getPercentPlannedAmount() {
        double percentPlannedAmount;
        if (budgetCategory.getCategoryType() == BudgetCategory.CategoryType.EXPENSE) {
            if (
                    budgetTotalsReportRow.getTotalPlannedSpendingInPeriod() < -0.5 ||
                    budgetTotalsReportRow.getTotalPlannedSpendingInPeriod() > 0.5
            ) {
                percentPlannedAmount = (plannedAmountInPeriod / budgetTotalsReportRow.getTotalPlannedSpendingInPeriod()) * 100.0;
            } else {
                percentPlannedAmount =  0;
            }
        } else {
            if (
                    budgetTotalsReportRow.getTotalPlannedIncomeInPeriod() < -0.5 ||
                    budgetTotalsReportRow.getTotalPlannedIncomeInPeriod() > 0.5
            ) {
                percentPlannedAmount = (plannedAmountInPeriod / budgetTotalsReportRow.getTotalPlannedIncomeInPeriod()) * 100.0;
            } else {
                percentPlannedAmount = 0;
            }
        }
        return Math.abs(percentPlannedAmount);
    }

    public double getActualAmountInPeriod() {
        return actualAmountInPeriod;
    }
    public void setActualAmountInPeriod(double actualAmountInPeriod) {
        this.actualAmountInPeriod = actualAmountInPeriod;
    }
    public void incrementActualAmount(double amount) {
        actualAmountInPeriod += amount;
    }

    public double getPercentActualAmount() {
        double percentActualAmount;
        if (budgetCategory.getCategoryType() == BudgetCategory.CategoryType.EXPENSE) {
            if (
                    budgetTotalsReportRow.getTotalActualSpendingInPeriod() < -0.5 ||
                    budgetTotalsReportRow.getTotalActualSpendingInPeriod() > 0.5
            ) {
                percentActualAmount = (actualAmountInPeriod / budgetTotalsReportRow.getTotalActualSpendingInPeriod()) * 100.0;
            } else {
                percentActualAmount = 0;
            }
        } else {
            if (
                    budgetTotalsReportRow.getTotalActualIncomeInPeriod() < -0.5 ||
                    budgetTotalsReportRow.getTotalActualIncomeInPeriod() > 0.5
            ) {
                percentActualAmount = (actualAmountInPeriod / budgetTotalsReportRow.getTotalActualIncomeInPeriod()) * 100.0;
            } else {
                percentActualAmount = 0;
            }
        }
        return Math.abs(percentActualAmount);
    }

    public double getForecastAnnualAmount() {
        return averageAnnualAmount;
    }

    public void setAverageAnnualAmount(double averageAnnualAmount) {
        this.averageAnnualAmount = averageAnnualAmount;
    }

    public void incrementForecastAnnualAmount(double budgetItemAverageAnnualAmount) {
        setAverageAnnualAmount(getForecastAnnualAmount() + budgetItemAverageAnnualAmount);
    }

    public double getPercentAverageAnnualAmount() {
        double percentAverageAnnualAmount;
        if (budgetCategory.getCategoryType() == BudgetCategory.CategoryType.EXPENSE) {
            if (
                    budgetTotalsReportRow.getTotalActualSpendingInPeriod() < -0.5 ||
                            budgetTotalsReportRow.getTotalActualSpendingInPeriod() > 0.5
            ) {
                percentAverageAnnualAmount = (actualAmountInPeriod / budgetTotalsReportRow.getTotalActualSpendingInPeriod()) * 100.0;
            } else {
                percentAverageAnnualAmount = 0;
            }
        } else {
            if (
                    budgetTotalsReportRow.getTotalActualIncomeInPeriod() < -0.5 ||
                            budgetTotalsReportRow.getTotalActualIncomeInPeriod() > 0.5
            ) {
                percentAverageAnnualAmount = (actualAmountInPeriod / budgetTotalsReportRow.getTotalActualIncomeInPeriod()) * 100.0;
            } else {
                percentAverageAnnualAmount = 0;
            }
        }
        return Math.abs(percentAverageAnnualAmount);
    }


    public BudgetCategory getBudgetCategory() {
        return budgetCategory;
    }
    public void setBudgetCategory(BudgetCategory budgetCategory) {
        this.budgetCategory = budgetCategory;
    }


    /*
     * Constructors:
     */
    /**
     * Create a ReportCategoryRow information object.
     *
     * @param budgetCategory A {@link BudgetCategory} object.
     * @param budgetTotalsReportRow A {@link BudgetTotalsReportRow} object that will be updated by this object.
     */
    public BudgetCategoryReportRow(BudgetCategory budgetCategory, BudgetTotalsReportRow budgetTotalsReportRow) {
        this.includedItems = 0;
        this.plannedAmountInPeriod = 0;
        this.actualAmountInPeriod = 0;
        this.budgetCategory = budgetCategory;
        this.budgetTotalsReportRow = budgetTotalsReportRow;
    }
}
