package com.hixon.financialApp.model.budget;

import com.hixon.financialApp.controller.CancelException;
import com.hixon.financialApp.controller.QuitException;
import com.hixon.financialApp.controller.SkipException;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.entity.IndependentEntity;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.base.ViewInt;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.Contract;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import static com.hixon.financialApp.utility.Utility.isNotNullOrEmpty;

public class BudgetItem extends Item {

    /*
     * Statics and constants:
     */
    // Column headers in an import file:
    public enum Headers {
        ID_BUDGET_ITEM, CATEGORY, PAYEE, MEMO, PERIOD, AMOUNT, RUNNING_BALANCE, MINIMUM_BALANCE, START_DATE,
        NUMBER_OF_PAYMENTS, END_DATE, ITEM_TYPE, HOW_IMPORTANT, HOW_OCCURS, HOW_PAID, ID_BUDGET
    }   // End Headers.


    /*
     * Fields:
     */
    // Budget that this BudgetItem belongs to:
    protected UUID idBudget = null;


    /*
     * Getters and setters:
     */
    public UUID getIdBudget() {
        return idBudget;
    }

    public void setIdBudget(UUID idBudget) {
        this.idBudget = idBudget;
        setDirty(true);
    }

    /**
     * Gets the Budget that this BudgetItem belongs to.
     *
     * @return The Budget object
     * @throws BudgetException if budget cannot be loaded
     * @throws EntityException if database error occurs
     * @throws SQLException if SQL error occurs
     */
    public Budget getBudget() throws BudgetException, EntityException, SQLException {
        if (idBudget == null) {
            throw new BudgetException("Budget ID is null for BudgetItem: " + this.getId());
        }
        return Budget.getById(idBudget);
    }

    @Override
    public String getName() throws EntityException {
        return payee;
    }

    /*
     * Entity architecture related fields and overridden methods:
     */
    private static final String selectColumns = "bin_to_uuid(bi.idBudgetItem) as 'bi.idBudgetItem', bi.category as " +
            "'bi.category', bi.payee as 'bi.payee', bi.memo as 'bi.memo', bi.period as 'bi.period', bi.amount as " +
            "'bi.amount', bi.runningBalance as 'bi.runningBalance', bi.minimumBalance as 'bi.minimumBalance', " +
            "bi.startDate as 'bi.startDate', bi.numberOfPayments as 'bi.numberOfPayments', bi.endDate as 'bi.endDate', " +
            "bi.itemType as 'bi.itemType', bi.howImportant as 'bi.howImportant', bi.howOccurs as 'bi.howOccurs', " +
            "bi.howPaid as 'bi.howPaid', bin_to_uuid(bi.Budget_idBudget) as 'bi.idBudget' ";

    public static String getSelectColumns() {
        return selectColumns;
    }

    public static String getSelectQuery() {
        return "select " + getSelectColumns() + "from budget_item bi";
    }

    private static final String insertQuery = "insert into budget_item (idBudgetItem, category, payee, memo, " +
            "period, amount, runningBalance, minimumBalance, startDate, numberOfPayments, endDate, itemType, howImportant, " +
            "howOccurs, howPaid, Budget_idBudget) values (";

    @Override
    public String getInsertQuery() throws BudgetException, ForecastException, EntityException, SQLException, NotImplementedException {

        return insertQuery + "uuid_to_bin('" + id + "'), \"" + category + "\", \"" + payee + "\", \"" + memo + "\", '" +
                generatePeriodType(period) + "', " + amount + ", " + runningBalance + ", "  + minimumBalance + ", " +
                Utility.calendarDateToSqlDateString(startDate) + ", " + numberOfPayments + ", " +
                Utility.calendarDateToSqlDateString(endDate) + ", '" + generateItemType(itemType) + "', '" +
                generateHowImportant(howImportant) + "', '" + generateHowOccurs(howOccurs) + "', '" +
                generateHowPaid(howPaid) + "', uuid_to_bin('" + idBudget + "'))";
    }

