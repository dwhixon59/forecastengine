package com.hixon.financialApp.model.forecast;

import com.hixon.financialApp.model.budget.BudgetException;

public interface ForecastTransactionIterator {

    void setForecast(Forecast forecast) throws ForecastException;
    ForecastTransaction getNext() throws Exception, BudgetException;
}
