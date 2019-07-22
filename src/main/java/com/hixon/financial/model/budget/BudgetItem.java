package com.hixon.financial.model.budget;

import com.hixon.financial.Utility;
import com.sun.istack.internal.NotNull;

import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.hixon.financial.model.budget.BudgetItem.HowPaid.*;
import static com.hixon.financial.model.budget.BudgetItem.ItemType.*;
import static com.hixon.financial.model.budget.BudgetItem.PeriodType.*;

public class BudgetItem {

    // Primary key of this BudgetItem:
    protected UUID idBudgetItem = null;
    protected UUID idBudget = null;
    protected String category = null;
    protected String payee;
    private SimpleDateFormat sdfMDY = new SimpleDateFormat("M/dd/yyyy",Locale.ENGLISH);;
    private SimpleDateFormat sdfYMDHMS = new SimpleDateFormat("yyyy/mm/dd hh:mm:ss",Locale.ENGLISH);;

    // How frequently this forecast item is expected to occur:
    public enum PeriodType {
        ON_DEMAND, DAILY, WEEKLY, BIWEEKLY, SEMIMONTHLY, SCHOOLYEARSEMIMONTHLY, MONTHLY, SIXWEEKS, BIMONTHLY, QUARTERLY, SEMIANNUALLY,
        ANNUALLY
    }

    protected PeriodType period;

    // Expected amount for this budget item:
    protected double amount = 0;

    // The first date that this item is expected to occur as a Transaction:
    protected Calendar startDate = new GregorianCalendar();

    // Last computed date this budget item will occur (used to find the next time it will occur):
    protected Calendar nextDate = new GregorianCalendar();

    // If this item is a a fixed number of payments (like an installment loan) this is the number of payments:
    protected int numberOfPayments = 0;

    // Last date that this ForecastItem can occur as a Transaction:
    protected Calendar endDate = null;

    // Type of expense:
    public enum ItemType {
        DISCRETIONARY, // "D"
        DISCRETIONARY_ESSENTIAL, // Discretionary but essential (some control over the amount) "DE"
        INCOME, // "I"
        INSTALLMENT_LOAN, // "IL"
        PERIODIC, // "P"
        PERIODIC_ESSENTIAL, // "PE"
        VARIABLE, // "V"
        REVOLVING_CREDIT, ESSENTIAL, VARIABLE_ESSENTIAL // "VE"
    }

    protected ItemType type;

    // How this budget item is expected to be paid:
    public enum HowPaid {
        AUTOMATIC_DEBIT, // "AD"
        AUTOMATIC_TRANSFER, // "AT"
        BILL_PAY, // "BP"
        CASH, // "CS"
        CHECK, // "CK"
        CREDIT_CARD, // "CC"
        DEBIT_CARD, // "DC"
        DIRECT_DEPOSIT, // "DD"
        ONLINE_PAYMENT, // Manual online-payment "OP"
        TRANSFER // "TX"
    }

    protected HowPaid howPaid;

    // A regular expression that can be applied to a downloaded transaction to match it to this budget item:
    protected String searchString;

    private static final String selectQuery = "select bin_to_uuid(idBudgetItem), category, payee, period, amount, " +
            "startDate, numberOfPayments, endDate, ItemType, howPaid, searchString, bin_to_uuid(Budget_idBudget) " +
            "from ForecastDatabase.BudgetItem ";

    private static final String insertQuery = "insert into ForecastDatabase.BudgetItem (idBudgetItem, category, payee, " +
            "period, amount, startDate, numberOfPayments, endDate, itemType, howPaid, searchString, Budget_idBudget) " +
            "values (";

    private static final String updateQuery = "Update ForecastDatabase.BudgetItem set ";

    // A regular expression for a substring that matches this budget item to transactions that are instances of it:
    private Pattern pattern = null;


