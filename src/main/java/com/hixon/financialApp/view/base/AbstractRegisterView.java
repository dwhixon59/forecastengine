package com.hixon.financialApp.view.base;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.Entity;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.model.user.UserResource;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.ViewException;
import com.hixon.financialApp.view.text.NewTransactionSummaryReport;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
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
    protected abstract NewTransactionSummaryReport getNewTransactionSummaryReport(Register register, List<Entity> items,
                                                                                  File file) throws FileNotFoundException;


    /*
     * Main methods:
     */
    @Override
    public boolean verifyRegisterBalance(Register register) throws EntityException, SQLException, BudgetException,
            RegisterException {
        boolean wasCorrect = true;
        Register dbRegister = Register.getById(register.getId());

        if (!Utility.isEqualCurrency(register.getBalance(), dbRegister.getBalance())) {
            Utility.getResolver().say("\nThe in memory register balance is " + Utility.formatDollarAmount(
                    register.getBalance()) + " but the register balance in the database is " + Utility.formatDollarAmount(
                    register.getBalance()) + ".  You should update it.");
        }

        if (Utility.getResolver().getYesOrNo("The current balance of the " +
                register.getRegisterName() + " is " + Utility.formatDollarAmount(register.getBalance()) +
                "  Do you want to update it?")) {
            double balance = Utility.getResolver().getDollarAmount();
            register.setBalance(balance);
            register.update();
            wasCorrect = false;
        }
        return wasCorrect;
    }

    @Override
    public boolean renderTransactionReport(Calendar startDate) throws FileNotFoundException, UnsupportedEncodingException,
            ViewException {
        return false;
    }


    @Override
    public List<UserResource> renderNewTransactionSummaryReport() throws EntityException, Exception, BudgetException,
            ViewException, RegisterException {

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

    protected UserResource renderNewTransactionSummaryReport(User user) throws EntityException, Exception, BudgetException,
            ViewException, RegisterException {

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

    protected boolean renderNewTransactionSummaryReport(User user, File file) throws EntityException, Exception,
            BudgetException, ViewException, RegisterException {

        // Get a list of the new transactions for the summary report:
        List<Entity> items = Collections.unmodifiableList(Register.getNewTransactions(register));

        // Render an New Transaction Summary report for those items:
        boolean result = false;
        if (items.size() > 0) {
            NewTransactionSummaryReport report = getNewTransactionSummaryReport(register, items, file);
            Renderer<NewTransactionSummaryReport> renderer = new Renderer<>(report);
            renderer.renderReport();
            result = true;
        }

        return result;
    }

}
