package com.hixon.financial.model.register;

import com.hixon.financial.Utility;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Register {

   /*
    * Fields in the Register class:
    */
   private static final String selectQuery = "select bin_to_uuid(idRegister) as idRegister, Name, Account_Type, " +
           "Account_Number from ForecastDatabase.Register ";

   private static final String listQuery = "select bin_to_uuid(idRegister) as idRegister, Name, Account_Type, " +
           "Account_Number from ForecastDatabase.Register ";

   private UUID idRegister = null;
   ;
   private String registerName = null;
   ;
   private String accountType = null;
   ;
   private String accountNumber = null;


   /*
    * Getters and setters:
    */
   public UUID getId() {
      return idRegister;
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


   /*
    * Constructors:
    */
   public Register(ResultSet rs) throws RegisterException, SQLException {
      try {
         if (rs != null) {

            this.idRegister = UUID.fromString(rs.getString("idRegister"));
            this.registerName = rs.getString("name");
            this.accountType = rs.getString("account_type");
            this.accountNumber = rs.getString("account_number");

         } else {
            throw new RegisterException("Result set passed into Register(rs) is empty or null.");
         }
      } catch (SQLException e) {
         System.out.println("[SEVERE]  SQL error encountered trying to create a register from a result set.");
         if (rs != null) rs.close();
         throw e;
      }
   }

   public Register(String registerName) throws RegisterException, SQLException {
      this.registerName = registerName;
      // Find the ID of the named budget:
      PreparedStatement preparedStmt = null;
      ResultSet rs = null;
      try {
         String query = selectQuery + "where name = ?";
         preparedStmt = Utility.getDbConnection().prepareStatement(query);
         preparedStmt.setString(1, registerName);
         rs = preparedStmt.executeQuery();
         if (rs != null && rs.next()) {
            this.idRegister = UUID.fromString(rs.getString(1));
         } else {
            throw new RegisterException("Register named " + registerName + " not found in the database.");
         }
      } catch (SQLException e) {
         System.out.println("[SEVERE]  SQL error encountered trying to retrieve the register ID.");
         if (preparedStmt != null) preparedStmt.close();
         if (rs != null) rs.close();
         throw e;
      }
   }

   /*
    * Load and save methods:
    */
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

   public static List<Register> getListOf() throws RegisterException {

      try {

         System.out.println("SQL statement is: " + listQuery);
         Statement statement = Utility.getDbConnection().createStatement();
         ResultSet rs = statement.executeQuery(listQuery);
         List<Register> registers = new ArrayList<>();
         while (rs.next()) {
            Register register = new Register(rs);
            registers.add(register);
         }
         return registers;

      } catch (SQLException | RegisterException e) {
         RegisterException re = new RegisterException("Database error occurred trying to retrieve a register with the " +
                 "sql statement " + listQuery);
         re.initCause(e);
         throw re;
      }
   }

   // Public Methods:
   public void save() {
   }
}
