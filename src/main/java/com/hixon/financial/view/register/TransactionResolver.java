package com.hixon.financial.view.register;

import com.hixon.financial.controller.Importer;
import com.hixon.financial.model.EntityException;
import com.hixon.financial.model.budget.BudgetException;
import com.hixon.financial.model.budget.BudgetItemMerchant;
import com.hixon.financial.model.register.Merchant;
import com.hixon.financial.model.register.RegisterException;
import com.hixon.financial.model.register.Transaction;
import com.hixon.financial.view.ViewException;

import java.sql.SQLException;
import java.text.ParseException;
import java.util.List;

public interface TransactionResolver {

    public Importer.TerminationCondition getTerminationCondition();

    public List<BudgetItemMerchant> assignBudgetItems(Merchant transaction)
            throws BudgetException, ParseException, SQLException, ViewException, EntityException, RegisterException;

    public Merchant assignMerchant(String transaction) throws ViewException, RegisterException, EntityException;

    String resolveUnmatchedAccount(String payee) throws RegisterException;

    void assignAmountsToBudgetItems(Transaction transaction,  Merchant merchant, List<BudgetItemMerchant> budgetItems) throws EntityException, RegisterException, SQLException, ViewException, BudgetException, ParseException;

    void beginImportItem();
}
