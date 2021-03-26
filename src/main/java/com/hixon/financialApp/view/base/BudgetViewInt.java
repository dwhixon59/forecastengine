package com.hixon.financialApp.view.base;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.view.ViewException;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.Calendar;

public interface BudgetViewInt {

    /**
     * Create and render a spending report for a given month as an XML spreadsheet file that can be imported into a
     * spreadsheet.
     *
     * @param month The month to report on.
     */
    public void renderSpendingReportForMonth(Calendar month) throws FileNotFoundException, UnsupportedEncodingException,
            EntityException, SQLException, BudgetException, RegisterException, ForecastException, ViewException;

    // Render the spending report:
    void renderPlannedVsActualReport(Calendar startDate, Calendar endDate) throws FileNotFoundException,
            UnsupportedEncodingException, EntityException, SQLException, BudgetException, RegisterException,
            ForecastException, ViewException;

    /**
     * Render a summary of the budget report for a period of one year.  This is calculated by considering the period of
     * each planned item in the budget and multiplying the number of occurrences in a a year by the amount of the item.
     */
    void renderBudgetSummaryReport() throws FileNotFoundException, UnsupportedEncodingException, ViewException,
            EntityException, SQLException, BudgetException, ForecastException, RegisterException;
}
