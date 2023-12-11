package com.hixon.financialApp.model.forecast;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.budget.Item;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.utility.Utility;
import org.jetbrains.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.UUID;

import static com.hixon.financialApp.model.budget.Item.HowOccurs.UNPLANNED;

public class ForecastItem extends Item {

    /*
     * Fields:
     */
    // The forecast this item is a part of:
    protected Forecast forecast = null;

    protected UUID idForecast = null;

    // The BudgetItem this ForecastItem was created from:
    protected UUID idBudgetItem = null;

    // Last computed date this budget item will occur (used to find the next time it will occur):
    protected Calendar nextDate = Calendar.getInstance();

    // Next forecast item when included in a list of forecast items:
    protected ForecastItem nextForecastItem = null;


    /*
     * Getters and setters:
     */
    public Forecast getForecast() throws EntityException, SQLException {
        if (forecast == null) {
            forecast = Forecast.getById(idForecast);
        }
        return forecast;
    }

    public void setForecast(Forecast forecast) {
        this.forecast = forecast;
        this.idForecast = forecast.getId();
        setDirty(true);
    }

    public UUID getIdBudgetItem() {
        return idBudgetItem;
    }

    public void setIdBudgetItem(UUID idBudgetItem) {
        this.idBudgetItem = idBudgetItem;
        setDirty(true);
    }

    public Calendar getNextDate() {
        return nextDate;
    }

    public void setNextDate(Calendar date) {
        this.nextDate = date;
        setDirty(true);
    }

    public ForecastItem getNextForecastItem() {
        return nextForecastItem;
    }

    public void setNextForecastItem(ForecastItem nextForecastItem) {
        this.nextForecastItem = nextForecastItem;
        setDirty(true);
    }


    /*
     * Constructors:
     */
    // A blank forecast item:
    public ForecastItem() {
        super(true);
    }

    // Constructor that builds a forecast item from a row in the forecast item table:
    ForecastItem(ResultSet rs) throws BudgetException, ForecastException {
        super(false);
        loadFromResultSet(rs);
    }

    // Constructor that builds a forecast item from a passed in values:
    public ForecastItem(Forecast forecast, UUID idBudgetItem, String category, String payee, String memo,
                        PeriodType period, double amount, double runningBalance, Calendar startDate, int numberOfPayments,
                        Calendar endDate, ItemType itemType, HowImportant howImportant, HowOccurs howOccurs,
                        HowPaid howPaid) {
        super(true);
        this.forecast = forecast;
        this.idForecast = forecast.getId();
        this.idBudgetItem = idBudgetItem;
        this.category = category;
        this.payee = payee;
        this.memo = (Utility.isNotNullOrEmpty(memo)) ? memo : "";
        this.period = period;
        this.amount = amount;
        this.startDate = startDate;
        this.numberOfPayments = numberOfPayments;
        this.endDate = endDate;
        this.itemType = itemType;
        this.howImportant = howImportant;
        this.howOccurs = howOccurs;
        this.howPaid = howPaid;
        setDirty(true);
    }

    // Constructor that builds a forecast item from a row in the budget item table:
    ForecastItem(Forecast forecast, ResultSet budgetItemRS) throws ForecastException, SQLException, BudgetException {
        super(true);
        setDirty(true);
        this.forecast = forecast;
        this.idForecast = forecast.getId();
        loadFromBudgetItem(budgetItemRS);
    }

    // Constructor that builds a forecast item from a budget item:
    public ForecastItem(Forecast forecast, BudgetItem budgetItem) {
        super(true);
        this.forecast = forecast;
        idForecast = forecast.getId();
        idBudgetItem = budgetItem.getId();
        category = budgetItem.getCategory();
        payee = budgetItem.getPayee();
        memo = budgetItem.getMemo();
        period = budgetItem.getPeriod();
        amount = budgetItem.getAmount();
        startDate = budgetItem.getStartDate();
        numberOfPayments = budgetItem.getNumberOfPayments();
        endDate = budgetItem.getEndDate();
        itemType = budgetItem.getItemType();
        howImportant = budgetItem.getHowImportant();
        howOccurs = budgetItem.getHowOccurs();
        howPaid = budgetItem.getHowPaid();
        setDirty(true);
    }

