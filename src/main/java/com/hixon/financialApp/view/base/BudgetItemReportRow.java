package com.hixon.financialApp.view.base;

import com.hixon.financialApp.model.budget.BudgetItem;

/**
 * This class represents a row in a tabular report that represents a budget item.  It contains some meta-data
 * about that item, such as the number of splits associated wit the iem.  The reason for the meta-data is to help the
 * reporting classes plan the layout of the portion of the report that contains the item and it's splits.
 */
public class BudgetItemReportRow extends ReportRow {

    /*
     * Fields:
     */
    private int includedSplits;
    private double plannedAmount;
    private double actualAmount;
    private final BudgetItem budgetItem;
    private final BudgetCategoryReportRow budgetCategoryReportRow;

    /*
     * Getters and Setters:
     */
    public int getIncludedSplits() {
        return includedSplits;
    }
    public void setIncludedSplits(int includedSplits) {
        this.includedSplits = includedSplits;
    }
    public void incrementIncludedSplits() {
        includedSplits++;
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
        return (plannedAmount / budgetCategoryReportRow.getPlannedAmount()) * 100.0;
     }

    public double getActualAmount() {
        return actualAmount;
    }
    public void setActualAmount(double actualAmount) {
        this.actualAmount = actualAmount;
    }
    public void incrementActualAmount(double actualAmount) {
        this.actualAmount += actualAmount;
    }
    public double getPercentActualAmount() {
        return (actualAmount / budgetCategoryReportRow.getActualAmount()) * 100;
    }

    public BudgetItem getBudgetItem() {
        return budgetItem;
    }


    /*
     * Constructors:
     */
    /**
     * Create a ReportCategoryRow information object.
     *
     * @param budgetItem A budget item object.
     */
    public BudgetItemReportRow(BudgetItem budgetItem, BudgetCategoryReportRow budgetCategoryReportRow) {
        this.includedSplits = 0;
        this.plannedAmount = 0;
        this.actualAmount = 0;
        this.budgetItem = budgetItem;
        this.budgetCategoryReportRow = budgetCategoryReportRow;
    }


    /*
     * Helper methods:
     */
}
