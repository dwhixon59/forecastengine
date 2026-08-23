package com.hixon.financialApp.model.budget;

import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.entity.IndependentEntity;
import com.hixon.financialApp.utility.Utility;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Records which budget item on the far side of a transfer corresponds to a budget item on this
 * side.
 *
 * <p>A transfer between two registers is one movement of money, but each register's statement
 * reports it separately.  When the second statement is imported the application has to know which
 * budget item that side belongs to, and only the user knows that Danni's {@code Danni's
 * contribution} corresponds to Dave's {@code Room rental and utilities}.  The pairing is stable, so
 * it is asked once -- through the ordinary import questions on the far side -- and remembered here.
 *
 * <p>The key is <b>source budget item plus target budget</b>.  That distinction matters:  the
 * {@code TransferMemoMapping} removed in {@code e6253c8} was keyed on a payee string such as
 * {@code HIXON D}, which is ambiguous across accounts, and so fixed an ambiguous payee to a single
 * register.  A budget item already names one budget, and pairing it against a named target budget
 * already carries the semantics -- the same source item can pair differently into two different
 * target budgets, and this key says so.
 *
 * <p>This is the same shape as the existing {@code budgetitem_merchant} mapping.
 */
public class TransferBudgetItemPair extends IndependentEntity {

    /*
     * Fields:
     */
    private UUID idSourceBudgetItem;
    private UUID idTargetBudget;
    private UUID idTargetBudgetItem;

    private static final String selectQuery =
            "select bin_to_uuid(idTransferBudgetItemPair) as 'tp.idTransferBudgetItemPair', " +
            "bin_to_uuid(sourceBudgetItem) as 'tp.sourceBudgetItem', " +
            "bin_to_uuid(targetBudget) as 'tp.targetBudget', " +
            "bin_to_uuid(targetBudgetItem) as 'tp.targetBudgetItem' " +
            "from transfer_budget_item_pair ";

    private static final String insertQuery =
            "insert into transfer_budget_item_pair (idTransferBudgetItemPair, sourceBudgetItem, " +
            "targetBudget, targetBudgetItem) values (";


    /*
     * Getters and setters:
     */
    public UUID getIdSourceBudgetItem() {
        return idSourceBudgetItem;
    }

    public UUID getIdTargetBudget() {
        return idTargetBudget;
    }

    public UUID getIdTargetBudgetItem() {
        return idTargetBudgetItem;
    }

    public void setIdTargetBudgetItem(UUID idTargetBudgetItem) {
        this.idTargetBudgetItem = idTargetBudgetItem;
        setDirty(true);
    }

    /**
     * The budget item the far side of the transfer should be assigned to.
     */
    public BudgetItem getTargetBudgetItem() throws EntityException, BudgetException {
        return BudgetItem.getById(idTargetBudgetItem);
    }


    /*
     * Constructors:
     */
    public TransferBudgetItemPair(BudgetItem sourceBudgetItem, Budget targetBudget, BudgetItem targetBudgetItem) {
        super(true);
        this.idSourceBudgetItem = sourceBudgetItem.getId();
        this.idTargetBudget = targetBudget.getId();
        this.idTargetBudgetItem = targetBudgetItem.getId();
        setDirty(true);
    }

    public TransferBudgetItemPair(ResultSet rs) throws SQLException {
        super(false);
        this.id = UUID.fromString(rs.getString("tp.idTransferBudgetItemPair"));
        this.idSourceBudgetItem = UUID.fromString(rs.getString("tp.sourceBudgetItem"));
        this.idTargetBudget = UUID.fromString(rs.getString("tp.targetBudget"));
        this.idTargetBudgetItem = UUID.fromString(rs.getString("tp.targetBudgetItem"));
        setDirty(false);
    }


    /*
     * CRUD methods:
     */
    @Override
    public boolean isValid() {
        return idSourceBudgetItem != null && idTargetBudget != null && idTargetBudgetItem != null;
    }

    @Override
    public String getInsertQuery() {
        return insertQuery + "uuid_to_bin('" + id + "'), uuid_to_bin('" + idSourceBudgetItem + "'), " +
                "uuid_to_bin('" + idTargetBudget + "'), uuid_to_bin('" + idTargetBudgetItem + "'))";
    }

    /**
     * The pairing is unique on (source budget item, target budget), so re-learning a pairing that
     * has changed updates the existing row rather than failing on the unique key.
     */
    @Override
    public String getInsertOnDuplicateUpdateQuery() {
        return getInsertQuery() + " on duplicate key update targetBudgetItem = uuid_to_bin('" +
                idTargetBudgetItem + "')";
    }

    @Override
    public String getUpdateByIdQuery() {
        return "update transfer_budget_item_pair set targetBudgetItem = uuid_to_bin('" + idTargetBudgetItem +
                "') where idTransferBudgetItemPair = uuid_to_bin('" + id + "')";
    }

    @Override
    public String getDeleteByIdQuery() {
        return "delete from transfer_budget_item_pair where idTransferBudgetItemPair = uuid_to_bin('" + id + "')";
    }

    @Override
    public String getPrintableTypeName() {
        return getPrintableTypeName_static();
    }

    public static String getPrintableTypeName_static() {
        return "transfer budget item pairing";
    }


    /*
     * Lookup:
     */
    /**
     * Find the pairing for a source budget item into a particular target budget.
     *
     * @param sourceBudgetItem the budget item the near side of the transfer was assigned to
     * @param targetBudget     the budget the far side of the transfer belongs to
     * @return the pairing, or null if this pairing has not been learned yet
     */
    public static TransferBudgetItemPair getBySourceAndTargetBudget(BudgetItem sourceBudgetItem, Budget targetBudget)
            throws EntityException, SQLException {

        if (sourceBudgetItem == null || targetBudget == null) {
            return null;
        }

        String query = selectQuery + "where sourceBudgetItem = uuid_to_bin('" + sourceBudgetItem.getId() + "') " +
                "and targetBudget = uuid_to_bin('" + targetBudget.getId() + "')";

        try (Statement statement = Utility.getDbConnection().createStatement();
             ResultSet rs = statement.executeQuery(query)) {
            if (rs.next()) {
                return new TransferBudgetItemPair(rs);
            }
            return null;
        } catch (SQLException e) {
            EntityException ee = new EntityException("Database error occurred looking up the transfer budget item " +
                    "pairing for budget item " + sourceBudgetItem.getPayee() + " into budget " + targetBudget.getName() +
                    ".\nSQL statement was:  " + query);
            ee.initCause(e);
            throw ee;
        }
    }

    /**
     * Record what the far import chose, so the question is never asked again for this pairing.
     *
     * <p>Learning is deliberately forgiving:  a pairing that is re-learned with a different target
     * item simply replaces the old one, on the grounds that the most recent answer is the one the
     * user just gave.
     */
    public static void learn(BudgetItem sourceBudgetItem, Budget targetBudget, BudgetItem targetBudgetItem)
            throws EntityException, SQLException {

        if (sourceBudgetItem == null || targetBudget == null || targetBudgetItem == null) {
            return;
        }

        TransferBudgetItemPair pairing = new TransferBudgetItemPair(sourceBudgetItem, targetBudget, targetBudgetItem);
        EntityInt.executeUpdate(pairing.getInsertOnDuplicateUpdateQuery(),
                "trying to record a " + getPrintableTypeName_static() + ".");
    }
}
