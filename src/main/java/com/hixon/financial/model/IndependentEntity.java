package com.hixon.financial.model;

import com.hixon.financial.Utility;
import com.hixon.financial.model.register.RegisterException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

public abstract class FinancialAppEntityBase implements FinancialAppEntity {

   protected UUID id = null;
   protected boolean dirty = false;

   public FinancialAppEntityBase(boolean createId) {
      if (createId) {
         id = UUID.randomUUID();
         this.dirty = true;
      }
   }

   // The dirty bit for save operations:
   protected boolean getDirty() {
      return dirty;
   }
   protected void setDirty(boolean dirty) {
      this.dirty = dirty;
   }

   // The ID operations:
   public UUID getId() {
      return id;
   }
   public void setId(UUID id) {
      this.id = id;
   }

   protected void save(String insertQuery, String exceptionMessage) throws RegisterException, EntityException {

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
