package com.hixon.financialApp.model.budget;

import com.hixon.financialApp.model.entity.DependentEntity;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.utility.Utility;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// A class that represents the relationship between a budget item and a transaction in the register:
public class BudgetItemMerchant extends DependentEntity {

   /*
    * Fields in the Wells Fargo download file transaction classifier:
    */
   protected double amount;
   protected int percentage;
   UUID idBudgetItem;
   BudgetItem budgetItem;
   UUID idMerchant;

   private static final String selectQuery = "select bim.amount, bim.percentage, bin_to_uuid(BudgetItem_idBudgetItem) " +
           "as 'bim.idBudgetItem', bin_to_uuid(Merchant_idMerchant) as 'bim.idMerchant' from BudgetItem_Merchant bim ";

   private static final String insertQuery = "insert into budgetitem_merchant (amount, percentage, " +
           "BudgetItem_idBudgetItem, Merchant_idMerchant) values (";

   private static final String deleteQuery = "delete from budgetitem_merchant where BudgetItem_idBudgetItem = " +
           "uuid_to_bin('";


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
   public String getUpdateByIdQuery() throws BudgetException {
      return null;
   }

   @Override
   public String getDeleteByIdQuery() {
      return null;
   }

   public static String getDeleteQuery() {
      return deleteQuery;
   }

   public static String getItemsForMerchantQuery() {

      return "select " + BudgetItem.getSelectColumns() + ", bm.amount as 'bm.amount', bm.percentage as 'bm.percentage' " +
              "from budget_item bi " +
              "inner join budgetitem_merchant bm on bi.idBudgetItem = bm.BudgetItem_idBudgetItem " +
              "inner join budget b on bi.Budget_idBudget = b.idBudget ";
   }

   @Override
   public String getPrintableTypeName() {
      return getPrintableTypeName_static();
   }

