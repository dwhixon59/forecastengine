package com.hixon.financial.model.forecast;

public interface ForecastTransactionIterator {

    void setForecast(Forecast forecast) throws ForecastException;
    ForecastTransaction getNext() throws Exception;
}