    /**
     * Validate the fields of an object.  Every entity is required to provide a method that validates the contents of
     * the entity.
     *
     * @return true if the object is valid
     */
    @Override
    public boolean isValid() { return true; }


    public static ForecastItem getById(UUID idForecastItem) throws EntityException, SQLException, BudgetException,
            ForecastException {
        ResultSet rs = EntityInt.getRSById(selectQuery + " where idForecastItem = ", idForecastItem,
                "attempting to retrieve a forecast item");
        ForecastItem forecastItem = null;
        if (rs != null) {
            forecastItem = new ForecastItem(rs);
        }
        return forecastItem;
    }

    public static ForecastItem getByBudgetItemId(UUID idBudgetItem) throws EntityException, ForecastException,
            BudgetException {
        ResultSet rs = EntityInt.getRSById(selectQuery + " where BudgetItem_idbudgetItem = ", idBudgetItem,
                "attempting to retrieve a forecast item based on the following budget item:  " + idBudgetItem);
        ForecastItem forecastItem = null;
        if (rs != null) {
            forecastItem = new ForecastItem(rs);
        }
        return forecastItem;
    }

    public static ForecastItem getByBudgetItemId(Forecast forecast, UUID idBudgetItem) throws EntityException, ForecastException,
            BudgetException {
        ResultSet rs = EntityInt.getRSById(selectQuery + " where Forecast_idForecast = uuid_to_bin('" +
                        forecast.getId() + "') and BudgetItem_idbudgetItem = ", idBudgetItem,
                "attempting to retrieve a forecast item based on the following budget item:  " + idBudgetItem
                        + "in the forecast " + forecast);
        ForecastItem forecastItem = null;
        if (rs != null) {
            forecastItem = new ForecastItem(rs);
        }
        return forecastItem;
    }


    /*
     * Load and save methods:
     */

    private static final String selectColumns = " bin_to_uuid(fi.idForecastItem) as 'fi.idForecastItem', fi.category as " +
            "'fi.category', fi.payee as 'fi.payee', fi.memo as 'fi.memo', fi.period as 'fi.period', " +
            "fi.amount as 'fi.amount', fi.startDate as' fi.startDate', fi.numberOfPayments as 'fi.numberOfPayments', " +
            "fi.endDate as 'fi.endDate', fi.ItemType as 'fi.ItemType', fi.howImportant as 'fi.howImportant', " +
            "fi.howOccurs as 'fi.howOccurs', fi.howPaid as 'fi.howPaid'," +
            " bin_to_uuid(fi.Forecast_idForecast) as 'fi.idForecast', " +
            "bin_to_uuid(fi.BudgetItem_idBudgetItem) as 'fi.idBudgetItem' ";

    public static String getSelectColumns() {
        return selectColumns;
    }

    public static final String selectQuery = "select" + selectColumns + "from forecast_item fi";

    public static String getSelectQuery() {
        return selectQuery;
    }

    private static final String insertQuery = "insert into forecast_item (idForecastItem, category," +
            " payee, memo, period, amount, startDate, numberOfPayments, endDate, itemType, howImportant, howOccurs, " +
            "howPaid, Forecast_idForecast";

    @Override
    public String getInsertQuery() throws BudgetException {
        String query = insertQuery;
        if (idBudgetItem != null) {
            query += ", BudgetItem_idBudgetItem";
        }
        query += ") values (";
        query += "uuid_to_bin('" + id + "'), \"" + category + "\", \"" + payee + "\", \"" + memo + "\", '" +
                Item.generatePeriodType(period) + "', " + amount + ", " + Utility.calendarDateToSqlDateString(startDate) +
                ", " + numberOfPayments + ", " + Utility.calendarDateToSqlDateString(endDate) + ", '" +
                Item.generateItemType(itemType) + "', '" + Item.generateHowImportant(howImportant) + "', '" +
                Item.generateHowOccurs(howOccurs) + "', '" + Item.generateHowPaid(howPaid) + "', uuid_to_bin('" +
                idForecast;
        if (idBudgetItem != null) {
            query += "'), uuid_to_bin('" + idBudgetItem + "'))";
        } else {
            query += "'))";
        }
        return query;
    }

