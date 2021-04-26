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
    private double averageAnnualAmount;
    private double plannedAmountInPeriod;
    private double actualAmountInPeriod;
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
        if (
                budgetCategoryReportRow.getPlannedAmountInPeriod() < -0.5 ||
                budgetCategoryReportRow.getPlannedAmountInPeriod() > 0.5
        ) {
            return Math.abs((plannedAmountInPeriod / budgetCategoryReportRow.getPlannedAmountInPeriod()) * 100.0);
        } else {
            return 0.0;
        }
     }

    public double getActualAmountInPeriod() {
        return actualAmountInPeriod;
    }
    public void setActualAmountInPeriod(double actualAmountInPeriod) {
        this.actualAmountInPeriod = actualAmountInPeriod;
    }
    public void incrementActualAmount(double actualAmount) {
        this.actualAmountInPeriod += actualAmount;
    }
    public double getPercentActualAmount() {
        if (
                budgetCategoryReportRow.getActualAmountInPeriod() < -0.5 ||
                budgetCategoryReportRow.getActualAmountInPeriod() > 0.5
        ) {
            return Math.abs((actualAmountInPeriod / budgetCategoryReportRow.getActualAmountInPeriod()) * 100);
        } else {
            return 0.0;
        }
    }

    public double getForecastAnnualAmount() {
        return averageAnnualAmount;
    }
    public void setForecastAnnualAmount(double averageAnnualAmount) {
        this.averageAnnualAmount = averageAnnualAmount;
    }
    public double getPercentForecastAnnualAmount() {
        if (
                budgetCategoryReportRow.getForecastAnnualAmount() < -0.5 ||
                        budgetCategoryReportRow.getForecastAnnualAmount() > 0.5
        ) {
            return Math.abs((averageAnnualAmount / budgetCategoryReportRow.getForecastAnnualAmount()) * 100);
        } else {
            return 0.0;
        }
    }

    public BudgetItem getBudgetItem() {
        return budgetItem;
    }


    /*
     * Constructors:
    /**
     * Create a ReportCategoryRow information object.
     *
     * @param budgetItem A budget item object.
     */
    public BudgetItemReportRow(BudgetItem budgetItem, BudgetCategoryReportRow budgetCategoryReportRow) {
        this.includedSplits = 0;
        this.averageAnnualAmount = 0;
        this.plannedAmountInPeriod = 0;
        this.actualAmountInPeriod = 0;
        this.budgetItem = budgetItem;
        this.budgetCategoryReportRow = budgetCategoryReportRow;
    }


    /*
     * Helper methods:
     */
}
