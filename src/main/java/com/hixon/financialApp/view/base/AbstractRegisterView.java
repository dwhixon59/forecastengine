package com.hixon.financialApp.view.base;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.Entity;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.model.user.UserResource;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.ViewException;
import com.hixon.financialApp.view.text.NewTransactionSummaryReport;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class AbstractRegisterView  extends AbstractView implements RegisterViewInt {

    /*
     * Fields:
     */
    protected Register register;

    public AbstractRegisterView(Register register) {
        this.register = register;
    }

    /*
     * Getters and Setters:
     */
    protected abstract NewTransactionSummaryReport getNewTransactionSummaryReport(User user, List<Entity> items, File file);


    /*
     * Main methods:
     */
    @Override
    public List<UserResource> renderNewTransactionSummaryReport() throws EntityException, SQLException, BudgetException, IOException,
            ViewException, ForecastException, RegisterException {

        // Create a holder for the individual user reports:
        List<UserResource> reports = new ArrayList<>();

        // Get a list of users:
        List<User> users = User.getAllUsers();

        // For each user:
        for (User user : users
        ) {
            // Render an New Transaction Summary report for the current user:
            UserResource userResource = renderNewTransactionSummaryReport(user);
            if (userResource != null) {
                reports.add(userResource);
            } else {
                Utility.getResolver().say("\nNo new transactions to report on.");
                break;
            }
        }

        return reports;
    }

    protected UserResource renderNewTransactionSummaryReport(User user) throws EntityException, SQLException, BudgetException,
            IOException, ViewException, ForecastException, RegisterException {

        UserResource userResource = null;
        File NewTransactionSummaryReportFile = File.createTempFile("NewTransactionSummaryReport_" + user.getFirstName() + "_",
                ".txt");
        if (renderNewTransactionSummaryReport(user, NewTransactionSummaryReportFile)) {
            userResource = new UserResource(user, UserResource.ResourceType.NewTransactionSummaryReport,
                    NewTransactionSummaryReportFile);
        } else {
            NewTransactionSummaryReportFile.delete();
        }
        return userResource;
    }

    protected boolean renderNewTransactionSummaryReport(User user, File file) throws EntityException, SQLException, BudgetException,
            FileNotFoundException, UnsupportedEncodingException, ViewException, ForecastException, RegisterException {

        // Get a list of the new transactions for the summary report:
        List<Entity> items = Collections.unmodifiableList(Register.getNewTransactions(register));

        // Render an New Transaction Summary report for those items:
        boolean result = false;
        if (items.size() > 0) {
            NewTransactionSummaryReport report = getNewTransactionSummaryReport(user, items, file);
            Renderer<NewTransactionSummaryReport> renderer = new Renderer<>(report);
            renderer.renderReport();
            result = true;
        }

        return result;
    }

}