    @Override
    public String getInsertOnDuplicateUpdateQuery() throws BudgetException {
        return null;
    }

    protected static final String updateQuery = "update forecast_item set ";
    public static final String getUpdateQuery() { return updateQuery; }

    @Override
    public String getUpdateByIdQuery() throws BudgetException {
        return updateQuery + " category = \"" + category + "\", payee = \"" + payee + "\", memo = \"" + memo + "\", " +
                "period = '" + Item.generatePeriodType(period) + "', amount = " + amount + ", startDate = "
                + Utility.calendarDateToSqlDateString(startDate) + ", numberOfPayments = " + numberOfPayments + ", " +
                "endDate = " + Utility.calendarDateToSqlDateString(endDate) + ", itemtype = '" +
                Item.generateItemType(itemType) + "', howImportant = '" + Item.generateHowImportant(howImportant) +
                "', howOccurs = '" + Item.generateHowOccurs(howOccurs) + "', howPaid = '" + Item.generateHowPaid(howPaid)
                + "', Forecast_idForecast = uuid_to_bin('" + idForecast + "'), BudgetItem_idBudgetItem = uuid_to_bin('" +
                idBudgetItem + "') where idForecastItem = uuid_to_bin('" + id + "')";
    }

    private static final String deleteQuery = "delete from forecast_item fi where ";
    public static final String getDeleteQuery() { return deleteQuery; }

    @Override
    public String getDeleteByIdQuery() {
        return deleteQuery + "idForecastItem = uuid_to_bin('" + id + "')";
    }

    @Override
    public String getPrintableEntityTypeName() {
        return "forecast item";
    }

    // Create a forecast item from a row in the forecast item table:
    private void loadFromResultSet(ResultSet rs) throws BudgetException, ForecastException {
        try {
            id = UUID.fromString(rs.getString("fi.idForecastItem"));
            category = rs.getString("fi.category");
            payee = rs.getString("fi.payee");
            memo = Utility.emptyStringIfNull(rs.getString("fi.memo"));
            period = Item.parsePeriodType(rs.getString("fi.period"));
            amount = rs.getDouble("fi.amount");
            startDate = Utility.localDateToCalendarDate(rs.getObject("fi.startDate", LocalDate.class));
            numberOfPayments = rs.getInt("fi.numberOfPayments");
            endDate = Utility.localDateToCalendarDate(rs.getObject("fi.endDate", LocalDate.class));
            itemType = parseItemType(rs.getString("fi.ItemType"));
            howImportant = parseHowImportant(rs.getString("fi.howImportant"));
            howOccurs = parseHowOccurs(rs.getString("fi.howOccurs"));
            howPaid = parseHowPaid(rs.getString("fi.howPaid"));
            idForecast = UUID.fromString(rs.getString("fi.idForecast"));
            idBudgetItem = UUID.fromString(rs.getString("fi.idBudgetItem"));
            setDirty(false);
        } catch (SQLException e) {
            ForecastException fe = new ForecastException("Error reading in the forecast item row.\n" + this.toString());
            fe.initCause(e);
            throw (fe);
        }
        return;
    }

