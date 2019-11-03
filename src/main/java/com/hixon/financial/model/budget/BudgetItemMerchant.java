package com.hixon.financial.model.budget;

import com.hixon.financial.Utility;
import com.hixon.financial.model.DependentEntity;
import com.hixon.financial.model.EntityException;
import com.hixon.financial.model.EntityInt;
import com.hixon.financial.model.forecast.ForecastException;
import com.hixon.financial.model.register.Merchant;
import com.hixon.financial.model.register.RegisterException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// A class that represents the relationship between a budget item and a transaction in the register:
public class BudgetItemMerchant extends DependentEntity {

   /*
    * Fields in the Wells Fargo download file transaction classifier:
    */
   protected double amount = 0;
   protected int percentage = 0;
   UUID idBudgetItem = null;
   BudgetItem budgetItem = null;
   UUID idMerchant = null;

   private static final String selectQuery = "select amount, percentage, BudgetItem_idBudgetItem as idBudgetItem, " +
           "Merchant_idMerchant as idMerchant from forecastdatabase.budgetitem_merchant ";

   private static final String insertQuery = "insert into forecastdatabase.budgetitem_merchant (amount, percentage, " +
           "BudgetItem_idBudgetItem, Merchant_idMerchant) values (";

   private static final String selectItemsForMerchantQuery = "select bin_to_uuid(Budget_Item.idBudgetItem) as " +
           "'idBudgetItem', category, payee, period, Budget_Item.amount, runningBalance, startDate, numberOfPayments, "
           + "endDate, ItemType, howImportant, howOccurs, howPaid, bin_to_uuid(Budget_idBudget) as 'idBudget', " +
           "BudgetItem_Merchant.amount as merchant_amount, BudgetItem_Merchant.percentage as merchant_percentage from " +
           "ForecastDatabase.Budget_Item inner join ForecastDatabase.BudgetItem_Merchant on Budget_Item.idBudgetItem = " +
           "BudgetItem_idBudgetItem ";


   /*
    * Getters and setters for BudgetItemMerchant:
    */
   public double getAmount() {
      return amount;
   }

   public void setAmount(double amount) {
      this.amount = amount;
      setDirty(true);
   }

   public int getPercentage() {
      return percentage;
   }

   public void setPercentage(int percentage) {
      this.percentage = percentage;
      setDirty(true);
   }

   public UUID getIdBudgetItem() {
      return idBudgetItem;
   }

   public void setIdBudgetItem(UUID idBudgetItem) {
      this.idBudgetItem = idBudgetItem;
      setDirty(true);
   }

   public BudgetItem getBudgetItem() {
      return budgetItem;
   }

   public void setBudgetItem(BudgetItem budgetItem) {
      this.budgetItem = budgetItem;
      setDirty(true);
   }

   public UUID getIdMerchant() {
      return idMerchant;
   }

   public void setIdMerchant(UUID idMerchant) {
      this.idMerchant = idMerchant;
      setDirty(true);
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
      return "budget item merchant";
   }


   /*
    * Constructors for BudgetItemMerchant:
    */
   public BudgetItemMerchant(BudgetItem budgetItem, Merchant merchant, double amount, int percentage) {
      super();
      this.idBudgetItem = budgetItem.getId();
      this.idMerchant = merchant.getId();
      this.amount = amount;
      this.percentage = percentage;
      this.budgetItem = budgetItem;
      setDirty(true);
   }

   public BudgetItemMerchant(ResultSet rs) throws BudgetException, SQLException {

      if (rs == null) {
         throw new BudgetException("Result set must not be null when constructing a BudgetItemMerchant");
      }

      this.amount = rs.getDouble("amount");
      this.percentage = rs.getInt("percentage");
      this.idBudgetItem = UUID.fromString(rs.getString("BudgetItem_idBudgetItem"));
      this.idMerchant = UUID.fromString(rs.getString("BudgetItemMerchant_idMerchant"));
      this.budgetItem = null;

      setDirty(false);

   }


   /*
    * Load and save methods for BudgetItemMerchant:
    */
   public static BudgetItemMerchant getById(UUID idBudgetItemMerchant) throws SQLException, BudgetException, EntityException {
      ResultSet rs = EntityInt.getRSById(selectQuery, idBudgetItemMerchant, "Database " +
              "error occurred retrieving BudgetItemMerchant with id = " + idBudgetItemMerchant);
      return new BudgetItemMerchant(rs);
   }

   public void save() throws RegisterException, EntityException, BudgetException, SQLException {
      try {

      super.executeQueryForThis(insertQuery + amount + ", " + percentage + ", uuid_to_bin('" + idBudgetItem + "'), " +
              "uuid_to_bin('" + idMerchant + "'))", "");
      }
      catch (EntityException ee) {
         try {
            System.out.println();
            BudgetException be = new BudgetException("Database error occurred inserting BudgetItemMerchant for BudgetItem " +
                    BudgetItem.getPayeeById(idBudgetItem) + " and Merchant " + Merchant.getNameById(idMerchant) +
                    " into the database.");
            be.initCause(ee);
            throw be;
         } catch (EntityException ee2) {
            BudgetException be = new BudgetException("Database error occurred inserting BudgetItemMerchant for BudgetItem " +
                    idBudgetItem + " and Merchant " + idMerchant + " into the database.");
            be.initCause(ee);
            throw be;
         }
      }
   }


   /*
    * Main methods for BudgetItemMerchant
    */
   // Get a list of budget items that go with a merchant:
   public static List<BudgetItemMerchant> getAssignedBudgetItems(Merchant merchant) throws SQLException, BudgetException,
           ParseException, RegisterException, BudgetException {

      // Find out what budget items are associated with the merchant for this transaction:
      String query = selectItemsForMerchantQuery + "where Merchant_idMerchant = uuid_to_bin('" + merchant.getId() + "')";
      try {
         Statement statement = Utility.getDbConnection().createStatement();
         ResultSet rs = statement.executeQuery(query);
         List<BudgetItemMerchant> budgetItems = new ArrayList<BudgetItemMerchant>();
         while (rs.next()) {
            budgetItems.add(new BudgetItemMerchant(new BudgetItem(rs), merchant, rs.getDouble("merchant_amount"),
                    rs.getInt("merchant_percentage")));
         }
         return budgetItems;

      } catch (SQLException e) {
         BudgetException be = new BudgetException("Database error occurred trying to get the budget items for " +
                 "merchant ID " + merchant.getId());
         be.initCause(e);
         throw be;
      }
   }
}