    private static final String updateQuery = "update budget_item set ";

    private static final String deleteQuery = "delete from budget_item where ";

    @Override
    public String getInsertOnDuplicateUpdateQuery() throws BudgetException {

        return insertQuery + "uuid_to_bin('" + id + "'), \"" + category + "\", \"" + payee + "\", \"" + memo + "\", '" +
                generatePeriodType(period) + "', " + amount + ", " + runningBalance + ", " +
                Utility.calendarDateToSqlDateString(startDate) + ", " + numberOfPayments + ", " +
                Utility.calendarDateToSqlDateString(endDate) + ", '" + generateItemType(itemType) + "', '" +
                generateHowImportant(howImportant) + "', '" + generateHowOccurs(howOccurs) + "', '" +
                generateHowPaid(howPaid) + "', uuid_to_bin('" + idBudget + "')) on duplicate key update " +
                "category = \"" + category + "\", payee = \"" + payee + "\", memo = \"" + memo + "\", period = '" +
                generatePeriodType(period) + "', amount = " + amount + ", runningBalance = " + runningBalance +
                ", minimumBalance = " + minimumBalance + ", startDate = " + Utility.calendarDateToSqlDateString(startDate) +
                ", numberOfPayments = " + numberOfPayments + ", endDate = " + Utility.calendarDateToSqlDateString(endDate) +
                ", itemType = '" + generateItemType(itemType) + "', howImportant = '" + generateHowImportant(howImportant) +
                "', howOccurs = '" + generateHowOccurs(howOccurs) + "', howPaid = '" + generateHowPaid(howPaid) +
                "', Budget_idBudget = uuid_to_bin('" + idBudget + "')";
    }

    @Override
    public String getUpdateByIdQuery() throws BudgetException {
        return updateQuery + "category = '" + category + "', payee = \"" + payee + "\", memo = \"" + memo + "\", period = '" +
                generatePeriodType(period) + "', amount = " + amount + ", runningBalance = " + runningBalance +
                ", minimumBalance = " + minimumBalance + ", startDate = " + Utility.calendarDateToSqlDateString(startDate) +
                ", numberOfPayments = " + numberOfPayments + ", endDate = " + Utility.calendarDateToSqlDateString(endDate) +
                ", itemType = '" + generateItemType(itemType) + "', howImportant = '" + generateHowImportant(howImportant) +
                "', howOccurs = '" + generateHowOccurs(howOccurs) + "', howPaid = '" + generateHowPaid(howPaid) +
                "', Budget_idBudget = uuid_to_bin('" + idBudget + "') where idBudgetItem = uuid_to_bin('" + id + "')";
    }

    @Override
    public String getDeleteByIdQuery() {
        return deleteQuery + "idBudgetItem = uuid_to_bin('" + id + "')";
    }

    @Override
    public String getPrintableTypeName() {
        return getPrintableTypeName_static();
    }

    public static String getPrintableTypeName_static() {
        return "budget item";
    }


    // Constructors:
    public BudgetItem() {
        super(false);
        setDirty(false);
    }

    public BudgetItem(ResultSet rs) throws BudgetException {
        super(false);
        loadFromResultSet(rs);
        setDirty(false);
    }

    public BudgetItem(Budget budget, String newName) {
        super(false);
        setIdBudget(budget.getId());
        setPayee(newName);
        setDirty(true);
    }

    /*
     * Helper methods:
     */
    // Create a budget item from a result set:
    public static BudgetItem createFromResultSet(ResultSet rs) {
        try {
            return new BudgetItem(rs);
        } catch (BudgetException e) {
            throw new RuntimeException(e);
        }
    }

    public String toStringVeryConcise() {
        return "Budget " + super.toStringShort();
    }

