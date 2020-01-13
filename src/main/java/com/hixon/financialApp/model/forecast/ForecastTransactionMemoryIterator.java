package com.hixon.financialApp.model.forecast;

import java.util.Calendar;

public class ForecastTransactionMemoryIterator implements ForecastTransactionIterator {

    Forecast forecast = null;
    ForecastTransaction[] transactions = null;
    int dayOfForecast = 0;
    ForecastTransaction nextTransaction = null;
    Calendar startDate = null;

   public ForecastTransactionMemoryIterator(Forecast forecast, Calendar startDate) throws ForecastException {
       setForecast(forecast);
       this.startDate = startDate;
   }


   @Override
    public void setForecast(Forecast forecast) throws ForecastException {
        if (forecast == null) throw new ForecastException("Forecast must not be null.");
        this.forecast = forecast;
        this.transactions = forecast.getTransactions();
        int i =0;
        while (transactions[i] == null && i < transactions.length) { i++; }
        if (i == transactions.length) throw new ForecastException("No transactions in the forecast.");
        nextTransaction = transactions[i];
    }


    @Override
    public ForecastTransaction getNext() throws ForecastException {

        // Check preconditions:
        if (forecast == null) {
            throw new ForecastException("Must set forecast before calling getNext().");
        }

        // Save off the next transaction, which is what we will return:
        ForecastTransaction currentTransaction = nextTransaction;

        // If there are more transactions:
        if (currentTransaction != null) {

            //  then if there are more transactions on this day, then go to the next transaction:
            if (nextTransaction.getNextTransaction() != null) {
                nextTransaction = nextTransaction.getNextTransaction();
            }
            else {
                // else go to the next day of the forecast that has transactions.
                dayOfForecast += 1;
                while ((dayOfForecast < transactions.length) && (transactions[dayOfForecast] == null)) {
                    dayOfForecast += 1;
                }
                //  Now if we found a day in the forecast with more transactions:
                if (dayOfForecast < transactions.length) {

                    // make that day the next day in the forecast:
                    nextTransaction = transactions[dayOfForecast];
                }
                else {
                    // else return null to indicate that there are no more transactions to process:
                    nextTransaction = null;
                }
            }
        }
        return currentTransaction;
    }
}