    // Getters and setters:
    public UUID getIdBudgetItem() {
        return idBudgetItem;
    }
    public void setIdBudgetItem(UUID idBudgetItem) {
        this.idBudgetItem = idBudgetItem;
    }
    public UUID getIdBudget() {
        return idBudget;
    }
    private void setIdBudget(UUID idBudget) { this.idBudget = idBudget; }
    public String getCategory() {
        return category;
    }
    public void setCategory(String category) {
        this.category = category;
    }
    public String getPayee() {
        return payee;
    }
    public void setPayee(String payee) {
        this.payee = payee;
    }
    public PeriodType getPeriod() {
        return period;
    }
    public void setPeriod(PeriodType period) {
        this.period = period;
    }
    public double getAmount() {
        return amount;
    }
    public void setAmount(double amount) {
        this.amount = amount;
    }
    public Calendar getStartDate() {
        return startDate;
    }
    public void setStartDate(Calendar startDate) {
        this.startDate = startDate;
    }
    public int getNumberOfPayments() {
        return numberOfPayments;
    }
    public void setNumberOfPayments(int numberOfPayments) {
        this.numberOfPayments = numberOfPayments;
    }
    public Calendar getEndDate() {
        return endDate;
    }
    public void setEndDate(Calendar endDate) {
        this.endDate = endDate;
    }
    public ItemType getType() {
        return type;
    }
    public void setType(ItemType type) {
        this.type = type;
    }
    public HowPaid getHowPaid() {
        return howPaid;
    }
    public void setHowPaid(HowPaid howPaid) {
        this.howPaid = howPaid;
    }
    public String getSearchString() {
        return searchString;
    }
    public void setSearchString(String searchString) {
        this.searchString = searchString;
    }
    public void setNextDate(Calendar nextDate) {
        this.nextDate = nextDate;
    }
    public void setPattern(Pattern p) {
        this.pattern = p;
    }
    public Pattern getPattern() {
        return this.pattern;
    }
    public static String getSelectQuery() {
        return selectQuery;
    }
    public static String getInsertQuery() {
        return insertQuery;
    }


    // Constructors:
    public BudgetItem() {

    }


    /*
     *  Helper methods:
     */
    public static PeriodType parsePeriodType(String dbPeriod) throws BudgetException {
        PeriodType period;
        switch (dbPeriod) {
            case "On-Demand":
                period = ON_DEMAND;
                break;
            case "Daily":
                period = DAILY;
                break;
            case "Weekly":
                period = WEEKLY;
                break;
            case "Bi-Weekly":
                period = BIWEEKLY;
                break;
            case "Semi-Monthly":
                period = SEMIMONTHLY;
                break;
            case "School-Year-Semi-Monthly":
                period = SCHOOLYEARSEMIMONTHLY;
                break;
            case "Monthly":
                period = MONTHLY;
                break;
            case "Six-Weeks":
                period = SIXWEEKS;
                break;
            case "Bi-Monthly":
                period = BIMONTHLY;
                break;
            case "Quarterly":
                period = QUARTERLY;
                break;
            case "Semi-Annually":
                period = SEMIANNUALLY;
                break;
            case "Annually":
                period = ANNUALLY;
                break;
            default:
                throw new BudgetException("Invalid budget item period type:  " + dbPeriod + ".");
        }
        return period;
    }

    private String generatePeriodType(PeriodType period) throws BudgetException {
        String dbPeriodType;
        switch (period) {
            case ON_DEMAND:
                dbPeriodType = "On-Demand";
                break;
            case DAILY:
                dbPeriodType = "Daily";
                break;
            case WEEKLY:
                dbPeriodType = "Weekly";
                break;
            case BIWEEKLY:
                dbPeriodType = "Bi-Weekly";
                break;
            case SEMIMONTHLY:
                dbPeriodType = "Semi-Monthly";
                break;
            case SCHOOLYEARSEMIMONTHLY:
                dbPeriodType = "School-Year-Semi-Monthly";
                break;
            case MONTHLY:
                dbPeriodType = "Monthly";
                break;
            case SIXWEEKS:
                dbPeriodType = "Six-Weeks";
                break;
            case BIMONTHLY:
                dbPeriodType = "Bi-Monthly";
                break;
            case QUARTERLY:
                dbPeriodType = "Quarterly";
                break;
            case ANNUALLY:
                dbPeriodType = "Annually";
                break;
            case SEMIANNUALLY:
                dbPeriodType = "Semi-Annually";
                break;
            default:
                throw new BudgetException("Invalid budget item period type:  " + period + ".");
        }
        return dbPeriodType;
    }


    public static ItemType parseItemType(String dbtype) throws BudgetException {
        ItemType type;
        switch (dbtype) {
            case "DE":
                type = DISCRETIONARY_ESSENTIAL;
                break;
            case "D":
                type = DISCRETIONARY;
                break;
            case "E":
                type = ESSENTIAL;
                break;
            case "IL":
                type = INSTALLMENT_LOAN;
                break;
            case "IN":
                type = INCOME;
                break;
            case "PE":
                type = PERIODIC_ESSENTIAL;
                break;
            case "P":
                type = PERIODIC;
                break;
            case "RC":
                type = REVOLVING_CREDIT;
                break;
            case "VE":
                type = VARIABLE_ESSENTIAL;
                break;
            case "V":
                type = VARIABLE;
                break;
            default:
                throw new BudgetException("Invalid item type: " + dbtype + ".");
        }
        return type;
    }

