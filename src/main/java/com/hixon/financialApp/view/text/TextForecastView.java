package com.hixon.financialApp.view.text;

import com.hixon.financialApp.controller.ControllerException;
import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.Entity;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.view.ViewException;
import com.hixon.financialApp.view.base.AbstractForecastView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.List;

public class TextForecastView extends AbstractForecastView {
    @Override
    protected void openLongTermForecastOutput() throws FileNotFoundException, UnsupportedEncodingException {

    }

    @Override
    protected void renderLongTermForecastFrontMatter() {

    }

    @Override
    protected void renderMonthHeader(Calendar plannedDate, double runningBalance) {

    }

    @Override
    protected void renderForecastTransaction(ForecastTransaction forecastTransaction, int credit, int debit) throws EntityException, SQLException, ForecastException, BudgetException {

    }

    @Override
    protected void renderLongTermForecastBackMatter() {

    }

    @Override
    protected void closeLongTermForecastOutput() {

    }

    @Override
    protected void closeForecastTransactionSource() throws ViewException {

    }

    @Override
    protected List<ForecastTransaction> openForecastTransactionSource() throws IOException, ControllerException, BudgetException {
        return null;
    }

    @Override
    protected ItemsOfInterestReport getItemsOfInterestReport(User user, List<Entity> items, File file) {
        return new ItemsOfInterestReport(forecast, user, items, file);
    }
}