    // Create a forecast item from a row in the budget item table:
    private void loadFromBudgetItem(ResultSet rs) throws SQLException, ForecastException, BudgetException {
        try {
            if (rs == null) throw new ForecastException("Result set to load from must not be null.");

            category = rs.getString("bi.category");
            payee = rs.getString("bi.payee");
            memo = rs.getString("bi.memo");
            period = parsePeriodType(rs.getString("bi.period"));
            amount = rs.getDouble("bi.amount");
            startDate = Utility.localDateToCalendarDate(rs.getObject("bi.startDate", LocalDate.class));
            endDate = Utility.localDateToCalendarDate(rs.getObject("bi.endDate", LocalDate.class));
            numberOfPayments = rs.getInt("bi.numberOfPayments");
            itemType = parseItemType(rs.getString("bi.itemType"));
            howImportant = parseHowImportant(rs.getString("bi.howImportant"));
            howOccurs = parseHowOccurs(rs.getString("bi.howOccurs"));
            howPaid = parseHowPaid(rs.getString("bi.howPaid"));
            idBudgetItem = UUID.fromString(rs.getString("bi.idBudgetItem"));

        } catch (SQLException | BudgetException e) {
            System.out.println("Error reading in the Budget Item row.");
            e.printStackTrace();
            throw e;
        }
    }  // End loadFromBudgetItem().

    // Update all the forecast items in the forecast from the current budget items:
    public static void updateForecastItemsFromBudgetItems(Forecast forecast) throws Exception {
        String query =
                "update forecast_item fi inner join budget_item bi on fi.BudgetItem_idBudgetItem = bi.idBudgetItem " +
                "set fi.category = bi.category, fi.payee = bi.payee, fi.memo = bi.memo, fi.period = bi.period, " +
                        "fi.amount = bi.amount, fi.startDate = bi.startDate, fi.numberOfPayments = bi.numberOfPayments, " +
                        "fi.endDate = bi.endDate, fi.itemType = bi.itemType, fi.howImportant = bi.howImportant, " +
                        "fi.howOccurs = bi.howOccurs, fi.howPaid = bi.howPaid " +
                "where fi.Forecast_idForecast = uuid_to_bin('" + forecast.getId() + "')";
        EntityInt.executeUpdate(query, "updating the forecast items from the budget items");
    }

    public static ForecastItem getByName(UUID idForecast, String category, String payee) throws EntityException,
            BudgetException, SQLException, ForecastException {
        ResultSet rs = EntityInt.getSingletonRS(getSelectQuery() + " where fi.Forecast_idForecast = uuid_to_bin('" +
                        idForecast + "') and fi.category = \"" + category + "\" and fi.payee = \"" + payee + "\"",
                "retrieve a forecast item where category = " + category + " and payee = " + payee + ".");
        if (rs != null) {
            return new ForecastItem(rs);
        } else {
            return null;
        }
    }


    /*
     * Helper methods:
     */
    // A convenience method to print out a ForecastItem object:
    @Override
    public String toString() {
        return "Forecast  " + super.toString() + ", \t\nBudgetItem ID = " + idBudgetItem;
    }

    // A convenience method to print out a ForecastItem object:
    @Override
    public String toStringShort() {
        return "Forecast  " + super.toStringShort();
    }

    // Get the Budget Item associated wit this Forecast Item:
    public BudgetItem getBudgetItem() throws EntityException, BudgetException {
        return BudgetItem.getById(getIdBudgetItem());
    }


    /*
     * Main methods:
     */

    /**
     * Get a result set containing all the forecast items in a forecast that should contribute to a new or updated
     * forecast in no particular order.  Usable items are items that are not unplanned (on demand) or expired or not
     * started yet.
     *
     * @param forecast The forecast containing the forecast items of interest.
     * @return a result set containing all the forecast items in a forecast.
     */
    public static @Nullable ResultSet getAllUsableForecastItemsInForecast(Forecast forecast)
            throws EntityException, BudgetException {
        Calendar today = Calendar.getInstance();
        String sqlQueryString = getSelectQuery() +
                " where fi.Forecast_idForecast = uuid_to_bin('" + forecast.getId() + "') " +
                    "and fi.howOccurs <> '" + generateHowOccurs(UNPLANNED) + "' " +
                    "and (fi.endDate is null or fi.endDate >= " + Utility.calendarDateToSqlDateString(today) + ")";
        ResultSet rs = EntityInt.getRS(sqlQueryString, "attempting to get a list of usable forecast items " +
                "in the forecast " + forecast.getDescription());
        return rs;
    }


