package com.hixon.financial.model.register;

import com.hixon.financial.Utility;
import com.hixon.financial.model.EntityException;
import com.hixon.financial.model.FinancialAppEntityInt;
import com.hixon.financial.model.IndependentEntity;
import com.hixon.financial.model.budget.BudgetException;
import com.hixon.financial.model.budget.BudgetItem;
import com.hixon.financial.model.budget.BudgetItemMerchant;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class Merchant extends IndependentEntity {

   /*
    * Fields in the Merchant class:
    */
   public static final String selectQuery = "select bin_to_uuid(idMerchant) as 'idMerchant', name, askAlways from " +
           "forecastdatabase.merchant ";
   public static final String selectJoinPayeeQuery = "select bin_to_uuid(A.idMerchant) as 'idMerchant', A.name, " +
           "B.payee, A.askAlways from forecastdatabase.merchant A inner join forecastdatabase.merchant_payee B on " +
           "A.idMerchant = B.Merchant_idMerchant ";
   public static final String insertQuery = "insert into forecastdatabase.merchant (idMerchant, name, askAlways) values (";

   private String name = null;
   boolean askAlways = false;
   private List<MerchantPayee> merchantPayees = new LinkedList<>();

   public static Merchant getById(UUID idMerchant) {
      return null;
   }


   /*
    * Getters and setters:
    */
   public String getName() {
      return name;
   }

   public void setName(String name) {
      dirty = true;
      this.name = name;
   }

   public boolean isAskAlways() {
      return askAlways;
   }

   public void setAskAlways(boolean askAlways) {
      this.askAlways = askAlways;
   }

   public List<MerchantPayee> getPayees() {
      return merchantPayees;
   }


   /*
    * Constructors:
    */
     // Create a new merchant with the provided name:
   public Merchant(String merchantName) {
      super(true);
      name = merchantName;
   }

   // Create and load an existing merchant from the database:
   public Merchant(ResultSet rs) throws RegisterException {
      super(false);
      loadFromResultSet(rs);
   }


   /*
    * Load and save methods:
    */
   private void loadFromResultSet(ResultSet rs) throws RegisterException {
      try {

         if (rs == null) throw new RegisterException("Result set passed into loadFromResultSet from must not be null.");
         this.id = UUID.fromString(rs.getString("idMerchant"));
         this.name = rs.getString("name");
         this.askAlways = rs.getBoolean("askAlways");
         dirty = false;

      } catch (SQLException e) {

         RegisterException re = new RegisterException("Error reading in the Merchant-Payee row for " + rs.toString());
         re.initCause(e);
         throw (re);
      }
   }  // End loadFromResultSet().


   public static Merchant loadFromCSV(String merchantName) throws RegisterException {

      String[] values = merchantName.split(",");
      if (values.length < 1) throw new RegisterException("Empty string passed into Merchant.loadFromCSV().");
      Merchant merchant = new Merchant(merchantName);
      if (values.length > 1) {
        merchant.setAskAlways(values[1].equalsIgnoreCase("y"));
      }
      System.out.println("Created new merchant " + merchantName);
      return merchant;

   }

   public static Merchant getByPayee(String payee) throws RegisterException {

      // Find the ID of the merchant that uses the passed in payee:
      String query = selectJoinPayeeQuery + "where B.payee = \"" + payee + "\"";
      try {
         Statement statement = Utility.getDbConnection().createStatement();
         ResultSet rs = statement.executeQuery(query);
         if (rs.next()) {
            Merchant merchant = new Merchant(rs);
            return merchant;
         } else {
            return null;
         }
      } catch (SQLException e) {
         RegisterException re = new RegisterException("Database error occurred trying to get the Merchant for the " +
                 "payee " + payee);
         re.initCause(e);
         throw re;
      }
   }

   public static Merchant getByName(String name) throws RegisterException {

      // Find the ID of the merchant that uses the passed in name:
      String query = selectQuery + "where name = \"" + name + "\"";
      try {
         Statement statement = Utility.getDbConnection().createStatement();
         ResultSet rs = statement.executeQuery(query);
         if (rs.next()) {
            Merchant merchant = new Merchant(rs);
            return merchant;
         } else {
            return null;
         }
      } catch (SQLException e) {
         RegisterException re = new RegisterException("Database error occurred trying to get the Merchant for the " +
                 "name " + name);
         re.initCause(e);
         throw re;
      }
   }

   public static Merchant getByNameLike(String name) throws RegisterException {
      // Find the ID of the merchant that uses the passed in name:
      String query = selectQuery + "where name like \"" + name + "%\"";
      try {
         Statement statement = Utility.getDbConnection().createStatement();
         ResultSet rs = statement.executeQuery(query);
         if (rs.next()) {
            Merchant merchant = new Merchant(rs);
            return merchant;
         } else {
            return null;
         }
      } catch (SQLException e) {
         RegisterException re = new RegisterException("Database error occurred trying to get the Merchant for the " +
                 "name " + name);
         re.initCause(e);
         throw re;
      }
   }

   // Get the name of a Merchant:
   public static String getNameById(UUID idMerchant) throws EntityException, SQLException {
      ResultSet rs = FinancialAppEntityInt.getRSById(selectQuery, idMerchant, "Database error occurred" +
              " trying to get the merchant with id = " + idMerchant);
      return rs.getString("name");
   }

   // Create a new payee associated with this merchant:
   public MerchantPayee addPayee(String payee) {

      MerchantPayee merchantPayee = new MerchantPayee(payee, id);
      merchantPayees.add(merchantPayee);
      return merchantPayee;
   }

   // Save this merchant if dirty, and any dirty merchant-payees:
   public void save() throws RegisterException, EntityException {

      // Save the merchant:
      super.save(insertQuery + "uuid_to_bin('" + id + "'), \"" + name + "\", " + askAlways + ")",
              "Problem with Insert of merchant.  Returned row count not equal to 1.");

      // Save the merchant payees:
      for (MerchantPayee merchantPayee : merchantPayees) {
         merchantPayee.save();
      }
   }

   // Add a budget item to the merchant:
   public BudgetItemMerchant addBudgetItem(BudgetItem budgetItem, double amount, int percentage) throws EntityException,
           RegisterException, BudgetException, SQLException {
      BudgetItemMerchant budgetItemMerchant = new BudgetItemMerchant(budgetItem, this, amount, percentage);
      budgetItemMerchant.save();
      return budgetItemMerchant;
   }
}