   public static String getPrintableTypeName_static() {
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

   public BudgetItemMerchant(Merchant merchant, BudgetItem budgetItem) {
      super();
      this.idBudgetItem = budgetItem.getId();
      this.idMerchant = merchant.getId();
      this.amount = 0.0;
      this.percentage = 0;
      this.budgetItem = budgetItem;
      setDirty(true);
   }

   public BudgetItemMerchant(ResultSet rs) throws BudgetException, SQLException {

      if (rs == null) {
         throw new BudgetException("Result set must not be null when constructing a BudgetItemMerchant");
      }

      this.amount = rs.getDouble("bim.amount");
      this.percentage = rs.getInt("bim.percentage");
      this.idBudgetItem = UUID.fromString(rs.getString("bim.idBudgetItem"));
      this.idMerchant = UUID.fromString(rs.getString("bim.idMerchant"));
      this.budgetItem = null;

      setDirty(false);

   }


   /*
    * Helper methods for BudgetItemMerchant:
    */
   /**
    * Validate the fields of an object.  Every entity is required to provide a method that validates the contents of
    * the entity.
    *
    * @return true if the object is valid
    */
   @Override
   public boolean isValid() { return true; }

   /**
    * Check if a budget item is in the list of budget items for the merchant.
    *
    * @param budgetItem     The budget item to check if it is in the list.
    * @param budgetItemsForMerchant The list of budget items for the merchant.
    * @return True if the selected budget item is in the list, false otherwise.
    */
   public static boolean isBudgetItemInList(BudgetItem budgetItem, List<BudgetItemMerchant> budgetItemsForMerchant) {
      for (BudgetItemMerchant budgetItemMerchant : budgetItemsForMerchant) {
         if (budgetItemMerchant.getBudgetItem().getId().equals(budgetItem.getId())) {
            return true;
         }
      }
      return false;
   }


   /*
    * Load and save methods for BudgetItemMerchant:
    */
   public static BudgetItemMerchant getById(UUID idBudgetItemMerchant) throws SQLException, BudgetException, EntityException {
      ResultSet rs = EntityInt.getRSById(selectQuery, idBudgetItemMerchant, "Database " +
              "error occurred retrieving BudgetItemMerchant with id = " + idBudgetItemMerchant);
      return new BudgetItemMerchant(rs);
   }

   public static BudgetItemMerchant getByItemAndMerchant(BudgetItem budgetItem, Merchant merchant) throws EntityException,
           SQLException, BudgetException {
      String query = selectQuery + " where BudgetItem_idBudgetItem = uuid_to_bin('" + budgetItem.getId() + "') and " +
              "Merchant_idMerchant = uuid_to_bin('" + merchant.getId() + "')";
      ResultSet rs = EntityInt.getRS(query, "Database error occurred retrieving BudgetItemMerchant for " +
              "budget item = " + budgetItem.getPayee() + " and merchant " + merchant.getName());
      BudgetItemMerchant budgetItemMerchant = null;
      if (rs.next()) {
         budgetItemMerchant = new BudgetItemMerchant(rs);
      }
      return budgetItemMerchant;
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
   // Get a list of budget items in the specified budget that are assigned to the specified merchant:
   public static List<BudgetItemMerchant> getAssignedBudgetItems(Budget budget, Merchant merchant) throws
           BudgetException {

      // Find out what budget items are associated with the given merchant in the given budget:
      String query = getItemsForMerchantQuery() + "where b.idBudget = uuid_to_bin('" + budget.getId() +
              "') and bm.Merchant_idMerchant = uuid_to_bin('" + merchant.getId() + "') order by payee ";
      try {
         Statement statement = Utility.getDbConnection().createStatement();
         ResultSet rs = statement.executeQuery(query);
         List<BudgetItemMerchant> budgetItems = new ArrayList<>();
         while (rs.next()) {
            budgetItems.add(new BudgetItemMerchant(new BudgetItem(rs), merchant, rs.getDouble("bm.amount"),
                    rs.getInt("bm.percentage")));
         }
         return budgetItems;

      } catch (SQLException e) {
         BudgetException be = new BudgetException("Database error occurred trying to get the budget items for " +
                 "merchant ID " + merchant.getId());
         be.initCause(e);
         throw be;
      }
   }

   /**
    * Get a list of merchants that are assigned to the specified budget item.
    *
    * @param budgetItem The budget item
    * @return List of BudgetItemMerchant associations for the budget item
    * @throws BudgetException if a database error occurs
    */
   public static List<BudgetItemMerchant> getAssignedMerchantsForBudgetItem(BudgetItem budgetItem) throws
           BudgetException {

      // Find out what merchants are associated with the given budget item:
      String query = "select " +
              "bin_to_uuid(bm.BudgetItem_idBudgetItem) as 'bm.idBudgetItem', " +
              "bin_to_uuid(bm.Merchant_idMerchant) as 'bm.idMerchant', " +
              "bm.amount as 'bm.amount', " +
              "bm.percentage as 'bm.percentage' " +
              "from budgetitem_merchant bm " +
              "where bm.BudgetItem_idBudgetItem = uuid_to_bin('" + budgetItem.getId() + "')";

      try {
         try (Statement statement = Utility.getDbConnection().createStatement()) {
            ResultSet rs = statement.executeQuery(query);
            List<BudgetItemMerchant> merchants = new ArrayList<>();
            while (rs.next()) {
               UUID merchantId = UUID.fromString(rs.getString("bm.idMerchant"));
               merchants.add(new BudgetItemMerchant(
                       budgetItem,
                       Merchant.getById(merchantId),
                       rs.getDouble("bm.amount"),
                       rs.getInt("bm.percentage")));
            }
            return merchants;
         }

      } catch (SQLException | EntityException | RegisterException e) {
         BudgetException be = new BudgetException("Database error occurred trying to get the merchants for " +
                 "budget item ID " + budgetItem.getId());
         be.initCause(e);
         throw be;
      }
   }

   // Get a list of budget items in the specified budget that are assigned to the specified merchant:
   public static List<BudgetItemMerchant> getAssignedUnexpiredBudgetItems(Budget budget, Merchant merchant) throws
           BudgetException {

      // Find out what budget items are associated with the given merchant in the given budget:
      String query =
              getItemsForMerchantQuery() +
              "where " +
                      "b.idBudget = uuid_to_bin('" + budget.getId() + "') and " +
                      "bm.Merchant_idMerchant = uuid_to_bin('" + merchant.getId() + "') and " +
                      "(bi.endDate is null or bi.endDate > now()) " +
                      "order by payee ";
      try {
         Statement statement = Utility.getDbConnection().createStatement();
         ResultSet rs = statement.executeQuery(query);
         List<BudgetItemMerchant> budgetItemMerchants = new ArrayList<>();
         while (rs.next()) {
            budgetItemMerchants.add(new BudgetItemMerchant(new BudgetItem(rs), merchant, rs.getDouble("bm.amount"),
                    rs.getInt("bm.percentage")));
         }
         return budgetItemMerchants;

      } catch (SQLException e) {
         BudgetException be = new BudgetException("Database error occurred trying to get the budget items for " +
                 "merchant ID " + merchant.getId());
         be.initCause(e);
         throw be;
      }
   }

   public static List<BudgetItemMerchant> getAssignedExpiredBudgetItems(Budget budget, Merchant merchant) throws
           BudgetException {

      // Find out what expired budget items are associated with the given merchant in the given budget:
      String query =
              getItemsForMerchantQuery() +
                      "where " +
                      "b.idBudget = uuid_to_bin('" + budget.getId() + "') and " +
                      "bm.Merchant_idMerchant = uuid_to_bin('" + merchant.getId() + "') and " +
                      "(bi.endDate is not null and bi.endDate <= now()) " +
                      "order by payee ";
      try {
         Statement statement = Utility.getDbConnection().createStatement();
         ResultSet rs = statement.executeQuery(query);
         List<BudgetItemMerchant> budgetItemMerchants = new ArrayList<>();
         while (rs.next()) {
            budgetItemMerchants.add(new BudgetItemMerchant(new BudgetItem(rs), merchant, rs.getDouble("bm.amount"),
                    rs.getInt("bm.percentage")));
         }
         return budgetItemMerchants;

      } catch (SQLException e) {
         BudgetException be = new BudgetException("Database error occurred trying to get the budget items for " +
                 "merchant ID " + merchant.getId());
         be.initCause(e);
         throw be;
      }
   }

   // Delete a budget item from a merchant:
   public static void deleteBudgetItemFromMerchant(BudgetItemMerchant budgetItemMerchant)
           throws BudgetException, EntityException, RegisterException {
        try {
             String query = getDeleteQuery() + budgetItemMerchant.getIdBudgetItem() + "') and Merchant_idMerchant = " +
                     "uuid_to_bin('" + budgetItemMerchant.getIdMerchant() + "')";
            try (Statement statement = Utility.getDbConnection().createStatement()) {
                statement.executeUpdate(query);
            }
        } catch (SQLException e) {
           BudgetException be = new BudgetException("Database error occurred trying to delete the budget item " +
           BudgetItem.getById(budgetItemMerchant.getIdBudgetItem()).getPayee() + " from merchant " +
                   Merchant.getById(budgetItemMerchant.getIdMerchant()));
           be.initCause(e);
           throw be;
        }
   }

   /**
    * Delete the association between a budget item and a merchant.
    *
    * @param budgetItem The budget item
    * @param merchant The merchant
    * @throws BudgetException if a database error occurs
    * @throws EntityException if a database error occurs
    * @throws RegisterException if a database error occurs
    */
   public static void deleteByItemAndMerchant(BudgetItem budgetItem, Merchant merchant)
           throws BudgetException, EntityException, RegisterException {
        try {
             String query = getDeleteQuery() + budgetItem.getId() + "') and Merchant_idMerchant = " +
                     "uuid_to_bin('" + merchant.getId() + "')";
            try (Statement statement = Utility.getDbConnection().createStatement()) {
                statement.executeUpdate(query);
            }
        } catch (SQLException e) {
           BudgetException be = new BudgetException("Database error occurred trying to delete the association between budget item " +
                   budgetItem.getPayee() + " and merchant " + merchant.getName());
           be.initCause(e);
           throw be;
        }
   }
}
