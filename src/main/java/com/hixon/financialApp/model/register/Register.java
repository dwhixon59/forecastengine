package com.hixon.financialApp.model.register;

import com.hixon.financialApp.controller.ControllerException;
import com.hixon.financialApp.controller.QuitException;
import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.BudgetItemMerchant;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.entity.IndependentEntity;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.ViewException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.hixon.financialApp.model.entity.EntityInt.SaveMethod.INSERT_ON_DUPLICATE_UPDATE;
import static com.hixon.financialApp.utility.Utility.*;

public class Register extends IndependentEntity {

   /*
    * Fields in the Register class:
    */
   private static final String selectQuery = "select bin_to_uuid(idRegister) as idRegister, name, account_type, " +
           "account_number, bin_to_uuid(Budget_idBudget) as idBudget from ForecastDatabase.Register ";

   private String registerName = null;
   private String accountType = null;
   private String accountNumber = null;
   private UUID idBudget = null;
   private List<Transaction> significantEvents = new ArrayList<Transaction>();


   /*
    * Getters and setters:
    */
   public UUID getId() {
      return id;
   }

   public String getRegisterName() {
      return registerName;
   }

   public void setRegisterName(String registerName) {
      this.registerName = registerName;
   }

   public String getAccountType() {
      return accountType;
   }

   public void setAccountType(String accountType) {
      this.accountType = accountType;
   }

   public String getAccountNumber() {
      return accountNumber;
   }

   public void setAccountNumber(String accountNumber) {
      this.accountNumber = accountNumber;
   }

   public UUID getIdBudget() {
      return idBudget;
   }

   public void setIdBudget(UUID idBudget) {
      this.idBudget = idBudget;
   }

   public List<Transaction> getSignificantEvents() {
      return significantEvents;
   }

   @Override
   public String getInsertQuery() throws BudgetException, ForecastException {
      return null;
   }

   @Override
   public String getInsertOnDuplicateUpdateQuery() throws BudgetException {
      return null;
   }

   @Override
   public String getUpdateByIdQuery() throws BudgetException {
      return null;
   }

   @Override
   public String getDeleteByIdQuery() {
      return null;
   }

   @Override
   public String getPrintableEntityTypeName() {
      return "register";
   }


   /*
    * Constructors:
    */
   public Register() {
      super(true);
   }

   public Register(ResultSet rs) throws RegisterException, SQLException {
      super(false);
      try {
         if (rs != null) {

            this.id = UUID.fromString(rs.getString("idRegister"));
            this.registerName = rs.getString("name");
            this.accountType = rs.getString("account_type");
            this.accountNumber = rs.getString("account_number");
            this.idBudget = UUID.fromString(rs.getString("idBudget"));

         } else {
            throw new RegisterException("Result set passed into Register(rs) is empty or null.");
         }
      } catch (SQLException e) {
         System.out.println("[SEVERE]  SQL error encountered trying to create a register from a result set.");
         if (rs != null) rs.close();
         throw e;
      }
   }


   /*
    * Helper methods:
    */
   public void addSignificantEvent(Transaction transaction) {
      significantEvents.add(transaction);
   }


   /*
    * Load and save methods:
    */
   public static Register getById(UUID idRegister) throws EntityException, SQLException, RegisterException {
      ResultSet rs = EntityInt.getRSById(selectQuery + "where idRegister = ", idRegister, "Database error encountered trying to " +
              "retrieve register with id = " + idRegister);
      return new Register(rs);
   }

   public static Register getByAccountNumber(String accountNumber) throws SQLException, RegisterException {

      try {
         String query = "select bin_to_uuid(idRegister) from ForecastDatabase.Register where Account_Number = '" +
                 accountNumber + "'";
         Statement statement = Utility.getDbConnection().createStatement();
         ResultSet rs = statement.executeQuery(query);
         if (rs.next()) {
            Register register = new Register(rs);
            return register;
         } else {
            return null;
         }
      } catch (SQLException e) {
         RegisterException re = new RegisterException("Database error occurred trying to retrieve a register with the " +
                 "account number " + accountNumber);
         re.initCause(e);
         throw re;
      }
   }

   public static Register getByLastFourDigits(String lastFourDigits) throws SQLException, RegisterException {

      String query = selectQuery + "where Account_Number like '%" + lastFourDigits + "'";
      try {
         System.out.println("SQL statement is: " + query);
         Statement statement = Utility.getDbConnection().createStatement();
         ResultSet rs = statement.executeQuery(query);
         if (rs.next()) {
            Register register = new Register(rs);
            return register;
         } else {
            return null;
         }
      } catch (SQLException e) {
         RegisterException re = new RegisterException("Database error occurred trying to retrieve a register with the " +
                 "sql statement " + query);
         re.initCause(e);
         throw re;
      }
   }

   public static Register getByName(String registerName) throws RegisterException, SQLException {
      // Find the ID of the named budget:
      PreparedStatement preparedStmt = null;
      ResultSet rs = null;
      String query = selectQuery + "where name = '" + registerName + "'";
      try {
         preparedStmt = Utility.getDbConnection().prepareStatement(query);
         rs = preparedStmt.executeQuery();
         Register register = null;
         if (rs != null && rs.next()) {
            register = new Register(rs);
         } else {
            throw new RegisterException("Register named " + registerName + " not found in the database.");
         }
         return register;
      } catch (SQLException e) {
         RegisterException re = new RegisterException("SQL error encountered trying to retrieve a list of registers.");
         re.initCause(e);
         if (preparedStmt != null) preparedStmt.close();
         if (rs != null) rs.close();
         throw re;
      }
   }

