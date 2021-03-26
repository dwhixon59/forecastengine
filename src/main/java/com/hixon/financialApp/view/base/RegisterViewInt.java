package com.hixon.financialApp.view.base;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.user.UserResource;
import com.hixon.financialApp.view.ViewException;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.List;

public interface RegisterViewInt {

    boolean renderTransactionReport(Calendar startDate) throws FileNotFoundException, UnsupportedEncodingException, ViewException;

    // Render the New Transaction Summary report for all users:
    List<UserResource> renderNewTransactionSummaryReport() throws EntityException, Exception, BudgetException,
            ViewException, RegisterException;

    /**
     * This method will verify the balance of the specified register and update it if it is incorrect.
     *
     * @param register The register whose balance needs to be verified.
     * @return True if the balance was correct.  False if the balance was incorrect and was therefore updated.
     */
    boolean verifyRegisterBalance(Register register) throws EntityException, SQLException, BudgetException, RegisterException;
}
