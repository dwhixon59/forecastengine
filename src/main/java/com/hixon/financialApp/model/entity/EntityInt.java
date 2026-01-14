package com.hixon.financialApp.model.entity;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.utility.Utility;
import org.apache.commons.lang3.NotImplementedException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

public interface EntityInt {

   // The dirty bit for save operations:
   boolean isDirty();

   void setDirty(boolean dirty);

   String getPrintableTypeName();

   // The base CRUD queries:
   String getInsertQuery() throws BudgetException, ForecastException, EntityException, SQLException, NotImplementedException;
   String getInsertOnDuplicateUpdateQuery() throws BudgetException, EntityException, SQLException, ForecastException;
   String getUpdateByIdQuery() throws BudgetException;
   String getDeleteByIdQuery();

   // The save operation:
   enum SaveMethod {INSERT, UPDATE, INSERT_ON_DUPLICATE_UPDATE, INSERT_ON_DUPLICATE_SKIP}
   void save(SaveMethod method) throws EntityException, SQLException;

   // The insert operation:
   void insert() throws ForecastException, BudgetException, EntityException, RegisterException, SQLException;

   // The update operation:
   void update() throws EntityException, BudgetException, SQLException, RegisterException;

   // The delete operation:
   void delete() throws EntityException, RegisterException;

   // Execute a query using the SQL call executeUpdate observing the dirty flag:
   void executeQueryForThis(String query, String exceptionMessage) throws RegisterException, EntityException;

   // Execute a query using the SQL call executeUpdate():
   static int executeUpdate(String query, String exceptionMessage) throws EntityException {

      Statement statement = null;
      ResultSet rs = null;

      int rowCount;
      try {
         statement = Utility.getDbConnection().createStatement();
         rowCount = statement.executeUpdate(query);
      } catch (SQLException e) {
         try {
            if (statement != null) statement.close();
            if (rs != null) rs.close();
         } finally {
            EntityException ee = new EntityException("Database error occurred " + exceptionMessage + ".  \nSQL " +
                    "statement was " + query);
            ee.initCause(e);
            throw ee;
         }
      }
      return rowCount;
   }

   // The generic get operation:
   // WARNING: This method returns a ResultSet that must be closed by the caller
   // Consider refactoring to use a callback pattern instead
   static ResultSet getRS(String selectQuery, String exceptionMessage) throws EntityException {

      Statement statement = null;
      ResultSet rs = null;

      try {
         statement = Utility.getDbConnection().createStatement();
         rs = statement.executeQuery(selectQuery);
         return rs;
      } catch (SQLException e) {
         try {
            if (statement != null) statement.close();
            if (rs != null) rs.close();
         } finally {
            EntityException ee = new EntityException("Database error occurred " + exceptionMessage + ".  \nSQL " +
                    "statement was " + selectQuery);
            ee.initCause(e);
            throw ee;
         }
      }
   }

   // The generic get single item operation:
   static ResultSet getSingletonRS(String selectQuery, String exceptionMessage) throws EntityException {

      ResultSet rs = null;
      try {
         rs = getRS(selectQuery, exceptionMessage);
         return (rs.next()) ? rs : null;
      } catch (SQLException e) {
         try {
            if (rs != null) rs.close();
         } finally {
            EntityException ee = new EntityException("Database error occurred " + exceptionMessage + ".  \nSQL " +
                    "statement was " + selectQuery);
            ee.initCause(e);
            throw ee;
         }
      }
   }

   // The generic get by ID operation:
   static ResultSet getRSById(String selectQuery, UUID id, String exceptionMessage) throws EntityException {
      return getSingletonRS(selectQuery + " uuid_to_bin('" + id + "')", exceptionMessage);
   }

   // Delete multiple rows in the database:
   static void deleteMultiple(String deleteQuery, String exceptionMessage) throws EntityException {
      Statement statement = null;
      try {
         statement = Utility.getDbConnection().createStatement();
         int rowCount = statement.executeUpdate(deleteQuery);
         if (rowCount == 0) {
            throw new EntityException(exceptionMessage + "  Row count for delete = 0.");
         }
      } catch (SQLException e) {
         try {
            if (statement != null) statement.close();
         } finally {
            EntityException ee = new EntityException("Database error occurred " + exceptionMessage + ".  \nSQL " +
                    "statement was " + deleteQuery);
            ee.initCause(e);
            throw ee;
         }
      }
   }
}
