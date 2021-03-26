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
    double plannedAmount;
    double actualAmount;
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

    public double getPlannedAmount() {
        return plannedAmount;
    }
    public void setPlannedAmount(double plannedAmount) {
        this.plannedAmount = plannedAmount;
    }
    public void incrementPlannedAmount(double plannedAmount) {
        this.plannedAmount += plannedAmount;
    }

    public double getPercentPlannedAmount() {
        double percentPlannedAmount;
        if (budgetCategory.getCategoryType() == BudgetCategory.CategoryType.EXPENSE) {
            percentPlannedAmount = (plannedAmount / budgetTotalsReportRow.getTotalPlannedSpending()) * 100.0;
        } else {
            percentPlannedAmount = (actualAmount / budgetTotalsReportRow.getTotalPlannedIncome()) * 100.0;
        }
        return percentPlannedAmount;
    }

    public double getActualAmount() {
        return actualAmount;
    }
    public void setActualAmount(double actualAmount) {
        this.actualAmount = actualAmount;
    }
    public void incrementActualAmount(double amount) {
        actualAmount += amount;
    }

    public double getPercentActualAmount() {
        double percentActualAmount;
        if (budgetCategory.getCategoryType() == BudgetCategory.CategoryType.EXPENSE) {
            percentActualAmount = (actualAmount / budgetTotalsReportRow.getTotalActualSpending()) * 100.0;
        } else {
            percentActualAmount = (actualAmount / budgetTotalsReportRow.getTotalActualIncome()) * 100.0;
        }
        return percentActualAmount;
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
        this.plannedAmount = 0;
        this.actualAmount = 0;
        this.budgetCategory = budgetCategory;
        this.budgetTotalsReportRow = budgetTotalsReportRow;
    }
}
