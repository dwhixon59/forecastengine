package com.hixon.financial.model.register;

import com.hixon.financial.Utility;
import com.hixon.financial.model.IndependentEntity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Calendar;
import java.util.UUID;


public class Transaction extends IndependentEntity {

   /*
    * Fields of the Transaction class:
    */
   private Calendar postDate = null;
   private Calendar authorizationDate;
   private boolean cleared = false;
   private int checkNumber = 0;
   private String payee = null;
   private double amount = 0;
   private double balance = 0;
   private UUID idRegister = null;
   private Register register = null;
   private UUID idMerchant = null;
   private String merchantPayee = null;


   public enum Headers {
      TRANSACTION_DATE, AMOUNT, CLEARED, CHECK_NUMBER, PAYEE
   }

   private static final String selectQuery = "select idTransaction, postDate, authorizationDate, amount, cleared, " +
           "checkNumber, payee, balance, Register_idRegister, Merchant_idMerchant from " +
           "forecastdatabase.transaction where ";

   private static final String insertQuery = "insert into forecastdatabase.transaction (idTransaction, " +
           "postDate, authorizationDate, amount, cleared, checkNumber, payee, balance, Register_idRegister, " +
           "Merchant_idMerchant) values(";


   /*
    * Getters and setters:
    */
   public void setidTransaction(UUID idTransaction) {
      this.id = idTransaction;
   }

   public Calendar getPostDate() {
      return postDate;
   }

   public void setPostDate(Calendar postDate) {
      this.postDate = postDate;
   }

   public boolean isCleared() {
      return cleared;
   }

   public void setCleared(boolean cleared) {
      this.cleared = cleared;
   }

   public int getCheckNumber() {
      return checkNumber;
   }

   public void setCheckNumber(int checkNumber) {
      this.checkNumber = checkNumber;
   }

   public String getPayee() {
      return payee;
   }

   public void setPayee(String payee) {
      this.payee = payee;
   }

   public double getAmount() {
      return amount;
   }

   public void setAmount(double amount) {
      this.amount = amount;
   }

   public double getBalance() {
      return balance;
   }

   public void setBalance(double balance) {
      this.balance = balance;
   }

   public UUID getIdRegister() {
      return idRegister;
   }

   public void setIdRegister(UUID idRegister) {
      this.idRegister = idRegister;
   }

   public Register getRegister() {
      return register;
   }

   public void setRegister(Register register) {
      this.register = register;
   }

   public Calendar getAuthorizationDate() {
      return authorizationDate;
   }

   public void setAuthorizationDate(Calendar authorizationDate) {
      this.authorizationDate = authorizationDate;
   }

   public UUID getIdMerchant() {
      return idMerchant;
   }

   public void setIdMerchant(UUID idMerchant) {
      this.idMerchant = idMerchant;
   }

   public static String getSelectQuery() {
      return selectQuery;
   }

   public static String getInsertQuery() {
      return insertQuery;
   }

   public void setMerchantPayee(String merchantPayee) {
      this.merchantPayee = merchantPayee;
   }

   public String getMerchantPayee() {
      return merchantPayee;
   }


   /*
    * Constructors:
    */

   public Transaction() {
      super(false);
   }

   public Transaction(Register register) {
      super(true);
      this.register = register;
   }

   public Transaction(ResultSet rs) throws SQLException {
      super(false);
      loadFromResultSet(rs);
   }


   /*
    * Load and save methods:
    */

   public void loadFromResultSet(ResultSet rs) throws SQLException {

      id = UUID.fromString(rs.getString("idTransaction"));
      postDate = Utility.SqlDateToCalendarDate(rs.getDate("postDate"));
      cleared = rs.getBoolean("cleared");
      checkNumber = rs.getInt("checkNumber");
      payee = rs.getString("payee");
      amount = rs.getDouble("amount");
      balance = rs.getDouble("balance");
      idRegister = UUID.fromString(rs.getString("Register_idRegister"));
      idMerchant = UUID.fromString(rs.getString("Merchant_idMerchant"));
   }

   // Save the transaction to the database:
   public void save() throws RegisterException {

      String query = null;
      Statement statement = null;
      ResultSet rs = null;
      try {
         query = insertQuery + "uuid_to_bin('" + id + "'), " +
                 Utility.calendarDateToSqlStringDate(postDate) + ", " +
                 Utility.calendarDateToSqlStringDate(authorizationDate) + ", " + amount + ", " + cleared + ", " +
                 checkNumber + ", \"" + payee + "\", " + balance + ", uuid_to_bin('" + register.getId() + "'), " +
                 "uuid_to_bin('" + idMerchant + "'))";
         System.out.println(query);
         statement = Utility.getDbConnection().createStatement();
         int rowCount = statement.executeUpdate(query);
         if (rowCount != 1) {
            throw new RegisterException("Problem with Insert of transaction.  Returned row count not equal to 1.");
         }
      } catch (SQLException e) {

         // If the transaction doesn't already exist in the register:
         try {
            if (statement != null) statement.close();
            if (rs != null) rs.close();
         } finally {
            RegisterException re = new RegisterException("Database error attempting to insert a transaction.");
            re.initCause(e);
            throw re;
         }
      }
   }
}

