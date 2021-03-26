package com.hixon.financialApp.view.base;

public class BudgetTotalsReportRow {

    double totalBudgetedSpending;
    double totalActualSpending;
    double totalBudgetedIncome;
    double totalActualIncome;

    /*
     * Getters and setters:
     */

    public double getTotalPlannedSpending() {
        return totalBudgetedSpending;
    }
    public void setTotalBudgetedSpending(double totalBudgetedSpending) {
        this.totalBudgetedSpending = totalBudgetedSpending;
    }
    public void incrementTotalBudgetedSpending(double totalBudgetedSpending) {
        this.totalBudgetedSpending += totalBudgetedSpending;
    }

    public double getTotalActualSpending() {
        return totalActualSpending;
    }
    public void setTotalActualSpending(double totalActualSpending) {
        this.totalActualSpending = totalActualSpending;
    }
    public void incrementTotalActualSpending(double totalActualSpending) {
        this.totalActualSpending += totalActualSpending;
    }

    public double getTotalPlannedIncome() {
        return totalBudgetedIncome;
    }
    public void setTotalBudgetedIncome(double totalBudgetedIncome) {
        this.totalBudgetedIncome = totalBudgetedIncome;
    }
    public void incrementTotalBudgetedIncome(double totalBudgetedIncome) {
        this.totalBudgetedIncome += totalBudgetedIncome;
    }

    public double getTotalActualIncome() {
        return totalActualIncome;
    }
    public void setTotalActualIncome(double totalActualIncome) {
        this.totalActualIncome = totalActualIncome;
    }
    public void incrementTotalActualIncome(double totalActualIncome) {
        this.totalActualIncome += totalActualIncome;
    }
}
