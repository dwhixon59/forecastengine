package com.hixon.financial.model.forecast;

public interface ForecastTransactionIterator {

    public void setForecast(Forecast forecast) throws ForecastException;
    public ForecastTransaction getNext() throws ForecastException;
}