    /**
     * Expire any forecast items that refer to budget items that no longer exist and are not already expired.
     */
    public static void expireOldForecastItems(Forecast forecast) throws EntityException, RegisterException {

        Calendar today = Calendar.getInstance();
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DATE, -1);
        String expireOldForecastItemsSqlString =
                getUpdateQuery() + "endDate = " + Utility.calendarDateToSqlDateString(yesterday) + " " +
                        "where Forecast_idForecast = uuid_to_bin('" + forecast.getId() + "') and " +
                        "BudgetItem_idBudgetItem is null and " +
                        "(endDate is null or endDate >= " + Utility.calendarDateToSqlDateString(today) + ")";
        EntityInt.executeUpdate(expireOldForecastItemsSqlString, "to expire old forecast items.");
    }


    /**
     * Delete any expired forecast items that refer to budget items that no longer exist and have no linked forecast
     * transactions.  These transactions will never generate any new forecast transactions, and they aren't linked to
     * any old forecast transactions so these zombie items are just cluttering up the list of forecast items.
     */
    public static void deleteExpiredUnusedForecastItems(Forecast forecast) throws EntityException, RegisterException {

        Calendar today = Calendar.getInstance();
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DATE, -1);
        String deleteExpiredUnusedForecastItemsSqlString = getDeleteQuery() +
                "Forecast_idForecast = uuid_to_bin('" + forecast.getId() + "') and " +
                "endDate <= " + Utility.calendarDateToSqlDateString(yesterday) + " and " +
                "BudgetItem_idBudgetItem = null and " +
                "(select count(*) " +
                    "from forecast_transaction ft " +
                    "where fi.idForecastItem = ft.ForecastItem_idForecastItem" +
                ") = 0";
        EntityInt.executeUpdate(deleteExpiredUnusedForecastItemsSqlString, "to delete expired, unused " +
                "forecast items.");
    }

    /**
     * Get the total amount spent on this item in the current month.
     *
     * @return The amount spent on this forecast item during the current month.
     * @throws ForecastException
     */
    public double getTotalAmountForCurrentMonth() throws ForecastException {

        Calendar currentMonth = Calendar.getInstance();
        return getTotalAmountForMonth(currentMonth);
    }

    /**
     * Get the total amount spent on this item in a specific month.
     *
     * @param month The month for which to compute total spending on this forecast item.
     * @return The amount spent on this forecast item during the specified month.
     * @throws ForecastException
     */
    public double getTotalAmountForMonth(Calendar month) throws ForecastException {

        Calendar monthStart = Calendar.getInstance();
        monthStart.set(Calendar.DATE, 1);
        Calendar monthEnd = Calendar.getInstance();
        monthEnd.set(Calendar.DATE, monthEnd.getActualMaximum(Calendar.DAY_OF_MONTH));
        return getAmountForDateRange(monthStart, monthEnd);
    }

    /**
     * Get the planned amount to spend on this item in a specific date range.
     *
     * @param startDateParm The starting date of the date range over which to compute spending.
     * @param endDateParm   The ending date of the date range over which to compute spending.
     * @return The amount spent on this forecast item during the specified date range.
     * @throws ForecastException
     */
    public double getAmountForDateRange(Calendar startDateParm, Calendar endDateParm) throws ForecastException {

        // Clone nextDate to protect it from side effects:
        Calendar nextDate = (Calendar) startDateParm.clone();

        // Compute the amount for the specified date range:
        nextDate = getFirstDateOnOrAfter(nextDate);
        double sum = 0;
        while (nextDate != null) {
            sum += amount;
            nextDate = getNextDateOnOrBefore(nextDate, endDateParm);
        }
        return sum;
    }
}
