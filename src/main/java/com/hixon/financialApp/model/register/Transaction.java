package com.hixon.financialApp.model.register;

import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.entity.IndependentEntity;
import com.hixon.financialApp.model.budget.BudgetException;

import java.sql.ResultSet;
import java.sql.SQLException;
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
   private boolean isImproper = false;
   private String importRecordId = null;
   private Merchant merchant;

   public enum Headers {
      TRANSACTION_DATE, AMOUNT, CLEARED, CHECK_NUMBER, PAYEE
   }

   private static final String selectQuery = "select bin_to_uuid(idTransaction) as idTransaction, postDate, " +
           "authorizationDate, amount, cleared, checkNumber, payee, balance, isImproper, importRecordId, " +
           "bin_to_uuid(Register_idRegister) as idRegister, bin_to_uuid(Merchant_idMerchant) as idMerchant from " +
           "forecastdatabase.transaction ";

   private static final String insertQuery = "insert into forecastdatabase.transaction (idTransaction, " +
           "postDate, authorizationDate, amount, cleared, checkNumber, payee, balance, isImproper, importRecordId, " +
           "Register_idRegister, Merchant_idMerchant) values(";
   @Override
   public String getInsertQuery() {
      return insertQuery + "uuid_to_bin('" + id + "'), " + Utility.calendarDateToSqlDateString(postDate) + ", " +
              Utility.calendarDateToSqlDateString(authorizationDate) + ", " + amount + ", " + cleared + ", " +
              checkNumber + ", \"" + payee + "\", " + balance + "," + isImproper + ", \"" + importRecordId +
              "\", uuid_to_bin('" + register.getId() + "'), uuid_to_bin('" + idMerchant + "'))";
   }

   private static final String insertOnDuplicateUpdateQuery = "";
   @Override
   public String getInsertOnDuplicateUpdateQuery() throws BudgetException {
      return null;
   }

   private static final String updateQuery = "update forecastdatabase.transaction set idTransaction = ?, " +
           "set postdate = ?, set authorizationDate = ?, set amount = ?, set cleared = ?, set checkNumber = ?, " +
           "set payee = ?, set balance = ?, set isImproper = ?, set importRecordId = ?, set Register_idRegister = ?, " +
           "set Merchant_idMerchant = ? where ";
   @Override
   public String getUpdateByIdQuery() {
      return updateQuery;
   }

   private static final String deleteQuery = "delete from forecastdatabase.transaction where ";

   @Override
   public String getDeleteByIdQuery() {
      return null;
   }

   @Override
   public String getPrintableEntityTypeName() {
      return "transaction";
   }


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

   public Register getRegister() throws EntityException, SQLException, RegisterException {
      if (register == null) {
         register = Register.getById(idRegister);
      }
      return register;
   }

   public void setRegister(Register register) {
      this.register = register;
   }

   public Calendar getAuthorizationDate() {
      return authorizationDate;
   }

   public Calendar getDate() {
      return (authorizationDate != null) ? authorizationDate : postDate;
   }

   public void setAuthorizationDate(Calendar authorizationDate) {
      this.authorizationDate = authorizationDate;
   }

   public UUID getIdMerchant() {
      return idMerchant;
   }

   public Merchant getMerchant() throws EntityException, RegisterException {
      if (merchant == null) {
         merchant = Merchant.getById(idMerchant);
      }
      return merchant;
   }

   public void setIdMerchant(UUID idMerchant) {
      this.idMerchant = idMerchant;
   }

   public static String getSelectQuery() {
      return selectQuery;
   }

   public void setMerchantPayee(String merchantPayee) {
      this.merchantPayee = merchantPayee;
   }

   public String getMerchantPayee() {
      return merchantPayee;
   }

   public boolean getIsImproper() {
      return isImproper;
   }

   public void setIsImproper(boolean isImproper) {
      this.isImproper = isImproper;
   }

   public String getImportRecordId() {
      return importRecordId;
   }

   public void setImportRecordId(String importRecordId) {
      this.importRecordId = importRecordId;
   }


   /*
    * Constructors:
    */
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

   public static Transaction getById(UUID idTransaction) throws EntityException, SQLException {
      return new Transaction(EntityInt.getRSById(selectQuery + "where idTransaction =", idTransaction,
              "Database error encountered trying to retrieve a transaction."));
   }

   public static Transaction getByImportRecordId(String importRecordId) throws EntityException, SQLException {
      ResultSet rs = EntityInt.getRS(selectQuery + "where importRecordId = \"" + importRecordId + "\"",
              "Database error encountered trying to retrieve a transaction by importRecordId.");
      Transaction transaction = null;
      if (rs.next()) {
         //TODO: create a log message for this:  System.out.println("Transaction \"" + importRecordId + "\" already imported.  Skipping.");
         transaction = new Transaction(rs);
      }
      return transaction;
   }

   public void loadFromResultSet(ResultSet rs) throws SQLException {

      id = UUID.fromString(rs.getString("idTransaction"));
      postDate = Utility.SqlDateToCalendarDate(rs.getDate("postDate"));
      authorizationDate = Utility.SqlDateToCalendarDate(rs.getDate("authorizationDate"));
      cleared = rs.getBoolean("cleared");
      checkNumber = rs.getInt("checkNumber");
      payee = rs.getString("payee");
      amount = rs.getDouble("amount");
      balance = rs.getDouble("balance");
      isImproper = rs.getBoolean("isImproper");
      importRecordId = rs.getString("importRecordId");
      idRegister = UUID.fromString(rs.getString("idRegister"));
      idMerchant = UUID.fromString(rs.getString("idMerchant"));
   }


   /*
    * Helper methods:
    */
   @Override
   public String toString() {
      String s = null;
      try {
         s = "Transaction:  Post date = " + Utility.calendarDateToStringDate(postDate) + ", Authorization date = " +
                 Utility.calendarDateToStringDate(authorizationDate) + ", Cleared = " + cleared + ", Check number = " +
                 checkNumber + ", Payee = " + payee + ", amount = " + amount + ", Balance = " + balance + ", Register = "  +
                 getRegister().getRegisterName() + ", Merchant = " + merchantPayee + ", Disputed = " + isImproper;
      } catch (EntityException | SQLException | RegisterException e) {
         e.printStackTrace();
      }
      return s;
   }


   /*
    * Main methods:
    */
}