    public static String generateItemType(ItemType type) throws BudgetException {
        String dbtype;
        switch (type) {
            case DISCRETIONARY_ESSENTIAL:
                dbtype = "DE";
                break;
            case DISCRETIONARY:
                dbtype = "D";
                break;
            case ESSENTIAL:
                dbtype = "E";
                break;
            case INSTALLMENT_LOAN:
                dbtype = "IL";
                break;
            case INCOME:
                dbtype = "IN";
                break;
            case PERIODIC_ESSENTIAL:
                dbtype = "PE";
                break;
            case PERIODIC:
                dbtype = "P";
                break;
            case REVOLVING_CREDIT:
                dbtype = "RC";
                break;
            case VARIABLE_ESSENTIAL:
                dbtype = "VE";
                break;
            case VARIABLE:
                dbtype = "V";
                break;
            default:
                throw new BudgetException("Invalid item type: " + type + ".");
        }
        return dbtype;
    }


    public static HowPaid parseHowPaid(String dbHowPaid) throws BudgetException {
        HowPaid howPaid;
        switch (dbHowPaid) {
            case "AD":
                howPaid = AUTOMATIC_DEBIT;
                break;
            case "AT":
                howPaid = AUTOMATIC_TRANSFER;
                break;
            case "BP":
                howPaid = BILL_PAY;
                break;
            case "CC":
                howPaid = CREDIT_CARD;
                break;
            case "CK":
                howPaid = CHECK;
                break;
            case "CS":
                howPaid = CASH;
                break;
            case "DC":
                howPaid = DEBIT_CARD;
                break;
            case "DD":
                howPaid = DIRECT_DEPOSIT;
                break;
            case "OP":
                howPaid = ONLINE_PAYMENT;
                break;
            case "TX":
                howPaid = TRANSFER;
                break;
            default:
                throw new BudgetException("Invalid item howPaid:  " + dbHowPaid + ".");
        }
        return howPaid;
    }

    public static String generateHowPaid(HowPaid howPaid) throws BudgetException {
        String dbHowPaid;
        switch (howPaid) {
            case AUTOMATIC_DEBIT:
                dbHowPaid = "AD";
                break;
            case AUTOMATIC_TRANSFER:
                dbHowPaid = "AT";
                break;
            case BILL_PAY:
                dbHowPaid = "BP";
                break;
            case CREDIT_CARD:
                dbHowPaid = "CC";
                break;
            case CHECK:
                dbHowPaid = "CK";
                break;
            case CASH:
                dbHowPaid = "CS";
                break;
            case DEBIT_CARD:
                dbHowPaid = "DC";
                break;
            case DIRECT_DEPOSIT:
                dbHowPaid = "DD";
                break;
            case ONLINE_PAYMENT:
                dbHowPaid = "OP";
                break;
            case TRANSFER:
                dbHowPaid = "TX";
                break;
            default:
                throw new BudgetException("Invalid item howPaid:  " + howPaid + ".");
        }
        return dbHowPaid;
    }

    // Print out a budget item:
    public String toString() {

        String endDate = null;
        if (this.endDate != null) {
            endDate = Utility.calendarDateToStringDate(this.endDate);
        } else {
            endDate = "null";
        }
        String line = "Budget Item: " + idBudgetItem + ", category = " + category + ", payee = " + payee +
                ", period = " + period  + ", amount = " + amount + ", start date = " +
                Utility.calendarDateToStringDate(startDate) + "\n   number of payments = " + numberOfPayments
                + ", end date = " + endDate + ", item type = " + type + ", how paid = " + howPaid + ", search string = "
                + searchString + ", budget id = " + idBudget + ".";
        return line;
    }


    /*
     *  CRUD methods:
     */
    // Load up a budget item from a budget item database table row:
    public BudgetItem loadFromResultSet(@NotNull ResultSet rs) throws SQLException, BudgetException {
        try {
            if (rs == null) throw new BudgetException("Result set to loadFromResultSet from must not be null.");

            idBudgetItem = UUID.fromString(rs.getString(1));
            category = rs.getString("category");
            payee = rs.getString("payee");
            period = parsePeriodType(rs.getString("period"));
            amount = rs.getDouble("AMOUNT");
            startDate.setTime(rs.getDate("startDate"));
            Date tempDate = rs.getDate("endDate");
            if (tempDate != null) {
                endDate = new GregorianCalendar();
                endDate.setTime(tempDate);
            }
            numberOfPayments = rs.getInt("numberOfPayments");
            type = parseItemType(rs.getString("ItemType"));
            howPaid = parseHowPaid(rs.getString("howPaid"));
            searchString = rs.getString("searchString");
            idBudget = UUID.fromString(rs.getString(12));

        } catch (SQLException e) {

            BudgetException be = new BudgetException("Error reading in the Budget Item row.\n" + this.toString());
            be.initCause(e);
            throw (be);

        }
        return this;
    }  // End loadFromResultSet().


