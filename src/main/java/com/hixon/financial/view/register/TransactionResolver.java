package com.hixon.financial.view.register;

import com.hixon.financial.controller.Importer;
import com.hixon.financial.controller.QuitException;
import com.hixon.financial.model.EntityException;
import com.hixon.financial.model.budget.BudgetException;
import com.hixon.financial.model.budget.BudgetItemMerchant;
import com.hixon.financial.model.forecast.ForecastTransaction;
import com.hixon.financial.model.forecast.ForecastTransactionSplit;
import com.hixon.financial.model.register.Merchant;
import com.hixon.financial.model.register.RegisterException;
import com.hixon.financial.model.register.Transaction;
import com.hixon.financial.model.register.TransactionSplit;
import com.hixon.financial.view.ViewException;

import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.List;

public interface TransactionResolver {

   public void say(String s);

   public Importer.TerminationCondition getTerminationCondition();

   public List<BudgetItemMerchant> assignBudgetItems(Merchant transaction)
           throws BudgetException, ParseException, SQLException, ViewException, EntityException, RegisterException;

   public Merchant assignMerchant(String merchantPayeeString, String transactionPayeeString) throws ViewException, RegisterException, EntityException;

   String resolveUnmatchedAccount(String payee) throws RegisterException;

   List<TransactionSplit> assignAmountsToBudgetItems(Transaction transaction, Merchant merchant,
                                                     List<BudgetItemMerchant> budgetItems)
           throws EntityException, RegisterException, SQLException, ViewException, BudgetException, ParseException;

   void beginImportItem();

   boolean askRegenerateForecast();

   UserResponse transactionAmountDiscrepancy(Transaction transaction, TransactionSplit split,
                                             ForecastTransaction forecastTransaction);

   // What to do if the split amount exceeds the budgeted amount:
   ForecastTransactionSplit.SplitDisposition assignOverageAmount(double amount) throws IOException;

   // What to do if we're not sure which forecast transaction to assign a split to because the amount differs:
   UserResponse assignSplitAmountToForecastTransaction(TransactionSplit split, ForecastTransaction forecastTransaction);

   // What to do if we're not sure which forecast transaction to assign a split to because the date differs:
   UserResponse assignSplitDateToForecastTransaction(TransactionSplit split, ForecastTransaction forecastTransaction) throws EntityException, SQLException;

   // Get the start date of the portion of the forecast to update:
   UserResponse getForecastUpdateStartDate() throws QuitException;
}

