package com.hixon.financial.model.register;

import com.hixon.financial.Utility;
import com.hixon.financial.model.EntityException;
import com.hixon.financial.model.EntityInt;
import com.hixon.financial.model.IndependentEntity;
import com.hixon.financial.model.budget.BudgetException;
import com.hixon.financial.model.forecast.ForecastException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
   public String getUpdateQuery() throws BudgetException {
      return null;
   }

   @Override
   public String getDeleteQuery() {
      return null;
   }

   @Override
   public String getEntityTypeName() {
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

}