    public boolean loadFromPayee(String payee) throws BudgetException {
        String query = selectQuery + "where payee = \"" + payee + "\"";
        try {
            Statement statement = Utility.getDbConnection().createStatement();
            ResultSet rs = statement.executeQuery(query);
            if (rs.next()) {
                loadFromResultSet(rs);
                return true;
            } else {
                return false;
            }
        } catch (SQLException e) {
            BudgetException be = new BudgetException("Database error occurred trying to get the budget item for " +
                    "payee " + payee);
            be.initCause(e);
            throw be;
        }
    }


    // Load a budget item from a comma separated values string:
    public void loadFromCSV(String csvLine) throws BudgetException, ParseException {

        String[] values = csvLine.split(",");
        if (values.length < 11) throw new BudgetException("Less than 11 values submitted for new budget item");
        setIdBudgetItem(UUID.randomUUID());
        setCategory(values[0]);
        setPayee(values[1]);
        setPeriod(parsePeriodType(values[2]));
        setAmount(Double.parseDouble(values[3]));
        Calendar tempDate = Calendar.getInstance();
        tempDate.setTime(sdfMDY.parse(values[4]));
        setStartDate(tempDate);
        setNumberOfPayments(Integer.parseInt(values[5]));
        if (values[6] != null && !values[6].isEmpty() && !values[6].equalsIgnoreCase("null")) {
            tempDate.setTime(sdfMDY.parse(values[6]));
        } else {
            tempDate = null;
        }
        setEndDate(tempDate);
        setType(parseItemType(values[7]));
        setHowPaid(parseHowPaid(values[8]));
        setSearchString(values[9]);
        Budget budget = new Budget(Utility.getDbConnection());
        budget.loadFromName(values[10]);
        setIdBudget(budget.getIdBudget());

        System.out.println("Created new budget item " + this.toString());
    }


    // Save this budget item:
    public void save() throws BudgetException, SQLException {

        String query = null;
        Statement statement = null;
        ResultSet rs = null;
        try {
            query = insertQuery + "uuid_to_bin('" + idBudgetItem + "'), '" + category + "', '" + payee + "', '" +
                    generatePeriodType(period) + "', " + amount + ", " + Utility.calendarDateToSqlStringDate(startDate) + ", "
                    + numberOfPayments + ", " + Utility.calendarDateToSqlStringDate(endDate) + ", '" + generateItemType(type)
                    + "', '" + generateHowPaid(howPaid) + "', '" + searchString + "', uuid_to_bin('" + idBudget + "'))";
            System.out.println(query);
            statement = Utility.getDbConnection().createStatement();
            int rowCount = statement.executeUpdate(query);
            if (rowCount == 0) {
                throw new BudgetException("Insert of budget item failed.");
            }
        } catch (SQLException e) {
            System.out.println();
            if (statement != null) statement.close();
            if (rs != null) rs.close();
            BudgetException be = new BudgetException("Database error attempting to update a budget item.");
            be.initCause(e);
            throw be;
        }
    }


    public static int getItemCount() throws SQLException {
        // Find out how many budget items there are:
        ResultSet rs = null;
        Statement stmt = null;
        try {
            stmt = Utility.getDbConnection().createStatement();
            rs = stmt.executeQuery("select count(*) from forecastdatabase.budgetItem");
        } catch (SQLException e) {
            System.out.println("[SEVERE]  SQL Error attempting to retrieve a list of items in the budget.");
            stmt.close();
            if (rs != null) rs.close();
            throw e;
        }
        try {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            System.out.println("Database error encountered trying to get the count of budget items.");
            throw e;
        }
    }

    public void update() throws BudgetException, SQLException {

        String endDateString = (endDate == null) ? "null" : "'" + Utility.calendarDateToSqlStringDate(endDate) + "'";
        String query = updateQuery + "category = '" + category + "', payee = \"" + payee + "\", period = '" +
            generatePeriodType(period) + "', amount = " + amount + ", startDate = " +
            Utility.calendarDateToSqlStringDate(startDate) + ", numberOfPayments = " + numberOfPayments + ", endDate = " +
            Utility.calendarDateToSqlStringDate(endDate) + ", itemType = '" + generateItemType(type) + "', howPaid = '" +
            generateHowPaid(howPaid) + "', searchString = \"" + searchString + "\", Budget_idbudget = uuid_to_bin('" +
            idBudget + "') where idBudgetItem = uuid_to_bin('" + idBudgetItem + "')";

        System.out.println(query);

        Statement statement = null;
        ResultSet rs = null;
        try {
            statement = Utility.getDbConnection().createStatement();
            int rowCount = statement.executeUpdate(query);
            if (rowCount == 0) {
                throw new BudgetException("Update of budget item couldn't find the item to update.");
            }
        } catch (SQLException e) {
            System.out.println();
            if (statement != null) statement.close();
            if (rs != null) rs.close();
            BudgetException be = new BudgetException("Database error attempting to update a budget item.");
            be.initCause(e);
            throw be;
        }
    }
}
