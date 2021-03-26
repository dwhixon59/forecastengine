package com.hixon.financialApp.model.forecast;

import java.util.Calendar;

/**
 * This class represent the daily balance in a forecast on a specific date.
 */
public class DailyBalance {

    /*
     * Fields:
     */
    // Date of this daily balance:
    Calendar date;

    // Balance of the forecast or register on that date:
    double balance;


    /*
     * Getters and setters:
     */
    public Calendar getDate() {
        return date;
    }

    public void setDate(Calendar date) {
        this.date = date;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }


    /*
     * Constructors:
     */
    public DailyBalance(Calendar date, double balance) {
        this.date = date;
        this.balance = balance;
    }


    /*
     *  Helper methods:
     */


    /*
     *  CRUD methods:
     */


    /*
     *  Main methods:
     */


}
