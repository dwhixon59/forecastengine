package com.hixon.financial.model;

import com.hixon.financial.Utility;
import com.hixon.financial.model.register.RegisterException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

public interface FinancialAppEntityInt {

   // The dirty bit for save operations:
   public boolean getDirty();

   public void setDirty(boolean dirty);

   // The generic get by ID operation:
   public static ResultSet getRSById(String selectQuery, UUID id, String exceptionMessage) throws EntityException {
      return getRS(selectQuery + " where id = uuid_to_bin('" + id + "')", exceptionMessage);
   }

   // The generic get operation:
   public static ResultSet getRS(String selectQuery, String exceptionMessage) throws EntityException {

      Statement statement = null;
      ResultSet rs = null;

      try {
         System.out.println(selectQuery);
         statement = Utility.getDbConnection().createStatement();
         rs = statement.executeQuery(selectQuery);
         rs.next();
         return rs;
      } catch (SQLException e) {
         try {
            if (statement != null) statement.close();
            if (rs != null) rs.close();
         } finally {
            EntityException ee = new EntityException(exceptionMessage);
            ee.initCause(e);
            throw ee;
         }
      }
   }

      // The save operation:
   public void save(String insertQuery, String exceptionMessage) throws RegisterException, EntityException;

   // Delete multiple rows in the database:
   public static void deleteMultiple(String deleteQuery, String exceptionMessage) throws EntityException {
      Statement statement = null;
      try {
         statement = Utility.getDbConnection().createStatement();
         System.out.println(deleteQuery);
         int rowCount = statement.executeUpdate(deleteQuery);
         if (rowCount == 0) {
            throw new EntityException(exceptionMessage + "  Row count for delete = 0.");
         }
      } catch (SQLException e) {
         try {
            if (statement != null) statement.close();
         } finally {
            EntityException ee = new EntityException(exceptionMessage);
            ee.initCause(e);
            throw ee;
         }
      }
   }
}
