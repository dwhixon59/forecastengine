package com.hixon.financialApp.model.budget;

import com.hixon.financialApp.model.entity.DependentEntity;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.user.User;
import org.apache.commons.lang3.NotImplementedException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class ItemOfInterest extends DependentEntity {

    /*
     * Fields
     */
    protected UUID idUser;
    protected UUID idBudgetItem;


    /*
     * Getters and setters:
     */
    public UUID getIdUser() {
        return idUser;
    }
    public void setIdUser(UUID idUser) {
        this.idUser = idUser;
    }

    public UUID getIdBudgetItem() {
        return idBudgetItem;
    }
    public void setIdBudgetItem(UUID idBudgetItem) {
        this.idBudgetItem = idBudgetItem;
    }


    /*
     * Constructors:
     */
    public ItemOfInterest(ResultSet rs) throws SQLException {
        super();
        this.idUser = UUID.fromString(rs.getString("ii.idUser"));
        this.idBudgetItem = UUID.fromString(rs.getString("ii.idBudgetItem"));
    }


    /*
     * Helper methods:
     */
    /**
     * Validate the fields of an object.  Every entity is required to provide a method that validates the contents of
     * the entity.
     *
     * @return true if the object is valid
     */
    @Override
    public boolean isValid() { return true; }


    /*
     *  CRUD methods:
     */

    // The select query:
    public static final String selectColumns = " bin_to_uuid(ii.User_idUser) as 'ii.idUser', " +
            "bin_to_uuid(ii.BudgetItem_idBudgetItem) as 'ii.idBudgetItem' ";

    public static String getSelectColumns() {
        return selectColumns;
    }

    public static final String selectQuery = "select" + selectColumns + "from items_of_interest ii";

    public static String getSelectQuery() {
        return selectQuery;
    }


    @Override
    public String getInsertQuery() throws BudgetException, ForecastException, EntityException, SQLException,
            NotImplementedException {
        throw new NotImplementedException("This method is not implemented for this class.");
    }

    @Override
    public String getInsertOnDuplicateUpdateQuery() throws BudgetException, EntityException, SQLException, ForecastException {
        throw new NotImplementedException("This method is not implemented for this class.");
    }

    @Override
    public String getUpdateByIdQuery() throws BudgetException {
        throw new NotImplementedException("This method is not implemented for this class.");
    }

    @Override
    public String getDeleteByIdQuery() {
        throw new NotImplementedException("This method is not implemented for this class.");
    }

    @Override
    public String getPrintableEntityTypeName() {
        return "ItemOfInterest";
    }


    /*
     *  Main methods:
     */
    public static ResultSet getTrackingItemsOfInterestForUser(User user) throws EntityException {
        String query = "select" + getSelectColumns() + "," + BudgetItem.getSelectColumns() +
                "from items_of_interest ii inner join budget_item bi on ii.BudgetItem_idBudgetItem = bi.idBudgetItem " +
                "where ii.User_idUser = uuid_to_bin('" + user.getId() + "') and " +
                    "bi.howOccurs = 'C'";
        ResultSet rs = EntityInt.getRS(query, "get a list of items of interest for the user " + user + ".");
        return rs;
    }

    public static ResultSet getUpcomingItemsOfInterestForUser(User user) throws EntityException {
        String query = "select" + getSelectColumns() + "," + BudgetItem.getSelectColumns() +
                "from items_of_interest ii inner join budget_item bi on ii.BudgetItem_idBudgetItem = bi.idBudgetItem " +
                "where ii.User_idUser = uuid_to_bin('" + user.getId() + "')";
        ResultSet rs = EntityInt.getRS(query, "get a list of items of interest for the user " + user + ".");
        return rs;
    }
}