    /**
     * The getDisplayString method is responsible for generating a display string for a budget item that will be used in
     * lists of budget items to clearly identify which budget item this is. The display string includes the budget
     * item's payee, category, and amount, along with the period type and planned date if applicable. The method uses the
     * Utility.formatRoundedDollarAmount method to format the amount as a rounded dollar amount. If the period type is
     * not ON_DEMAND, the method retrieves the applicable ForecastTransaction for the budget item and includes the
     * planned date in the display string. If the memo for the budget item is not empty, it is also included in the
     * display string. This method is useful for displaying budget items in a user-friendly format, such as in a list or
     * report.
     *
     * @return A string that displays the budget item's payee, category, and amount, along with the period type and
     * planned date if applicable.
     */
    public String getDisplayString() {
        String line = "";
        line += getPayee();
        if (getCategory() != null && !getCategory().isEmpty()) {
            line += " (";
            line += getCategory();
            line += ", ";
            if (getAmount() != 0) {
                line += Utility.formatRoundedDollarAmount(getAmount()) + " ";
            }
            try {
                if (getPeriod() != null) {
                    line += Item.generatePeriodType(getPeriod());
                }
            } catch (BudgetException e) {
                throw new RuntimeException(e);
            }
            if (getPeriod() != null && getPeriod() != Item.PeriodType.ON_DEMAND) {
                line += ", ";
                ForecastTransaction forecastTransaction = null;
                try {
                    forecastTransaction = ForecastTransaction.getApplicableForecastTransaction(
                            getId(), Calendar.getInstance());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                if (forecastTransaction != null) {
                    line += Utility.calendarDateToStringDate(forecastTransaction.getPlannedDate());
                } else {
                    line += "Not planned.";
                }
            }
            if (getMemo() != null && !getMemo().isEmpty()) {
                line += ", " + getMemo();
            }
            line += ")";
        }
        return line;
    }


    /*
     *  Load and save methods:
     */
    public static BudgetItem getById(UUID idBudgetItem) throws EntityException, BudgetException {
        return new BudgetItem(EntityInt.getRSById(getSelectQuery() + " where bi.idBudgetItem = ", idBudgetItem,
                "Database error encountered trying to retrieve a budget item."));
    }

    /**
     * Get a list of budget items by the amount of the budget item:
     *
     * @param budget The budget that contains the budget item.
     * @param amount The amount of the budget item to search for.
     * @return A list of budget items that match the amount.
     * @throws BudgetException
     * @throws SQLException
     * @throws EntityException
     */
    public static List<BudgetItem> getByAmount(Budget budget, double amount) throws BudgetException, SQLException,
            EntityException {
        String query = getSelectQuery() + " where bi.Budget_idBudget = uuid_to_bin('" + budget.getId() +
                "') and  bi.amount = " + amount;
        List<BudgetItem> budgetItems = new ArrayList<>();
        ResultSet rs = EntityInt.getRS(query, "Database error encountered trying to retrieve a budget item.");
        while (rs.next()) {
            budgetItems.add(new BudgetItem(rs));
        }
        return budgetItems;
    }

    /**
     * Load a budget item from the database by its name.
     *
     * @param scope
     * @param name  The name of the budget item to load.
     * @return True if the budget item was found and loaded, false if it was not found.
     * @throws EntityException
     * @throws SQLException
     * @throws RegisterException
     * @throws BudgetException
     * @throws ForecastException
     */
    @Override
    public boolean loadByName(IndependentEntity scope, String name) throws EntityException {
        try {
            List<BudgetItem> budgetItemsForPayee = getUnexpiredByPayee(Budget.getById(scope.getId()), name);
            if (budgetItemsForPayee.size() == 1) {
                BudgetItem budgetItem = budgetItemsForPayee.get(0);
                this.id = budgetItem.getId();
                this.category = budgetItem.getCategory();
                this.payee = budgetItem.getPayee();
                this.memo = budgetItem.getMemo();
                this.period = budgetItem.getPeriod();
                this.amount = budgetItem.getAmount();
                this.runningBalance = budgetItem.getRunningBalance();
                this.minimumBalance = budgetItem.getMinimumBalance();
                this.startDate = budgetItem.getStartDate();
                this.endDate = budgetItem.getEndDate();
                this.numberOfPayments = budgetItem.getNumberOfPayments();
                this.itemType = budgetItem.getItemType();
                this.howImportant = budgetItem.getHowImportant();
                this.howOccurs = budgetItem.getHowOccurs();
                this.howPaid = budgetItem.getHowPaid();
                this.idBudget = budgetItem.getIdBudget();
                setDirty(false);
                return true;
            } else {
                return false;
            }
        } catch (BudgetException | SQLException e) {
            EntityException ee = new EntityException("Error loading the budget item by name " + name + ".");
            ee.initCause(e);
            throw ee;
        }
    }

    // Load up a budget item from a budget item database table row:
    public BudgetItem loadFromResultSet(ResultSet rs) throws BudgetException {
        try {
            if (rs == null) throw new BudgetException("Result set to loadFromResultSet from must not be null.");

            id = UUID.fromString(rs.getString("bi.idBudgetItem"));
            category = rs.getString("bi.category");
            payee = rs.getString("bi.payee");
            memo = Utility.emptyStringIfNull(rs.getString("bi.memo"));
            period = parsePeriodType(rs.getString("bi.period"));
            amount = rs.getDouble("bi.amount");
            runningBalance = rs.getDouble("bi.runningBalance");
            minimumBalance = rs.getDouble("bi.minimumBalance");
            startDate = Utility.localDateToCalendarDate(rs.getObject("bi.startDate", LocalDate.class));
            endDate = Utility.localDateToCalendarDate(rs.getObject("bi.endDate", LocalDate.class));
            numberOfPayments = rs.getInt("bi.numberOfPayments");
            itemType = parseItemType(rs.getString("bi.ItemType"));
            howImportant = parseHowImportant(rs.getString("bi.howImportant"));
            howOccurs = parseHowOccurs(rs.getString("bi.howOccurs"));
            howPaid = parseHowPaid(rs.getString("bi.howPaid"));
            idBudget = UUID.fromString(rs.getString("bi.idBudget"));
            setDirty(false);

        } catch (SQLException e) {
            BudgetException be = new BudgetException("Error reading in the Budget Item row.\n" + this.toString());
            be.initCause(e);
            throw (be);

        } catch (BudgetException e) {
            throw new RuntimeException(e);
        }
        return this;
    }  // End loadFromResultSet().

    // Get the budget item for a payee:
    public static List<BudgetItem> getUnexpiredByPayee(Budget budget, String payee) throws BudgetException {

        // Convert the current date to SQL date string
        String currentDateSqlString = Utility.calendarDateToSqlDateString(Calendar.getInstance());

        // Get the budget items for the specified payee that are not expired:
        String query =
                getSelectQuery() + " " +
                        "where payee = '" + payee.replace("'", "''") + "' and " +
                        "Budget_idBudget = uuid_to_bin('" + budget.getId() + "') and " +
                        "(" +
                        "endDate is null or " +
                        "endDate >= " + currentDateSqlString +
                        ")";

        List<BudgetItem> budgetItems = new ArrayList<>();
        try {
            Statement statement = Utility.getDbConnection().createStatement();
            ResultSet rs = statement.executeQuery(query);
            while (rs.next()) {
                BudgetItem budgetItem = new BudgetItem(rs);
                budgetItems.add(budgetItem);
            }
            return budgetItems;

        } catch (SQLException e) {
            BudgetException be = new BudgetException("Database error occurred trying to get the budget items for " +
                    "payee " + payee);
            be.initCause(e);
            throw be;
        }
    }

    /**
     * Get the budget item for a search string using full text search.
     *
     * @param searchTerm The payee to search for.
     * @return The budget item for the payee.
     * @throws BudgetException If there is a problem with the database.
     */
    public static BudgetItem getByFullTextSearch(String searchTerm) throws BudgetException, CancelException {

        try {
            // Create a natural language search query for the passed in name
            String query = getSelectQuery() + "where match(bi.category, bi.payee) against ('" + searchTerm +
                    "' in natural language mode)";
            Statement statement = Utility.getDbConnection().createStatement();

            // Until a budget item is found, or the user wants to exit without selecting one:
            BudgetItem budgetItem = null;
            while (true) {
                ResultSet rs = statement.executeQuery(query);

                // If there is at least one result:
                if (rs.next()) {
                    budgetItem = new BudgetItem(rs);

                    // If the result is not an exact match for the searchTerm, then have the user select the correct one:
                    if (budgetItem.getPayee() != searchTerm) {

                        // first make lists for the budget items and their names:
                        List<BudgetItem> budgetItems = new ArrayList<>();
                        List<String> budgetItemNames = new ArrayList<>();

                        // and add the budget items and budget item names to the lists:
                        do {
                            budgetItems.add(new BudgetItem(rs));
                            budgetItemNames.add(budgetItems.get(budgetItems.size() - 1).getPayee());
                        } while (rs.next());

                        // then let the user select the correct one:
                        int selection = Utility.getView().selectByPositionFromList("Select the correct budget item " +
                                        "for " + searchTerm, budgetItemNames, ViewInt.ALLOW_NONE, ViewInt.ALLOW_CANCEL,
                                ViewInt.ALLOW_QUIT, ViewInt.ALLOW_SKIP);

                        // If the user selected a budget item
                        if (selection > 0) {

                            // then return the selected budget item:
                            budgetItem = budgetItems.get(selection - 1);

                        } else {

                            // otherwise, return null indicating that merchant was not found in the database and will
                            // need to be added:
                            budgetItem = null;
                        }
                    }
                    // Return the budget item:
                    return budgetItem;
                }
                // otherwise, ask the user if they want to try again:
                if (!Utility.getView().getYesOrNo("Would you like to try again?")) {
                    throw new CancelException("User canceled the search for a budget item for the search term " +
                            searchTerm + ".");
                }
            }

        } catch (SQLException e) {
            BudgetException be = new BudgetException("Database error occurred trying to get the budget item for " +
                    "the search term " + searchTerm);
            be.initCause(e);
            throw be;

        } catch (QuitException | SkipException e) {

            // Ignore these exceptions as they will never be thrown:
            return null;
        }
    }

    // Get the payee for a budget item using its arbitrary ID:
    public static String getPayeeById(UUID idBudgetItem) throws BudgetException {

        if (idBudgetItem == null) {
            throw new BudgetException("Budget item ID may not be null in call to getPayeeById(idBudgetItem).");
        }

        String query = "select payee from budget_item where idBudgetItem = uuid_to_bin('" +
                idBudgetItem + "')";
        try {
            Statement statement = Utility.getDbConnection().createStatement();
            ResultSet rs = statement.executeQuery(query);
            if (rs.next()) {
                return rs.getString("payee");
            }
            return null;

        } catch (SQLException e) {
            BudgetException be = new BudgetException("Database error occurred trying to get the budget item for " +
                    "id " + idBudgetItem);
            be.initCause(e);
            throw be;
        }
    }


    // Load a budget item from a comma separated values string:
    public void loadFromCsvRecord(CSVRecord record) throws BudgetException, ParseException {

        if (record.size() < 14) throw new BudgetException("Less than 14 values submitted for new budget item");
        setId(UUID.fromString(record.get(Headers.ID_BUDGET_ITEM)));
        setCategory(record.get(Headers.CATEGORY));
        setPayee(record.get(Headers.PAYEE));
        setMemo(record.get(Headers.MEMO));
        setPeriod(parsePeriodType(record.get(Headers.PERIOD)));
        setAmount(Double.parseDouble(record.get(Headers.AMOUNT)));
        setRunningBalance(Double.parseDouble(record.get(Headers.RUNNING_BALANCE)));
        setMinimumBalance(Double.parseDouble(record.get(Headers.MINIMUM_BALANCE)));
        Calendar tempDate = Calendar.getInstance();
        tempDate.setTime(sdfMDY.parse(record.get(Headers.START_DATE)));
        setStartDate(tempDate);
        setNumberOfPayments(Integer.parseInt(record.get(Headers.NUMBER_OF_PAYMENTS)));
        if (record.get(Headers.END_DATE) != null &&
                !record.get(Headers.END_DATE).isEmpty() &&
                record.get(Headers.END_DATE).equalsIgnoreCase("null")) {
            tempDate.setTime(sdfMDY.parse(record.get(Headers.END_DATE)));
        } else {
            tempDate = null;
        }
        setEndDate(tempDate);
        setItemType(parseItemType(record.get(Headers.ITEM_TYPE)));
        setHowImportant(parseHowImportant(record.get(Headers.HOW_IMPORTANT)));
        setHowOccurs(parseHowOccurs(record.get(Headers.HOW_OCCURS)));
        setHowPaid(parseHowPaid(record.get(Headers.HOW_PAID)));
        setIdBudget(UUID.fromString(record.get(Headers.ID_BUDGET)));

        System.out.println("Created new budget item " + toString());
        setDirty(true);
    }


    // Load a budget item from a comma separated values string entered by a user:
    public static BudgetItem loadFromUserCSV(String csvLine) throws
            BudgetException, ParseException, SQLException, EntityException {

        String[] values = csvLine.split(",");
        BudgetItem budgetItem = new BudgetItem();
        if (values.length < 14) throw new BudgetException("Less than 14 values submitted for new budget item");
        budgetItem.setId(UUID.randomUUID());
        budgetItem.setCategory(values[0]);
        budgetItem.setPayee(values[1]);
        if (isNotNullOrEmpty(values[2])) budgetItem.setMemo(values[2]);
        budgetItem.setPeriod(parsePeriodType(values[3]));
        budgetItem.setAmount(Double.parseDouble(values[4]));
        budgetItem.setRunningBalance(Double.parseDouble(values[5]));
        budgetItem.setMinimumBalance(Double.parseDouble(values[6]));
        Calendar tempDate = Calendar.getInstance();
        tempDate.setTime(sdfMDY.parse(values[7]));
        budgetItem.setStartDate(tempDate);
        budgetItem.setNumberOfPayments(Integer.parseInt(values[8]));
        if (isNotNullOrEmpty(values[9])) {
            tempDate.setTime(sdfMDY.parse(values[9]));
        } else {
            tempDate = null;
        }
        budgetItem.setEndDate(tempDate);
        budgetItem.setItemType(parseItemType(values[10]));
        budgetItem.setHowImportant(parseHowImportant(values[11]));
        budgetItem.setHowOccurs(parseHowOccurs(values[12]));
        budgetItem.setHowPaid(parseHowPaid(values[13]));
        Budget budget = Budget.getByName(values[14]);
        budgetItem.setIdBudget(budget.getId());

        System.out.println("Created new budget item " + budgetItem.toString());
        budgetItem.setDirty(true);
        return budgetItem;
    }


    // Save this budget item:
    @Override
    public void save(SaveMethod method) throws SQLException, EntityException {

        // Save this budget item:
        super.save(method);

        // Mark all the forecasts that use the budget this item belongs to as out of sync with the budget:
        String updateInSyncQuery = "update forecast set inSync = 0 where Budget_idBudget = " +
                "uuid_to_bin('" + idBudget + "')";
        EntityInt.executeUpdate(updateInSyncQuery, "Database error attempting to set the " +
                "inSync flag on the forecast.");
    }

    public void update() throws BudgetException, SQLException {

        String query = updateQuery + "category = \"" + category + "\", payee = \"" + payee + "\", memo = \"" + memo +
                "\", period = '" + generatePeriodType(period) + "', amount = " + amount + ", runningBalance = " +
                runningBalance + ", minimumBalance = " + minimumBalance + ", startDate = " +
                Utility.calendarDateToSqlDateString(startDate) + ", numberOfPayments = " + numberOfPayments +
                ", endDate = " + Utility.calendarDateToSqlDateString(endDate) + ", itemType = '" + generateItemType(itemType) +
                "', howImportant = '" + generateHowImportant(howImportant) + "', howOccurs = '" + generateHowOccurs(howOccurs) +
                "', howPaid = '" + generateHowPaid(howPaid) + "', Budget_idbudget = uuid_to_bin('" + idBudget + "') " +
                "where idBudgetItem = uuid_to_bin('" + id + "')";

        Statement statement = null;
        try {
            statement = Utility.getDbConnection().createStatement();
            int rowCount = statement.executeUpdate(query);
            if (rowCount == 0) {
                throw new BudgetException("Update of budget item couldn't find the item to update.");
            }
            setDirty(false);
        } catch (SQLException e) {
            System.out.println();
            if (statement != null) statement.close();
            BudgetException be = new BudgetException("Database error attempting to update a budget item.");
            be.initCause(e);
            throw be;
        }
    }

    @Override
    public boolean isValid() {
        return true;
    }


    /*
     * Main methods:
     */

    /**
     * Get a result set consisting of all the budget items.
     *
     * @return A result set of all the budget items.
     * @throws EntityException
     */
    public static ResultSet getAllBudgetItemsRS() throws EntityException {

        String query = getSelectQuery() + " order by category, payee";
        return EntityInt.getRS(query, "getting the budget items for a MTD spending report");
    }

    /**
     * Get a result set consisting of all the budget items that have not expired as of the specified date.  This
     * method is useful to get a filtered list of budget items that does not include any of the ones that are no
     * longer in use.
     *
     * @param date   The for which the budget items must be valid (unexpired).
     * @param budget
     * @return A result set of budget items that does not include any expired budget items.
     * @throws EntityException
     */
    public static ResultSet getAllUnexpiredBudgetItems(Calendar date, Budget budget) throws EntityException {

        String query = getSelectQuery() +
                " where " +
                    "Budget_idBudget = uuid_to_bin('" + budget.getId() + "') and " +
                    "endDate is null or endDate >= " + Utility.calendarDateToSqlDateString(date) + " " +
                "order by " +
                    "category, payee";
        return EntityInt.getRS(query, "getting the budget items for a MTD spending report");

    }

    /**
     * Get all budget items for a specific budget, including both expired and unexpired items.
     *
     * @param budget The budget to retrieve items for
     * @return A result set of all budget items for the specified budget.
     * @throws EntityException if a database error occurs
     */
    public static ResultSet getAllBudgetItems(Budget budget) throws EntityException {
        String query = getSelectQuery() +
                " where Budget_idBudget = uuid_to_bin('" + budget.getId() + "') " +
                "order by category, payee";
        return EntityInt.getRS(query, "getting all budget items for budget");
    }

    /**
     * Get a list of budget items joined with their splits and transactions that are instances of them.
     *
     * @param startDate The result set will contain only the items that have not expired as of this date and only
     *                  splits associated with transactions that occurred on or after this date.
     * @param endDate
     * @return ResultSet containing the joined items and splits.
     */
    public static ResultSet getBudgetItemsWithSplits(Calendar startDate, Calendar endDate) throws
            EntityException {

        ResultSet rs = null;

        String query = "select " + getSelectColumns() + ", " + TransactionSplit.getSelectColumns() + ", " +
                Transaction.getSelectColumns() + ", " + Merchant.getSelectColumns() + " " +
                "from budget_item bi " +
                "left outer join transaction_split ts on bi.idBudgetItem = ts.BudgetItem_idBudgetItem " +
                "left outer join transaction tr on ts.Transaction_idTransaction = tr.idTransaction " +
                "left outer join merchant m on tr.Merchant_idMerchant = m.idMerchant " +
                "where " +
                "(" +
                "bi.endDate is null or " +
                "bi.endDate >= " + Utility.calendarDateToSqlDateString(startDate) + " or " +
                "(" +
                "bi.endDate < " + Utility.calendarDateToSqlDateString(startDate) + " and " +
                "ts.amount is not null " +
                ")" +
                ") and " +
                "(" +
                "(" +
                "tr.postDate >= " + Utility.calendarDateToSqlDateString(startDate) + " and " +
                "tr.postDate <= " + Utility.calendarDateToSqlDateString(endDate) +
                ") or " +
                "tr.postDate is null" +
                ") " +
                "order by bi.category, bi.payee, tr.postDate";
        return EntityInt.getRS(query, "retrieve a list of budget items joined with their splits and transactions");
    }

    // Get a list of the items of interest for a specific user:
    public static List<BudgetItem> getItemsOfInterest(User user) throws
            EntityException, SQLException, BudgetException {
        List<BudgetItem> items = new ArrayList<>();

        String query = getSelectQuery() + "where user = '" + user + "' order by category asc, payee asc";
        ResultSet rs = EntityInt.getRS(query, " while retrieving a list of items of interest for the user "
                + user + ".");
        while (rs.next()) {
            items.add(new BudgetItem(rs));
        }

        return items;
    }


    /**
     * Get the amount of money budgeted for this budget item in the current month.
     *
     * @return The amount of money budgeted for this item in the current month.
     */
    public double getBudgetedAmountForCurrentMonth() throws ForecastException {
        Calendar month = Calendar.getInstance();
        return getBudgetedAmountForMonth(month);
    }


    /**
     * Get the amount of money budgeted for this budget item in a given month.
     *
     * @param month The month to compute the budgeted amount for.  It does not matter what the date of the month is
     *              set to.
     * @return The amount of money budgeted for this budget item in a given month.
     */
    @Contract(pure = true)
    public double getBudgetedAmountForMonth(Calendar month) throws ForecastException {

        // Set the start date for the period to the first day of the month passed in:
        Calendar monthStartDate = (Calendar) month.clone();
        monthStartDate.set(Calendar.DATE, 1);

        // Set the end date for the period to the last day of the month passed in:
        Calendar monthEndDate = (Calendar) month.clone();
        int lastDayOfMonth = monthEndDate.getActualMaximum(Calendar.DATE);
        monthEndDate.set(Calendar.DATE, lastDayOfMonth);

        // Get the budgeted amount for the date range matching the specified month:
        return getBudgetedAmountInPeriod(monthStartDate, monthEndDate);
    }

    /**
     * Get the amount of money budgeted for this budget item in a period (date range).
     *
     * @param periodStartDate Staring date of the period to get the total amount for.
     * @param periodEndDate   Ending date of the period to get the total amount for.
     * @return
     */
    public double getBudgetedAmountInPeriod(Calendar periodStartDate, Calendar periodEndDate) throws
            ForecastException {

        // Get the date of the first time this budget item would occur in the given period:
        Calendar nextDate = getFirstDateInWindow(periodStartDate, periodEndDate);

        // While there would be more occurrences of the budget item in the period, total them up:
        double total = 0;
        while (nextDate != null && periodEndDate.after(nextDate)) {
            total += getAmount();
            nextDate = getNextDateOnOrBefore(nextDate, periodEndDate);
        }

        return total;
    }

    /**
     * Get the total amount spent on a budget item month-to-date:
     */
    public double getAmountSpentMTD() throws EntityException, SQLException,
            RegisterException {

        // Get a list of the splits for this budget item month-to-date:
        List<TransactionSplit> splits = TransactionSplit.getSplitsListForBudgetItemMTD(this);

        // Total the amounts of the splits:
        double total = 0;
        for (TransactionSplit split : splits
        ) {
            total += split.getAmount();
        }

        return total;
    }

}
