package com.hixon.financialApp.view.text;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.Entity;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.ViewException;
import com.hixon.financialApp.view.base.AbstractRegisterReport;

import java.io.*;
import java.sql.SQLException;
import java.util.List;

public class NewTransactionSummaryReport extends AbstractRegisterReport {

    private final User user;
    private final List<Entity> items;
    private final File reportFile;
    private PrintWriter pw;

    public NewTransactionSummaryReport(Register register, User user, List<Entity> items, File file) {
        super(register);

        this.user = user;
        this.items = items;
        this.reportFile = file;
    }

    /*
     * Output the report:
     */
    @Override
    public void openReportOutput() throws FileNotFoundException, UnsupportedEncodingException, ViewException {
        boolean append = false;
        boolean autoFlush = true;
        String charset = "UTF-8";

        FileOutputStream fos = new FileOutputStream(reportFile, append);
        OutputStreamWriter osw = new OutputStreamWriter(fos, charset);
        BufferedWriter bw = new BufferedWriter(osw);
        pw = new PrintWriter(bw, autoFlush);
    }

    @Override
    public void renderReportFrontMatter() {
        pw.println("Items of Interest to " + user.getFirstName() + ":");
    }

    @Override
    public void renderHeaderRow() {

    }

    @Override
    public List<Entity> getItems() {
        return items;
    }

    @Override
    public void renderItemRow(Entity item) throws EntityException, ForecastException, SQLException, BudgetException {
        ForecastTransaction forecastTransaction = (ForecastTransaction) item;
        pw.println(forecastTransaction.getForecastItem().getPayee() + "\t" + ((forecastTransaction.getRemainingAmount() ==
                0) ? "$0.00" : Utility.formatDollarAmount(-forecastTransaction.getRemainingAmount())));
        Utility.getResolver().say(forecastTransaction.getForecastItem().getPayee() + "\t" +
                ((forecastTransaction.getRemainingAmount() == 0) ? "$0.00" :
                        Utility.formatDollarAmount(-forecastTransaction.getRemainingAmount())));
    }

    @Override
    public void renderSummaryRow() {

    }

    @Override
    public void renderReportBackMatter() {

    }

    @Override
    public void closeReportOutput() {
        pw.close();
    }
}

