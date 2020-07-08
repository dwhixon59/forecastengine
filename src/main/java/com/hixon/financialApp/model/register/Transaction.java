package com.hixon.financialApp.model.register;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.IndependentEntity;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.utility.Utility;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Calendar;
import java.util.UUID;

import static com.hixon.financialApp.model.entity.EntityInt.getRS;
import static com.hixon.financialApp.model.entity.EntityInt.getRSById;
import static com.hixon.financialApp.utility.Utility.*;


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
           "bin_to_uuid(Register_idRegister) as idRegister, bin_to_uuid(Merchant_idMerchant) as idMerchant" +
           " from forecastdatabase.transaction ";

   private static final String insertQuery = "insert into forecastdatabase.transaction (idTransaction, " +
           "postDate, authorizationDate, amount, cleared, checkNumber, payee, balance, isImproper, importRecordId, " +
           "Register_idRegister, Merchant_idMerchant) values(";

   @Override
   public String getInsertQuery() {
      return insertQuery + "uuid_to_bin('" + id + "'), " + Utility.calendarDateToSqlDateString(postDate) + ", " +
              Utility.calendarDateToSqlDateString(authorizationDate) + ", " + amount + ", " + cleared + ", " +
              checkNumber + ", \"" + payee + "\", " + balance + ", " + isImproper + ", \"" + importRecordId +
              "\", uuid_to_bin('" + register.getId() + "'), uuid_to_bin('" + getIdMerchant() + "'))";
   }

   @Override
   public String getInsertOnDuplicateUpdateQuery() throws BudgetException {
      return getInsertQuery() + " on duplicate key update postDate = " + Utility.calendarDateToSqlDateString(postDate) +
              ", authorizationDate = " + Utility.calendarDateToSqlDateString(authorizationDate) + ", amount = " + amount
              + ", cleared = " + cleared + ", checkNumber = " + checkNumber + ", payee = \"" + payee + "\", balance = "
              + balance + ", isImproper = " + isImproper + ", importRecordId = \"" + importRecordId + "\", " +
              "Register_idRegister = uuid_to_bin('" + register.getId() + "'), Merchant_idMerchant = uuid_to_bin('" +
              getIdMerchant() + "')";
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
      return deleteQuery + "idTransaction = uuid_to_bin('" + id + "')";
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
      if (idMerchant == null && merchant != null) {
         idMerchant = merchant.getId();
      }
      return idMerchant;
   }

   public void setIdMerchant(UUID idMerchant) {
      this.idMerchant = idMerchant;
   }

   public Merchant getMerchant() throws EntityException, RegisterException {
      if (merchant == null) {
         merchant = Merchant.getById(idMerchant);
      }
      return merchant;
   }

   public void setMerchant(Merchant merchant) {
      this.merchant = merchant;
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

   // Constructor for importing a provisional transaction:
   public Transaction(Register register, String postDate, String payee, String credit, String debit, String merchantPayee)
           throws ParseException {

      super(true);
      this.postDate = (stringDateSlashToCalendarDate(postDate));
      authorizationDate = null;
      cleared = false;
      checkNumber = 0;
      this.payee = payee;
      if (!credit.trim().isEmpty()) {
         this.amount = parseDollarAmount(credit);
      } else {
         this.amount = -parseDollarAmount(debit);
      }
      balance = 0;
      isImproper = false;
      importRecordId = null;
      this.register = register;
      idRegister = register.getId();
      merchant = null;
      idMerchant = null;
      this.merchantPayee = merchantPayee;
   }


   /*
    * Load and save methods:
    */

   public static Transaction getById(UUID idTransaction) throws EntityException, SQLException {
      return new Transaction(getRSById(selectQuery + "where idTransaction =", idTransaction,
              "Database error encountered trying to retrieve a transaction."));
   }

   public static Transaction getByImportRecordId(String importRecordId) throws EntityException, SQLException {
      ResultSet rs = getRS(selectQuery + "where importRecordId = \"" + importRecordId + "\"",
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
   public String toStringSummary() {
      String authDate = (authorizationDate != null) ? "\n\tAuthorization date = " +
              Utility.calendarDateToStringDate(authorizationDate) : "";
      String checkNumberString = (checkNumber != 0) ? "\n\tCheck number = " + checkNumber : "";
      String s = null;
      s = "\tPost date = " + Utility.calendarDateToStringDate(postDate) +
               authDate +
              "\n\tMerchant = " + merchant.getName() +
              "\n\tamount = " + formatDollarAmount(amount) +
              "\n\tCleared = " + cleared +
              "\n\tOriginal Payee = " + payee +
              checkNumberString;
      return s;
   }

   public String toString() {
      String s = null;
      try {
         String merchantName;
         if (merchant != null) {
            merchantName = merchant.getName();
         } else {
            merchantName = "null";
         }
         String registerName;
         if (register != null) {
            registerName = register.getRegisterName();
         } else {
            registerName = "null";
         }
         s = "Transaction:  Post date = " + Utility.calendarDateToStringDate(postDate) + ", Authorization date = " +
                 Utility.calendarDateToStringDate(authorizationDate) + ", Cleared = " + cleared + ", Check number = " +
                 checkNumber + ", Merchant = " + merchantName + ", amount = " + formatDollarAmount(amount) +
                 ",\n\tPayee = " + payee + ", Balance = " + balance + ", Register = " + getRegister().getRegisterName()
                 + ", Merchant payee = " + merchantPayee + ", Disputed = " + isImproper;
      } catch (EntityException | SQLException | RegisterException e) {
         e.printStackTrace();
      }
      return s;
   }

   public String provisionalToString() {
      String s = null;
      try {
         String postDateString = "\tPost date = " + Utility.calendarDateToStringDate(postDate) + "\n";
         String checkNumberString = (checkNumber != 0) ? "\tCheck number = " + checkNumber + "\n" : "";
         Merchant merchant = getMerchant();
         String merchantString = (merchant != null) ? "\tMerchant = " + merchant.getName() + "\n" : "";
         String amountString = "\tAmount = " + formatDollarAmount(amount) + "\n";
         String balanceString = (balance > 0.0) ? "\t" + formatDollarAmount(balance) + "\n" : "";
         Register register = getRegister();
         String registerNameString = (register != null) ? "\tRegister name = " + register.getRegisterName() + "\n" : "";
         String merchantPayeeString = (merchantPayee != null) ? "\tMerchant payee = " + merchantPayee + "\n" : "";
         String disputedString = (isImproper) ? "\tDisputed transaction.\n" : "";
         s = new StringBuilder().append("Transaction:  ").append(importRecordId).append("\n").append(postDateString).
                 append(checkNumberString).append(merchantString).append(amountString).append(balanceString).
                 append(registerNameString).append(merchantPayeeString).append(disputedString).toString();
      } catch (EntityException | SQLException | RegisterException e) {
         e.printStackTrace();
      }
      return s;
   }


   /*
    * Main methods:
    */
   // Find provisional transactions based on the merchant and amount and return the first one found:
   public static Transaction getFirstProvisionalTransaction(UUID idMerchant, double amount) throws EntityException,
           SQLException {

      ResultSet rs = getRS(selectQuery + " where Merchant_idMerchant = uuid_to_bin('" + idMerchant +
              "') and amount = " + amount + " and cleared = false order by postDate asc", "Database error" +
              " occured while trying to retrieve any provisional transactions that match a merchant and amount.");
      Transaction transaction = null;
      if (rs != null) {
         if (rs.next()) {
            transaction = new Transaction(rs);
         }
      }
      return transaction;
   }


   // Get a list of transactions that were previously skipped during the importRegisterTransactions() process.  We know
   // they were skipped because either there is no merchant assigned, or there are no splits assigned, or the
   // transactions has not been reconciled:
   public static ResultSet getSkippedTransactionsWrtForecast(Forecast forecast) throws EntityException {
      Calendar startDate = forecast.getStartDate();
      Utility.setToLastBusinessDayBefore(startDate);
      String query = getSelectQuery() + " where postDate >= " + Utility.calendarDateToSqlDateString(startDate) + " " +
              "and idTransaction not in " +
              "(select idTransaction from Transaction " +
              "inner join Transaction_Split on idTransaction = Transaction_idTransaction " +
              "inner join Forecast_Transaction_Split on Transaction_idTransaction = Transaction_Split_idTransaction and " +
              "BudgetItem_idBudgetItem = Transaction_Split_idBudgetitem) " +
              "order by postDate asc";
      return getRS(query, "attempting to retieve a list of transactions that were previously " +
              "skipped during the import process.");
   }


   // Combine a cleared transaction with an uncleared transaction:
   public Boolean reconcileWithProvisional() throws EntityException, SQLException {
      Boolean result = false;
      Transaction provisionalTransaction = getFirstProvisionalTransaction(merchant.getId(), amount);
      if (provisionalTransaction != null) {
         setId(provisionalTransaction.getId());
         result = true;
      }
      return result;
   }
}

