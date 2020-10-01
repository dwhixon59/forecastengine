package com.hixon.financialApp.model.budget;

import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.entity.IndependentEntity;
import com.hixon.financialApp.model.forecast.ForecastException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class Budget extends IndependentEntity {

   /*
    * Fields:
    */
   private String budgetName = null;
   private static final String selectQuery = "select bin_to_uuid(idBudget) as idbudget, name from " +
           "budget ";


   /*
   * Getters and setters:
   */

   public String getBudgetName() {
      return budgetName;
   }
   public void setBudgetName(String budgetName) {
      this.budgetName = budgetName;
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

   @Override
   public String getPrintableEntityTypeName() {
      return "budget";
   }


   /*
    * Constructors:
    */
   public Budget() {
      super(false);
   }

   public Budget(ResultSet rs) throws SQLException, BudgetException {
      super(false);
      try {
         if (rs == null) throw new BudgetException("Result set to Budget.loadFromResultSet() from must not be null.");

         id = UUID.fromString(rs.getString(1));
         budgetName = rs.getString("name");
         setDirty(false);

      } catch (SQLException e) {
         System.out.println("Error reading in the Budget Item row.");
         e.printStackTrace();
         throw e;
      }
   }


   /*
    * Load and save methods:
    */
   public static Budget getById(UUID idBudget) throws BudgetException, EntityException, SQLException {
      ResultSet rs = EntityInt.getRSById(selectQuery + "where idBudget = ", idBudget,
              "No budget found with id " + idBudget);
      return new Budget(rs);
   }

   public static Budget getByName(String name) throws BudgetException, EntityException, SQLException {
      ResultSet rs = EntityInt.getSingletonRS(selectQuery + "where name = '" + name + "'",
              "No budget found with name " + name);
      return new Budget(rs);
   }


   /*
    * Main methods:
    */

}
