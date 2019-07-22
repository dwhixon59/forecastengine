package com.hixon.financial.view.register;

import com.hixon.financial.controller.Importer;
import com.hixon.financial.model.EntityException;
import com.hixon.financial.model.budget.BudgetException;
import com.hixon.financial.model.budget.BudgetItem;
import com.hixon.financial.model.register.Merchant;
import com.hixon.financial.model.register.RegisterException;
import com.hixon.financial.model.register.Transaction;
import com.hixon.financial.view.ViewException;

import java.sql.SQLException;
import java.text.ParseException;

public interface TransactionResolver {

    public Importer.TerminationCondition getTerminationCondition();

    public BudgetItem resolveUnmatchedBudgetItem(Transaction transaction)
            throws BudgetException, ParseException, SQLException, ViewException;

    public Merchant resolveUnmatchedMerchant(String transaction) throws ViewException, RegisterException, EntityException;

    String resolveUnmatchedAccount(String payee) throws RegisterException;
}
