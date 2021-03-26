package com.hixon.financialApp.model.forecast;

import com.hixon.financialApp.model.budget.BudgetException;

public interface ForecastTransactionIterator {

    ForecastTransaction getNext() throws Exception, BudgetException;
}
