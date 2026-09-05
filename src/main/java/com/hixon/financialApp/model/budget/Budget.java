package com.hixon.financialApp.model.budget;

import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.entity.IndependentEntity;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.RegisterException;

import com.hixon.financialApp.utility.Utility;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.List;
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

    public String getName() {
        return budgetName;
    }

    public void setBudgetName(String budgetName) {
        this.budgetName = budgetName;
        setDirty(true);
    }

    @Override
    public String getInsertQuery() throws BudgetException, ForecastException {
        String nameVal = budgetName != null ? "'" + budgetName + "'" : "NULL";
        return "insert into budget (idBudget, name) values (uuid_to_bin('" + id + "'), " + nameVal + ")";
    }

    @Override
    public String getInsertOnDuplicateUpdateQuery() throws BudgetException {
        return null;
    }

    @Override
    public String getUpdateByIdQuery() throws BudgetException {
        String nameVal = budgetName != null ? "'" + budgetName + "'" : "NULL";
        return "update budget set name = " + nameVal + " where idBudget = uuid_to_bin('" + id + "')";
    }

    @Override
    public String getDeleteByIdQuery() {
        return "delete from budget where idBudget = uuid_to_bin('" + id + "')";
    }

    @Override
    public String getPrintableTypeName() {
        return getPrintableTypeName_static();
    }

    public static String getPrintableTypeName_static() {
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
            if (rs == null)
                throw new BudgetException("Result set to Budget.loadFromResultSet() from must not be null.");

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

    public static List<Budget> getListOf() throws BudgetException, SQLException {
        try (java.sql.Statement statement = com.hixon.financialApp.utility.Utility.getDbConnection().createStatement()) {
            ResultSet rs = statement.executeQuery(selectQuery + "order by name");
            List<Budget> budgets = new ArrayList<>();
            while (rs.next()) {
                Budget budget = new Budget(rs);
                budgets.add(budget);
            }
            return budgets;
        } catch (SQLException | BudgetException e) {
            BudgetException be = new BudgetException("Database error occurred trying to retrieve budgets with the " +
                    "sql statement " + selectQuery);
            be.initCause(e);
            throw be;
        }
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
    

    public List<Register> getRegisters() throws BudgetException, SQLException, RegisterException, EntityException {
        ResultSet rs = EntityInt.getRS(Register.getSelectQuery() + " where Budget_idBudget = uuid_to_bin('" +
                        id + "')", "get the Registers associated with the budget " + this);
        List<Register> registers = new ArrayList<>();
        if (rs != null) {
            while (rs.next()) {
                registers.add(new Register(rs));
            }
        } else {
            throw new BudgetException("No registers found for budget " + this);
        }
        return registers;
    }

    // Get the register for the budget:
    public Register getRegister() throws BudgetException, SQLException, RegisterException, EntityException {
        return getRegisters().get(0);
    }


    /**
     * One budget item payee that differs from another only by letter case.
     *
     * @param payee    the payee exactly as it is stored
     * @param category the category the item sits in
     * @param count    how many budget items carry this exact spelling
     */
    public record CaseVariantPayee(String payee, String category, int count) {
    }

    /**
     * Find budget item payees that are the same name spelled with different capitalisation.
     *
     * <p>They behave as separate items everywhere:  two budget items, two sets of assigned
     * merchants, two lines in the forecast's expense breakdown, and two candidates competing for
     * the same relevancy score when an import asks which item a charge belongs to.  The 09-04-2026
     * forecast reported "Smart Phones" and "Smart phones" as distinct payees inside one Utilities
     * category, and the Visible charge was offered three near-identical choices spanning both.
     *
     * <p>Reporting only.  Merging two items means deciding which spelling wins and moving every
     * merchant assignment, split and forecast item behind the loser, which is not a decision to
     * make on the user's behalf during a daily update.
     *
     * <p>The grouping is done in Java rather than SQL so it is testable without a database, and so
     * that it does not depend on the column's collation -- MySQL's default collation is
     * case-insensitive, which is exactly what makes these pairs easy to create and hard to notice.
     *
     * @return the variant spellings, grouped so that every returned list holds two or more
     *         spellings of one name; empty when every payee is spelled one way
     * @throws SQLException if the budget items cannot be read
     */
    public List<List<CaseVariantPayee>> checkForCaseVariantPayees() throws SQLException {

        String query =
            "SELECT bi.payee as payee, bi.category as category, COUNT(*) as itemCount " +
            "FROM budget_item bi " +
            "WHERE bi.Budget_idBudget = UUID_TO_BIN('" + this.getId() + "') " +
            "GROUP BY bi.payee, bi.category " +
            "ORDER BY bi.payee";

        List<CaseVariantPayee> allPayees = new ArrayList<>();
        try (Statement statement = Utility.getDbConnection().createStatement();
             ResultSet rs = statement.executeQuery(query)) {

            while (rs.next()) {
                allPayees.add(new CaseVariantPayee(
                        rs.getString("payee"),
                        rs.getString("category"),
                        rs.getInt("itemCount")));
            }
        }

        return groupCaseVariantPayees(allPayees);
    }

    /**
     * Group payees that match case-insensitively but are not spelled identically.
     *
     * <p>Separated from the query so the rule can be tested directly.  A name spelled one way is
     * not a finding however many items carry it -- duplicates of a single spelling are an ordinary
     * and deliberate thing, several budget items for one payee -- so only names with two or more
     * distinct spellings are returned.
     *
     * @param allPayees every distinct payee/category pairing in the budget
     * @return one list per name that is spelled more than one way, in name order
     */
    public static List<List<CaseVariantPayee>> groupCaseVariantPayees(List<CaseVariantPayee> allPayees) {

        Map<String, List<CaseVariantPayee>> byLowercaseName = new TreeMap<>();
        for (CaseVariantPayee payee : allPayees) {
            if (payee == null || payee.payee() == null) {
                continue;
            }
            byLowercaseName.computeIfAbsent(payee.payee().toLowerCase(), key -> new ArrayList<>()).add(payee);
        }

        List<List<CaseVariantPayee>> variants = new ArrayList<>();
        for (Map.Entry<String, List<CaseVariantPayee>> entry : byLowercaseName.entrySet()) {
            long distinctSpellings = entry.getValue().stream().map(CaseVariantPayee::payee).distinct().count();
            if (distinctSpellings > 1) {
                variants.add(entry.getValue());
            }
        }
        return variants;
    }


    /*
     * Main methods:
     */

}
