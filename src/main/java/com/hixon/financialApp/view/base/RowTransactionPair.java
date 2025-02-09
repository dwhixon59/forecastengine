package com.hixon.financialApp.view.base;

import com.hixon.financialApp.model.forecast.ForecastTransaction;

/**
 * A class representing a pair of a row number and a forecast transaction.
 */
public class RowTransactionPair {
    private int row;
    private ForecastTransaction forecastTransaction;

    /**
     * Constructs a new RowTransactionPair with the specified row number and forecast transaction.
     *
     * @param row the row number
     * @param forecastTransaction the forecast transaction
     */
    public RowTransactionPair(int row, ForecastTransaction forecastTransaction) {
        this.row = row;
        this.forecastTransaction = forecastTransaction;
    }

    /**
     * Returns the row number.
     *
     * @return the row number
     */
    public int getRowNum() {
        return row;
    }

    /**
     * Sets the row number.
     *
     * @param row the row number
     */
    public void setRow(int row) {
        this.row = row;
    }

    /**
     * Returns the forecast transaction.
     *
     * @return the forecast transaction
     */
    public ForecastTransaction getForecastTransaction() {
        return forecastTransaction;
    }

    /**
     * Sets the forecast transaction.
     *
     * @param forecastTransaction the forecast transaction
     */
    public void setForecastTransaction(ForecastTransaction forecastTransaction) {
        this.forecastTransaction = forecastTransaction;
    }
}