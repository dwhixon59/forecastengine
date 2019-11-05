package com.hixon.financial.model;

import com.hixon.financial.Utility;
import com.hixon.financial.model.budget.BudgetException;
import com.hixon.financial.model.forecast.ForecastException;
import com.hixon.financial.model.register.RegisterException;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public abstract class Entity implements EntityInt {

   /*
    *  Fields:
    */
   // The dirty bit for save operations:
   private boolean isDirty;


   /*
    *  Getters and setters:
    */
   @Override
   public boolean isDirty() {
      return isDirty;
   }
   @Override
   public void setDirty(boolean dirty) {
      this.isDirty = dirty;
   }


   /*
    *  Constructors:
    */
   public Entity() {
      this.isDirty = false;
   }


   /*
    *  Load and save operations:
    */
   // The generic load() method is two or three constructors.  One from a result set, one from a CSV line and a default
   // or default-like constructor;

   // The generic save operation:
   @Override
   public void save(SaveMethod method) throws EntityException, RegisterException, BudgetException, SQLException, ForecastException {

      if (isDirty()) {
         switch (method) {
            case INSERT:
               executeQueryForThis(getInsertQuery(), " inserting a " + getPrintableEntityTypeName());
               break;
            case UPDATE:
               executeQueryForThis(getUpdateByIdQuery(), "Trying to update a " + getPrintableEntityTypeName() + ".");
               break;
            case INSERT_ON_DUPLICATE_UPDATE:
               executeQueryForThis(getInsertOnDuplicateUpdateQuery(), getPrintableEntityTypeName());
               break;

            case INSERT_ON_DUPLICATE_SKIP:
               try {
                  executeQueryForThis(getInsertQuery(), " inserting a " + getPrintableEntityTypeName());
               } catch (EntityException e) {
                  SQLException se = (SQLException) e.getCause();
                  if (!se.getSQLState().equalsIgnoreCase("SQL92"))
                     throw new NotImplementedException();
               }
         }
      }
      else {
         //System.out.println("Attempt to save an entity of type " + getEntityTypeName() + " that wasn't dirty.  Skipping");
      }
      isDirty = false;
   }

   // The generic update operation:
   @Override
   public void update() throws EntityException, BudgetException, SQLException {
      throw new EntityException("Update method not implemented yet.");
   }

   // The generic delete operation:
   @Override
   public void delete() throws EntityException, RegisterException {
         EntityInt.executeQuery(getDeleteByIdQuery(), getPrintableEntityTypeName());
   }


   /*
    *  Main methods:
    */
   // Execute a query using the SQL call executeUpdate():
   public void executeQueryForThis(String query, String exceptionMessage) throws RegisterException, EntityException {

      Statement statement = null;
      ResultSet rs = null;

      try {
         if (isDirty()) {
            statement = Utility.getDbConnection().createStatement();
            statement.executeUpdate(query);
            setDirty(false);
         } else {
            //System.out.println("Attempt to execute a query on an entity of type " + getEntityTypeName() + " that isn't dirty.  Skipped.");
         }
      } catch (SQLException e) {

         // Close the database connections if possible:
         try {
            if (statement != null) statement.close();
            if (rs != null) rs.close();
         } finally {
            EntityException ee = new EntityException("Database error occured " + exceptionMessage + ".  \nSQL " +
                    "statement was " + query);
            ee.initCause(e);
            throw ee;
         }
      }
   }

} // End class FinancialAppEntity.
