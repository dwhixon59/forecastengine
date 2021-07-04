package com.hixon.financialApp.view.text;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.Entity;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.view.ViewException;

import java.io.*;
import java.sql.SQLException;
import java.util.List;


/*
 * A report that shows the remaining amounts in each item in the current period of the specified forecast that is of
 * interest to a specified user, or all users:
 */
public class UpcomingItemsOfInterestReport extends ForecastReport {

    private final User user;

    public UpcomingItemsOfInterestReport(Forecast forecast, User user, List<Entity> items, File reportFile)
            throws FileNotFoundException {

        super(forecast, items, reportFile);
        this.user = user;
    }


    /*
     * Output the report:
     */
    @Override
    public void openReportOutput() throws FileNotFoundException, UnsupportedEncodingException, ViewException {
        boolean append = true;
        boolean autoFlush = true;
        String charset = "UTF-8";

        FileOutputStream fos = new FileOutputStream(reportFile, append);
        OutputStreamWriter osw = new OutputStreamWriter(fos, charset);
        BufferedWriter bw = new BufferedWriter(osw);
        pw = new PrintWriter(bw, autoFlush);
    }

    @Override
    public void renderReportFrontMatter() {
        pw.println("Upcoming Items of Interest to " + user.getFirstName() + ":");
        pw.println("Date, Item, Amount");
        pw.println("------------------------------------");
    }

    @Override
    public void renderItemRow(Entity item) throws EntityException, ForecastException, SQLException, BudgetException,
            RegisterException {
        ForecastTransaction forecastTransaction = (ForecastTransaction) item;
        pw.println(forecastTransaction.toStringCompact());
    }

    /**
     * Nothing to output at this time.
     *
     * @throws EntityException
     * @throws Exception
     * @throws BudgetException
     */
    @Override
    public void renderReportBackMatter() throws EntityException, Exception, BudgetException, RegisterException {

    }
}
