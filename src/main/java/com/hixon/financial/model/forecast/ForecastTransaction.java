package com.hixon.financial.model.forecast;

import com.hixon.financial.Utility;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.UUID;

/**
 * This the class that represents a single transaction in the forecast.
 */
public class ForecastTransaction {

    // Primary key for this ForecastTransaction:
    private final UUID id = UUID.randomUUID();

    // The date that this transaction is expected to occur, or is due:
    private Calendar plannedDate = null;

    // Amount of the transaction in case of an override:
    private double amount = 0;

    // Running runningBalance of the forecast:
    private double runningBalance = 0;

    // A reference to the forecast item that this transaction is an occurrence of;
    private ForecastItem forecastItem = null;

    // A pointer to a transaction in the register that is the actual instance of this forecast transaction:
    private ForecastTransaction transaction = null;

    // A pointer to the next transaction on the same date:
    private ForecastTransaction nextTransaction = null;

    // A pointer to the next transaction in the list of significant transactions:
    private ForecastTransaction nextSignificantEvent = null;


    // Constructors:
    public ForecastTransaction(ForecastItem item, Calendar nextDate) throws Exception {
        if (item == null || nextDate == null) throw new Exception("ForecastItem seeds cannot be null.");
        forecastItem = item;
        plannedDate = (Calendar) nextDate.clone();;
        System.out.println(this.toString());
    }

    public ForecastTransaction(ForecastTransaction forecastTransaction) throws Exception {
        if (forecastTransaction == null) throw new Exception("Forecast transaction to copy cannot be null.");
        forecastItem = forecastTransaction.getForecastItem();
        plannedDate = (Calendar) forecastTransaction.getPlannedDate().clone();
    }

    // Getters and setters:
    public UUID getId() {
        return id;
    }
    public Calendar getPlannedDate() {
        return plannedDate;
    }
    public void setPlannedDate(GregorianCalendar plannedDate) {
        this.plannedDate = plannedDate;
    }
    public double getAmount() {
        return amount;
    }
    public void setAmount(double amount) { this.amount = amount; }
    public double getRunningBalance() {
        return runningBalance;
    }
    void setRunningBalance(double runningBalance) {
        this.runningBalance = runningBalance;
    }
    public ForecastTransaction getTransaction() { return transaction; }
    public ForecastItem getForecastItem() { return forecastItem; }
    public ForecastTransaction getNextTransaction() { return nextTransaction; }
    public void setNextTransaction(ForecastTransaction nextTransaction) { this.nextTransaction = nextTransaction; }
    public ForecastTransaction getNextSignificantEvent() { return nextSignificantEvent; }
    public void setNextSignificantEvent(ForecastTransaction forecastTransaction) { nextSignificantEvent =
            forecastTransaction; }

    // Utilty methods:
    @Override
    public String toString() {
        return "Forecast Transaction:  Planned Date: " + Utility.calendarDateToStringDate(this.getPlannedDate()) + ", Payee: " +
                this.getForecastItem().getPayee() + ", Amount: " + Utility.formatDollarAmount(forecastItem.getAmount())
                + ", Balance: " + Utility.formatDollarAmount(runningBalance) + ", Forecast transaction - ID: " +
                this.getId().toString() + ", Next significant event: " + this.getNextSignificantEvent();
    }
}
