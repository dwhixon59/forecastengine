package com.hixon.financialApp.view.base;

public class BudgetTotalsReportRow {

    double totalPlannedSpendingInPeriod;
    double totalActualSpendingInPeriod;
    double totalPlannedIncomeInPeriod;
    double totalActualIncomeInPeriod;
    double totalAverageAnnualSpending;
    double totalAverageAnnualIncome;


    /*
     * Getters and setters:
     */
    public double getTotalPlannedSpendingInPeriod() {
        return totalPlannedSpendingInPeriod;
    }
    public void setTotalPlannedSpendingInPeriod(double totalPlannedSpendingInPeriod) {
        this.totalPlannedSpendingInPeriod = totalPlannedSpendingInPeriod;
    }
    public void incrementTotalPlannedSpending(double totalPlannedSpending) {
        this.totalPlannedSpendingInPeriod += totalPlannedSpending;
    }

    public double getTotalActualSpendingInPeriod() {
        return totalActualSpendingInPeriod;
    }
    public void setTotalActualSpendingInPeriod(double totalActualSpendingInPeriod) {
        this.totalActualSpendingInPeriod = totalActualSpendingInPeriod;
    }
    public void incrementTotalActualSpending(double spending) {
        this.totalActualSpendingInPeriod += spending;
    }

    public double getTotalPlannedIncomeInPeriod() {
        return totalPlannedIncomeInPeriod;
    }
    public void setTotalPlannedIncomeInPeriod(double totalPlannedIncomeInPeriod) {
        this.totalPlannedIncomeInPeriod = totalPlannedIncomeInPeriod;
    }
    public void incrementTotalPlannedIncome(double totalPlannedIncome) {
        this.totalPlannedIncomeInPeriod += totalPlannedIncome;
    }

    public double getTotalActualIncomeInPeriod() {
        return totalActualIncomeInPeriod;
    }
    public void setTotalActualIncomeInPeriod(double totalActualIncomeInPeriod) {
        this.totalActualIncomeInPeriod = totalActualIncomeInPeriod;
    }
    public void incrementTotalActualIncome(double income) {
        this.totalActualIncomeInPeriod += income;
    }

    public double getTotalAverageAnnualSpending() {
        return totalAverageAnnualSpending;
    }
    public void setTotalAverageAnnualSpending(double totalAverageAnnualSpending) {
        this.totalAverageAnnualSpending = totalAverageAnnualSpending;
    }
    public void incrementTotalForecastSpending(double amount) {
        this.totalAverageAnnualSpending += amount;
    }

    public double getTotalAverageAnnualIncome() {
        return totalAverageAnnualIncome;
    }
    public void setTotalAverageAnnualIncome(double totalAverageAnnualIncome) {
        this.totalAverageAnnualIncome = totalAverageAnnualIncome;
    }
    public void incrementTotalForecastIncome(double amount) {
        this.totalAverageAnnualIncome += amount;
    }
}
