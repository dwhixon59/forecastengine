package com.hixon.financial.model;

import com.hixon.financial.Utility;
import com.hixon.financial.model.register.RegisterException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class FinancialAppEntity implements FinancialAppEntityInt {

   // The dirty bit for save operations:
   protected boolean dirty = false;

   public FinancialAppEntity() {
      this.dirty = false;
   }

   public boolean getDirty() {
      return dirty;
   }

   public void setDirty(boolean dirty) {
      this.dirty = dirty;
   }


   // The save operation:
   public void save(String insertQuery, String exceptionMessage) throws RegisterException, EntityException {

      Statement statement = null;
      ResultSet rs = null;

      if (dirty) {
         try {
            System.out.println(insertQuery);
            statement = Utility.getDbConnection().createStatement();
            int rowCount = statement.executeUpdate(insertQuery);
            if (rowCount != 1) {
               throw new EntityException(exceptionMessage);
            }
            setDirty(false);
         } catch (SQLException e) {

            // If the transaction doesn't already exist in the register:
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
   }
}
