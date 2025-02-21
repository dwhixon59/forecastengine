package com.hixon.financialApp.view.base;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.Entity;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.view.ViewException;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.List;

/**
 * The AbstractReport class contains the code that is common to all reports.  For example all reports are a formatted
 * list of items, so this class contains a list of items.  The class also implements the view report interface
 * {@link ReportRendererInt} that is the basis of the generic report rendering class and provides default implementations of
 * all the methods is the interface so that sub classes only have to implement the methods that they need to.
 */
public abstract class AbstractReportView extends AbstractView implements ReportRendererInt {

    protected final List<Entity> items;

    protected AbstractReportView(List<Entity> items) {
        this.items = items;
    }

    @Override
    public void openReportOutput() throws FileNotFoundException, UnsupportedEncodingException, ViewException {

    }

    @Override
    public void renderReportFrontMatter() {

    }

    @Override
    public void renderHeaderRow() {

    }

    @Override
    public void renderItemRow(Entity item) throws Exception {

    }

    @Override
    public void renderSummaryRow() throws Exception {

    }

    @Override
    public void renderReportBackMatter() throws EntityException, SQLException, ForecastException, BudgetException, Exception, RegisterException {

    }

    @Override
    public void closeReportOutput() {

    }

    @Override
    public List<Entity> getItems() throws Exception {
        return items;
    }

}
