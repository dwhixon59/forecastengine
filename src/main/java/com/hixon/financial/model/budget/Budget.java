package com.hixon.financial.model.budget;

import com.hixon.financial.model.EntityException;
import com.hixon.financial.model.FinancialAppEntityInt;
import com.hixon.financial.model.IndependentEntity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class Budget extends IndependentEntity {

   /*
    * Fields:
    */
   private String budgetName = null;
   private static final String selectQuery = "select bin_to_uuid(id), name from ForecastDatabase.Budget ";


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
   * Getters and setters:
   */
   public String getBudgetName() {
      return budgetName;
   }
   public void setBudgetName(String budgetName) {
      this.budgetName = budgetName;
      setDirty(true);
   }


   /*
    * Load and save methods:
    */
   public static Budget getById(UUID idBudget) throws BudgetException, EntityException, SQLException {
      ResultSet rs = FinancialAppEntityInt.getRSById(selectQuery, idBudget, "No budget found with id "
              + idBudget);
      return new Budget(rs);
   }

   public static Budget getByName(String name) throws BudgetException, EntityException, SQLException {
      ResultSet rs = FinancialAppEntityInt.getRS(selectQuery + "where name = '" + name + "'",
              "No budget found with name " + name);
      return new Budget(rs);
   }


   /*
    * Main methods:
    */

}
