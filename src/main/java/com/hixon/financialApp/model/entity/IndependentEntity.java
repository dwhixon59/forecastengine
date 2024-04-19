package com.hixon.financialApp.model.entity;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.register.RegisterException;

import java.sql.SQLException;
import java.util.UUID;

public abstract class IndependentEntity extends Entity implements IndependentEntityInt {

    /*
     * Fields for IndependentEntity:
     */
    protected UUID id = null;


    /*
    // Getters and setters for IndependentEntity:
     */
    @Override
    public UUID getId() {
        return this.id;
    }

    @Override
    public void setId(UUID id) {
        this.id = id;
        setDirty(true);
    }


    /*
    // Constructors for IndependentEntity:
     */
    public IndependentEntity(boolean createId) {
        if (createId) {
            id = UUID.randomUUID();
            setDirty(true);
        }
    }

    /*
    // Main methods for IndependentEntity:
     */
    public static IndependentEntity getById(UUID uuid) throws EntityException, SQLException, RegisterException,
            BudgetException, ForecastException {
        return null;
    }

    public boolean loadByName(IndependentEntity scope, String name) throws EntityException {
        throw new EntityException("The method loadByName() is not overriden by the derived class.");
    }

   /*
   public static <T extends IndependentEntity> T getById(UUID uuid) throws Exception {

      try (Statement statement = Utility.getDbConnection().createStatement()) {

         ResultSet rs;
         rs = statement.executeQuery(T.getSelectQuery() + " where id" + T.getEntityTypeName() + " = uuid_to_bin('" +
                 uuid.toString() + "')");
         rs.next();
         T item = mapper.apply(rs);
         return item;

      } catch (SQLException e) {
         Exception ex = new Exception("Database error occurred trying to retrieve an item with the " +
                 "sql statement " + selectQuery);
         ex.initCause(e);
         throw ex;
      }
   }
 */

}