   public static List<Register> getListOf() throws RegisterException {

      try {

         System.out.println("SQL statement is: " + selectQuery);
         Statement statement = Utility.getDbConnection().createStatement();
         ResultSet rs = statement.executeQuery(selectQuery);
         List<Register> registers = new ArrayList<>();
         while (rs.next()) {
            Register register = new Register(rs);
            registers.add(register);
         }
         return registers;

      } catch (SQLException | RegisterException e) {
         RegisterException re = new RegisterException("Database error occurred trying to retrieve a register with the " +
                 "sql statement " + selectQuery);
         re.initCause(e);
         throw re;
      }
   }

   public void save() {
   }


   /*
    * Main methods:
    */
   // Reprocess any transactions that were previously skipped:
   public boolean processSkippedTransactions(Forecast forecast)
           throws QuitException, EntityException, RegisterException, ViewException, ControllerException, BudgetException {

      getResolver().say("Process any transactions that were previously skipped in register '" +
              getRegisterName() + "' and/or forecast '" + forecast.getDescription() + "'");

      int i = 0;
      try {
         // Retrieve any transactions that were skipped.
         ResultSet rs = Transaction.getSkippedTransactionsWrtForecast(forecast);

         // For each transaction in the result set:
         Transaction transaction;
         Merchant merchant;
         while(rs.next()) {

            // Get the transaction for this import record:
            transaction = new Transaction(rs);

            // Let the resolver know we are beginning a new item:
            resolver.say("Reprocess skipped " + transaction.toString());

            // Get the merchant for this transaction:
            merchant = transaction.getMerchant();
            if (merchant == null) {
               merchant = Merchant.getByPayee(transaction.getMerchantPayee());
               if (merchant == null) {
                  merchant = resolver.assignMerchant(transaction.getMerchantPayee(), transaction.getPayee());
                  if (merchant == null) {
                     switch (resolver.getTerminationCondition()) {
                        case SKIP:
                           continue;

                        case QUIT:
                           throw new QuitException("Quitting reprocessing of skipped transactions at user request.");

                        default:
                           throw new ControllerException("Invalid termination condition " +
                                   resolver.getTerminationCondition() + " during transaction import");
                     }
                  }
               }

               // then update the transaction merchant info from the merchant that we just found or created:
               transaction.setMerchant(merchant);
               transaction.setIdMerchant(merchant.getId());
            }

            // If there is a provisional transaction for this transaction, then use the same ID:
            transaction.reconcileWithProvisional();

            // Get the assigned budget items for the merchant:
            List<BudgetItemMerchant> budgetItems = BudgetItemMerchant.getAssignedBudgetItems(merchant);

            // If we couldn't find any matching items, get some help from the user:
            if (budgetItems.size() < 1) {
               budgetItems = resolver.assignBudgetItems(merchant);
               if (budgetItems == null) {
                  switch (resolver.getTerminationCondition()) {
                     case SKIP:
                        transaction.save(INSERT_ON_DUPLICATE_UPDATE);
                        continue;

                     case QUIT:
                        throw new QuitException("Quitting reprocessing of skipped transactions at user request.");

                     default:
                        throw new ControllerException("Invalid termination condition " +
                                resolver.getTerminationCondition() + " during transaction import");
                  }
               }
            }

            // Tell the user about the bank transaction we are processing:
            getResolver().say("Imported a bank transaction to " + merchant.getName() + " for " +
                    formatDollarAmount(Math.abs(transaction.getAmount())) + " on " +
                    ((transaction.getAuthorizationDate() != null) ?
                            calendarDateToStringDate(transaction.getAuthorizationDate()) :
                            calendarDateToStringDate(transaction.getPostDate())));

            // Get the splits for the transaction.  Create them if they don't already exist:
            List<TransactionSplit> splits = TransactionSplit.getSplitsForTransaction(transaction);
            if (splits == null) {
               splits = resolver.assignAmountsToBudgetItems(transaction, merchant, budgetItems);
            }

            // Save the transaction and associated items:
            transaction.save(INSERT_ON_DUPLICATE_UPDATE);
            if (splits != null) {
               for (TransactionSplit split : splits) {
                  getResolver().say(split.toString());
                  split.save();
               }

               // Reconcile this transaction with the forecast:
               ForecastTransaction.reconcile(forecast, transaction, splits, resolver);
            }

            i++;
         } // End for each record in the transactions file.

         // TODO: Process any significant events that occurred during reconciliation:

         // TODO: Save the import event:

      } catch (Exception e) {
         throw new RegisterException("Exception occurred while processing skipped transactions", e);
      }

      // Return the number of transactions imported:
      if (i > 0) {
         Utility.getResolver().say("Successfully reprocessed " + i + " skipped transactions in the register.");
      } else {
         Utility.getResolver().say("There were no skipped transactions in the register '" + registerName + "'.");
      }
      return forecast.getInSync();

   } // End processSkippedTransactions().
}
