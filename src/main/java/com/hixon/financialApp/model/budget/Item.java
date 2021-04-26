package com.hixon.financialApp.model.budget;

import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.entity.IndependentEntity;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.utility.Utility;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;

import static com.hixon.financialApp.model.budget.Item.HowOccurs.*;
import static com.hixon.financialApp.model.budget.Item.HowPaid.*;
import static com.hixon.financialApp.model.budget.Item.ItemType.CREDIT_CARD;
import static com.hixon.financialApp.model.budget.Item.ItemType.*;
import static com.hixon.financialApp.model.budget.Item.PeriodType.*;
import static java.lang.Math.abs;

// This class represents an expense item.  It is used in budgets and forecasts.
public abstract class Item extends IndependentEntity {

    /*
     * Constants:
     */
    // The name of the budget category that contains all the income items. These items have special considerations, for
    // example they are taxable.
    public static final String INCOME_CATEGORY_NAME = "Income";

    // The month-day-full-year format:
    protected static final SimpleDateFormat sdfMDY = new SimpleDateFormat("M/dd/yyyy", Locale.ENGLISH);

    // The acceptable variance for the amount an item is 5%:
    private static final double ACCEPTABLE_VARIANCE = 0.05;


    /*
     * Fields:
     */
    protected String category = null;
    protected String payee = null;
    protected PeriodType period;
    // Expected amount for this budget item:
    protected double amount = 0;
    // The running balance if this item is an envelope:
    protected double runningBalance = 0;
    // The first date that this item is expected to occur as a Transaction:
    protected Calendar startDate = new GregorianCalendar();
    // If this item is a a fixed number of payments (like an installment loan) this is the number of payments:
    protected int numberOfPayments = 0;
    // Last date that this ForecastItem can occur as a Transaction:
    protected Calendar endDate = null;
    protected ItemType itemType;
    protected HowImportant howImportant;
    protected HowOccurs howOccurs;
    protected HowPaid howPaid;


    /*
     * Constructors:
     */
    public Item(boolean createId) {
        super(createId);
    }


    /*
     * Helper methods:
     */
    // How frequently this forecast item is expected to occur:
    public enum PeriodType {
        ON_DEMAND, DAILY, WEEKLY, BIWEEKLY, SEMIMONTHLY, SCHOOLYEARSEMIMONTHLY, MONTHLY, SIXWEEKS, BIMONTHLY, QUARTERLY, SEMIANNUALLY,
        ANNUALLY;
    }

    public boolean isExpired(Calendar nextDate) {
        return (getEndDate() == null) ? false : getEndDate().compareTo(nextDate) < 0;
    }

    // Type of expense:
    public enum ItemType {
        CELEBRATION, // "C"
        CREDIT_CARD, // "CC"
        EXPENSE, // "E"
        FEES, // "F"
        GIFT, // "G"
        INCOME, // "IN"
        INSTALLMENT_LOAN, // "IL"
        INSURANCE, // "I"
        INVESTMENTS, // "INV"
        MAINTENANCE, // "M"
        MAJOR_EXPENSE, // "ME"
        MEDICAL, // "MED"
        RENT, // "R"
        REVOLVING_CREDIT, // "RC"
        SAVINGS, // "SAV"
        SUBSCRIPTIONS // "S"
    }

    // How important is this expense:
    public enum HowImportant {
        DISCRETIONARY_NONESSENTIAL, // "DN" - Complete control at the time of the transaction.  For example taking a
        // vacation.  The user can take no vacation, or take a cheap one, or spend a lot on
        // one, purely at their discretion.
        DISCRETIONARY_ESSENTIAL, // "DE" - Discretionary but essential. For example buying groceries.  It isn't possible
        // to eliminate buying groceries, but the user can choose between hamburger and lobster
        // at the time of the transaction.
        FIXED_NONESSENTIAL, // "FN" - No control over the amount at the time of the transaction, but not essential.  For
        // example a HBO subscription.
        FIXED_ESSENTIAL, // "FE" - The expense cannot be entirely avoided, but it can be changed.  For example an Internet
        // service provider or your mortgate.  You can't do without Internet service, and possibly a
        // mortgage, but there are multiple options you can switch between them, though some much easier
        // that others.
        VARIABLE_NONESSENTIAL, // "VN" - No control over the amount at the time of the transaction, but not essential.  For
        // example a HBO subscription.
        VARIABLE_ESSENTIAL // "VE" - Essential, but varies month to month, like the electric bill, and the user has no
        // control over the factors that make it vary, like the weather.
    }

    // How do the occurrences of this item happen relative to the budget period for the item:
    public enum HowOccurs {
        COLLECTION, // "C" - More than once a period, like groceries (multiple trips to the store in one period).
        ENVELOPE, // "E" - Less than once a period (like car maintenance, or vacation savings).
        PERIODIC, // "P" - Once each period, on or about a particular day or date.
        UNPLANNED, // "U" - There is no budget period for this item. It happens randomly and it's not in the forecast.
        VARIABLE_PERIODIC // "VP" - Same as periodic, but the amount varies each period (like the electric bill).
    }

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
        RECURRING_PAYMENT, // Recurring payment "RP"
        TRANSFER // "TX"
    }

    public static boolean isIncomeCategory(String name) {
        return name.equalsIgnoreCase(Item.INCOME_CATEGORY_NAME);
    }


    /*
     *  Getter and setter methods:
     */

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
        setDirty(true);
    }

    public String getPayee() {
        return payee;
    }

    public void setPayee(String payee) {
        this.payee = payee;
        setDirty(true);
    }

    public PeriodType getPeriod() {
        return period;
    }

    public void setPeriod(PeriodType period) {
        this.period = period;
        setDirty(true);
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
        setDirty(true);
    }

    public double getRunningBalance() {
        return runningBalance;
    }

    public void setRunningBalance(double runningBalance) {
        this.runningBalance = runningBalance;
        setDirty(true);
    }

    public Calendar getStartDate() {
        return startDate;
    }

    public void setStartDate(Calendar startDate) {
        this.startDate = startDate;
        setDirty(true);
    }

    public int getNumberOfPayments() {
        return numberOfPayments;
    }

    public void setNumberOfPayments(int numberOfPayments) {
        this.numberOfPayments = numberOfPayments;
        setDirty(true);
    }

    public Calendar getEndDate() {
        return endDate;
    }

    public void setEndDate(Calendar endDate) {
        this.endDate = endDate;
        setDirty(true);
    }

    public ItemType getItemType() {
        return itemType;
    }

    public void setItemType(ItemType itemType) {
        this.itemType = itemType;
        setDirty(true);
    }

    public HowImportant getHowImportant() {
        return howImportant;
    }

    public void setHowImportant(HowImportant howImportant) {
        this.howImportant = howImportant;
        setDirty(true);
    }

    public HowOccurs getHowOccurs() {
        return howOccurs;
    }

    public void setHowOccurs(HowOccurs howOccurs) {
        this.howOccurs = howOccurs;
        setDirty(true);
    }

    public HowPaid getHowPaid() {
        return howPaid;
    }

    public void setHowPaid(HowPaid howPaid) {
        this.howPaid = howPaid;
        setDirty(true);
    }


    /*
     *  Helper methods:
     */


    public boolean isIncome() {
        return itemType == INCOME;
    }

    /**
     * Get the amount spent on this item in a typical year (not any particular year).  The algorithm used is to
     * calculate the daily amount by dividing the amount by the number of days in the type of period it has.  Then
     * multiply that by the number of days in a year to get the annual amount.
     *
     * @return The annual amount of this item.
     */
    public double getForecastAnnualAmount() {
        double monthlyAmount = 0.0;
        switch (period) {
            case ON_DEMAND:
                monthlyAmount = 0.0;
                break;
            case DAILY:
                monthlyAmount = amount * 365.0;
                break;
            case WEEKLY:
                monthlyAmount = amount / 7.0 * 365.0;
                break;
            case BIWEEKLY:
                monthlyAmount = amount / 14.0 * 365.0;
                break;
            case SEMIMONTHLY:
                monthlyAmount = amount * 24.0;
                break;
            case SCHOOLYEARSEMIMONTHLY:
                monthlyAmount = amount * 9.0;
                break;
            case MONTHLY:
                monthlyAmount = amount * 12.0;
                break;
            case SIXWEEKS:
                monthlyAmount = amount / 42.0 * 365.0;
                break;
            case BIMONTHLY:
                monthlyAmount = amount * 6.0;
                break;
            case QUARTERLY:
                monthlyAmount = amount * 4.0;
                break;
            case ANNUALLY:
                monthlyAmount = amount;
                break;
            case SEMIANNUALLY:
                monthlyAmount = amount * 2.0;
                break;
        }
        return monthlyAmount;
    }

    /**
     * Get the amount spent on this item in a typical month (not any particular month).  The algorithm used is to
     * divide the annual amount by the number of months in a year.
     *
     * @return The annual amount of this item divided by 12.
     */
    public double getAverageAmountForAMonth() {
        return getForecastAnnualAmount() / 12.0;
    }

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

    public static String generatePeriodType(PeriodType period) throws BudgetException {
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
            case "C":
                type = CELEBRATION;
                break;
            case "CC":
                type = CREDIT_CARD;
                break;
            case "E":
                type = EXPENSE;
                break;
            case "F":
                type = FEES;
                break;
            case "G":
                type = GIFT;
                break;
            case "IN":
                type = INCOME;
                break;
            case "IL":
                type = INSTALLMENT_LOAN;
                break;
            case "I":
                type = INSURANCE;
                break;
            case "INV":
                type = INVESTMENTS;
                break;
            case "M":
                type = MAINTENANCE;
                break;
            case "ME":
                type = MAJOR_EXPENSE;
                break;
            case "MED":
                type = MEDICAL;
                break;
            case "R":
                type = RENT;
                break;
            case "RC":
                type = REVOLVING_CREDIT;
                break;
            case "SAV":
                type = SAVINGS;
                break;
            case "S":
                type = SUBSCRIPTIONS;
                break;
            default:
                throw new BudgetException("Invalid item type: " + dbtype + ".");
        }
        return type;
    }

    public static String generateItemType(ItemType type) throws BudgetException {
        String dbtype;
        switch (type) {
            case CELEBRATION:
                dbtype = "C";
                break;
            case CREDIT_CARD:
                dbtype = "CC";
                break;
            case EXPENSE:
                dbtype = "E";
                break;
            case FEES:
                dbtype = "F";
                break;
            case GIFT:
                dbtype = "G";
                break;
            case INCOME:
                dbtype = "IN";
                break;
            case INSTALLMENT_LOAN:
                dbtype = "IL";
                break;
            case INSURANCE:
                dbtype = "I";
                break;
            case INVESTMENTS:
                dbtype = "INV";
                break;
            case MAINTENANCE:
                dbtype = "M";
                break;
            case MAJOR_EXPENSE:
                dbtype = "ME";
                break;
            case MEDICAL:
                dbtype = "MED";
                break;
            case RENT:
                dbtype = "R";
                break;
            case REVOLVING_CREDIT:
                dbtype = "RC";
                break;
            case SAVINGS:
                dbtype = "SAV";
                break;
            case SUBSCRIPTIONS:
                dbtype = "S";
                break;
            default:
                throw new BudgetException("Invalid item type: " + type + ".");
        }
        return dbtype;
    }

    public static HowImportant parseHowImportant(String dbHowImportant) throws BudgetException {
        HowImportant howImportant;
        switch (dbHowImportant) {
            case "DN":
                howImportant = HowImportant.DISCRETIONARY_NONESSENTIAL;
                break;
            case "DE":
                howImportant = HowImportant.DISCRETIONARY_ESSENTIAL;
                break;
            case "FN":
                howImportant = HowImportant.FIXED_NONESSENTIAL;
                break;
            case "FE":
                howImportant = HowImportant.FIXED_ESSENTIAL;
            case "VN":
                howImportant = HowImportant.VARIABLE_NONESSENTIAL;
                break;
            case "VE":
                howImportant = HowImportant.VARIABLE_ESSENTIAL;
                break;
            default:
                throw new BudgetException("Invalid item howImportant:  " + dbHowImportant + ".");
        }
        return howImportant;
    }

    // How important is this expense:
    public static String generateHowImportant(HowImportant howImportant) throws BudgetException {
        String dbHowImportant;
        switch (howImportant) {
            case DISCRETIONARY_NONESSENTIAL:
                dbHowImportant = "DN";
                break;
            case DISCRETIONARY_ESSENTIAL:
                dbHowImportant = "DE";
                break;
            case FIXED_NONESSENTIAL:
                dbHowImportant = "FN";
                break;
            case FIXED_ESSENTIAL:
                dbHowImportant = "FE";
                break;
            case VARIABLE_NONESSENTIAL:
                dbHowImportant = "VN";
                break;
            case VARIABLE_ESSENTIAL:
                dbHowImportant = "VE";
                break;
            default:
                throw new BudgetException("Invalid item howPaid:  " + howImportant + ".");
        }
        return dbHowImportant;
    }

    // How do the occurrences of this item happen relative to the budget period for the item:
    public static HowOccurs parseHowOccurs(String dbHowOccurs) throws BudgetException {
        HowOccurs howOccurs;
        switch (dbHowOccurs) {
            case "C":
                howOccurs = COLLECTION;
                break;
            case "E":
                howOccurs = ENVELOPE;
                break;
            case "P":
                howOccurs = PERIODIC;
                break;
            case "U":
                howOccurs = UNPLANNED;
                break;
            case "VP":
                howOccurs = VARIABLE_PERIODIC;
                break;
            default:
                throw new BudgetException("Invalid item howPaid:  " + dbHowOccurs + ".");
        }
        return howOccurs;
    }

    public static String generateHowOccurs(HowOccurs howOccurs) throws BudgetException {
        String dbHowOccurs;
        switch (howOccurs) {
            case COLLECTION:
                dbHowOccurs = "C";
                break;
            case ENVELOPE:
                dbHowOccurs = "E";
                break;
            case PERIODIC:
                dbHowOccurs = "P";
                break;
            case UNPLANNED:
                dbHowOccurs = "U";
                break;
            case VARIABLE_PERIODIC:
                dbHowOccurs = "VP";
                break;
            default:
                throw new BudgetException("Invalid item howPaid:  " + howOccurs + ".");
        }
        return dbHowOccurs;
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
            case "CS":
                howPaid = CASH;
                break;
            case "CK":
                howPaid = CHECK;
                break;
            case "CC":
                howPaid = HowPaid.CREDIT_CARD;
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
            case "RP":
                howPaid = RECURRING_PAYMENT;
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
            case CASH:
                dbHowPaid = "CS";
                break;
            case CHECK:
                dbHowPaid = "CK";
                break;
            case CREDIT_CARD:
                dbHowPaid = "CC";
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
            case RECURRING_PAYMENT:
                dbHowPaid = "RP";
                break;
            case TRANSFER:
                dbHowPaid = "TX";
                break;
            default:
                throw new BudgetException("Invalid item howPaid:  " + howPaid + ".");
        }
        return dbHowPaid;
    }

    public static int getItemCount() throws SQLException, EntityException {
        // Find out how many budget items there are:
        ResultSet rs = EntityInt.getSingletonRS("select count(*) from budgetItem",
                "Database error attempting to retrieve a list of items in the budget.");
        try {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            System.out.println("Database error encountered trying to get the count of budget items.");
            throw e;
        }
    }

    /**
     * Determine if a given number of days of variance between the planned and actual dates of occurrence of an item of
     * this type is OK.
     *
     * @param variance The difference between the planned date of occurrence and the actual date of occurrence.
     * @return True if the variance is OK for this type of item.
     */
    public boolean isWithinNormalDateVariance(int variance) throws BudgetException {
        return isWithinNormalDateVariance(variance, getPeriod(), getHowOccurs());
    }

    // Determine if a given number of days of variance between the planned and actual dates of occurrence of an item of
    // this type is OK:
    public static boolean isWithinNormalDateVariance(int variance, PeriodType period, HowOccurs howOccurs)
            throws BudgetException {

        boolean isOk = true;

        // Only periodic transactions can be considered overdue so if it's periodic:
        if (howOccurs == Item.HowOccurs.PERIODIC || howOccurs == Item.HowOccurs.VARIABLE_PERIODIC) {
            switch (period) {
                case DAILY:
                    isOk = false;
                    break;
                case WEEKLY:
                    isOk = variance > -2 && variance < 2;
                    break;
                case BIWEEKLY:
                    isOk = variance > -3 && variance < 3;
                    break;
                case SEMIMONTHLY:
                case SCHOOLYEARSEMIMONTHLY:
                case MONTHLY:
                case SIXWEEKS:
                    isOk = variance > -4 && variance < 4;
                    break;
                case BIMONTHLY:
                case QUARTERLY:
                case SEMIANNUALLY:
                case ANNUALLY:
                    isOk = variance > -8 && variance < 8;
                    break;
                case ON_DEMAND:
                    isOk = variance > -8 && variance < 8;
                default:
                    throw new BudgetException("Unknow HowOccurs type " + howOccurs + " in switch statement.");
            }
        }

        return isOk;
    }

    /**
     * Determine if a given amount of variance between the planned and actual amounts of an occurrence of an item of
     * a particular type is OK.
     *
     * @param actualAmount The amount to determine if it is within an acceptable variance to this item's amount.
     * @return true if the difference between actualAmount and the amount of this item is <= the acceptable variance of
     * this item.
     */
    public boolean isWithinNormalAmountVariance(double actualAmount) {
        return isWithinNormalAmountVariance(amount, actualAmount);
    }

    /**
     * Determine if an amount of variance between two arbitrary amounts (presumably planned vs. actual) are within the
     * normal variance of this item.
     *
     * @param plannedAmount The first amount to determine the difference from; presumably the "planned" amount.
     * @param actualAmount  The second amount to determine the difference from; presumably the "actual" amount.
     * @return true if the difference between plannedAmount and actualAmount is <= the acceptable variance of this item.
     */
    public boolean isWithinNormalAmountVariance(double plannedAmount, double actualAmount) {

        // Compute the variance between the two amounts:
        double variance = Utility.currencyDifference(plannedAmount, actualAmount);

        // Determine if the variance is acceptable based upon the amount and what kind of an item it is:
        boolean isOk = true;
        if (variance != 0.00) {
            switch (howOccurs) {
                case ENVELOPE:
                    // Envelope type budget items have no expectation as the amount of a single transaction.  Their
                    // expectation is as to the total amount spent in a period.
                    break;

                case COLLECTION:
                case UNPLANNED:
                    // Collection and unplanned categories have no expectation as the amount of a single transaction.
                    // For these types of items we only care that the actual amount did not exceed the planned amount by
                    // more than other than 5% of budget item amount:
                    if (variance > 0.00) {
                        isOk = variance < Math.abs(amount * ACCEPTABLE_VARIANCE);
                    }
                    break;

                case PERIODIC:  // Periodic and unplanned transactions can vary no more than 5%
                    double acceptableVariance;
                    if (itemType == INSTALLMENT_LOAN)
                        acceptableVariance = .02;
                    else
                        acceptableVariance = .05;
                    isOk = Math.abs(variance) < Math.abs(amount * acceptableVariance);
                    break;

                case VARIABLE_PERIODIC:  // Variable periodic transactions can vary plus or minus 20%:
                    isOk = Math.abs(variance) < Math.abs(amount * 0.20);
                    break;
            }
        }
        return isOk;
    }


    // Compute the first occurrence of this item after an arbitrary date:
    public Calendar getFirstDateOnOrAfter(Calendar onOrAfterDateParm) throws ForecastException {

        // Check pre-conditions:
        if (onOrAfterDateParm == null) throw new ForecastException("Start date cannot be null.");

        Calendar tempDate = Calendar.getInstance();
        Calendar onOrAfterDate = Calendar.getInstance();
        Utility.copyDate(onOrAfterDateParm, onOrAfterDate);
        Calendar nextDate = Calendar.getInstance();
        Utility.copyDate(onOrAfterDateParm, nextDate);

        // If the start date of this item (which is the first date this item could occur) is after the start date of
        // the query, then move up the query start date to the item start date:
        if (startDate.compareTo(onOrAfterDate) > 0) {
            onOrAfterDate.set(startDate.get(Calendar.YEAR), startDate.get(Calendar.MONTH),
                    startDate.get(Calendar.DATE));
            nextDate.set(startDate.get(Calendar.YEAR), startDate.get(Calendar.MONTH),
                    startDate.get(Calendar.DATE));
        }

        // Get the day of the week of the first occurrence of this budget item:
        int budgetItemStartDateDayOfWeek = startDate.get(Calendar.DAY_OF_WEEK);

        // Get the day of the week of the forecast start date:
        int forecastStartDayOfWeek = onOrAfterDate.get(Calendar.DAY_OF_WEEK);

        // Set nextDate to the first occurrence of the budget item on or after the forecast date:
        switch (period) {

            case DAILY:
                // Next date is already set to the first date in the forecast
                break;

            case WEEKLY:
                // Set the date of the first occurrence to the same day of the week as the budget item start date:
                if (budgetItemStartDateDayOfWeek >= forecastStartDayOfWeek) {
                    nextDate.add(Calendar.DATE, budgetItemStartDateDayOfWeek - forecastStartDayOfWeek);
                } else {
                    nextDate.add(Calendar.DATE, 7 - (forecastStartDayOfWeek - budgetItemStartDateDayOfWeek));
                }
                break;

            case BIWEEKLY:
                // The algorithm is to calculate the number of 14 day periods between the start date of this item and
                // the on-or-after-date.  This probably not a whole number.  The remainder represents the portion of
                // a 14 day period that the on-or-after-date falls.  So take 14 - the remainder, which is the part of a
                // 14 days period that remains till the next date of this item, and add it to the on-or-after-date.
                int daysTillNextOccurrence = 14 - (Utility.daysBeteween(startDate, onOrAfterDate) % 14);
                Utility.copyDate(onOrAfterDate, nextDate);
                if (daysTillNextOccurrence != 14) {
                    nextDate.add(Calendar.DATE, daysTillNextOccurrence);
                }
                break;

            case SEMIMONTHLY:
                // At the moment semi-monthly means the 1st or the 15th, so pick the first one to occur on or after the
                // forecast start date:
                if (onOrAfterDate.get(Calendar.DATE) == 1) {
                    nextDate.set(onOrAfterDate.get(Calendar.YEAR), onOrAfterDate.get(Calendar.MONTH), 1);
                } else if (onOrAfterDate.get(Calendar.DATE) > 1 && onOrAfterDate.get(Calendar.DATE) <= 15) {
                    nextDate.set(onOrAfterDate.get(Calendar.YEAR), onOrAfterDate.get(Calendar.MONTH), 15);
                } else {
                    nextDate.set(onOrAfterDate.get(Calendar.YEAR), onOrAfterDate.get(Calendar.MONTH) + 1, 1);
                }
                break;

            case SCHOOLYEARSEMIMONTHLY:
                // At the moment semi-monthly means the 1st or the 15th, so pick the first one to occur on or after the
                // forecast start date:
                if (onOrAfterDate.get(Calendar.DATE) > 1 && onOrAfterDate.get(Calendar.DATE) <= 15) {
                    nextDate.set(onOrAfterDate.get(Calendar.YEAR), onOrAfterDate.get(Calendar.MONTH), 15);
                } else {
                    nextDate.set(onOrAfterDate.get(Calendar.YEAR), onOrAfterDate.get(Calendar.MONTH) + 1, 1);
                }
                int month = nextDate.get(Calendar.MONTH);
                if (month >= 6 && month <= 8) {
                    nextDate.set(Calendar.MONTH, Calendar.SEPTEMBER);
                }
                break;

            case MONTHLY:
                // If the item start date is after the first month of the forecast:
                tempDate.set(onOrAfterDate.get(Calendar.YEAR), onOrAfterDate.get(Calendar.MONTH), 1);
                tempDate.add(Calendar.MONTH, 1);
                if (startDate.compareTo(tempDate) >= 0) {

                    // then it's next date is it's start date:
                    nextDate.set(startDate.get(Calendar.YEAR), startDate.get(Calendar.MONTH),
                            startDate.get(Calendar.DATE));
                } else {

                    // Make the item start on it's day of the month, this month:
                    nextDate.set(onOrAfterDate.get(Calendar.YEAR), onOrAfterDate.get(Calendar.MONTH),
                            startDate.get(Calendar.DATE));

                    // If the item start date day-of-the-month occurs before the forecast start date day-of-the-month:
                    if (startDate.get(Calendar.DATE) < onOrAfterDate.get(Calendar.DATE)) {

                        // then make it the start in the second month in the forecast window:
                        nextDate.add(Calendar.MONTH, 1);
                    }
                }
                break;

            case SIXWEEKS:
                // Compute the number of six-week increments occur between the item start date and the forecast start
                // date.
                long sixWeekUnits = abs(ChronoUnit.DAYS.between(startDate.toInstant(), onOrAfterDate.toInstant()) / (7 * 6));

                // Set next date to the item start data + that many six-week increments:
                nextDate.set(startDate.get(Calendar.YEAR), startDate.get(Calendar.MONTH), startDate.get(Calendar.DATE));
                nextDate.add(Calendar.DATE, (int) sixWeekUnits * 42);

                // We used an integer value for the division, so we should be on the forecast start date, or less than
                // six weeks before or after it.  If we are before it, then increment by six weeks to get into the
                // forecast:
                if (nextDate.before(onOrAfterDate)) nextDate.add(Calendar.DATE, 42);
                break;

            case BIMONTHLY:
                // If one of the start months is odd and the other is even:
                if ((onOrAfterDate.get(Calendar.MONTH) & 1) != (startDate.get(Calendar.MONTH) & 1)) {

                    // then the first occurrence is in the month after the forecast start month:
                    nextDate.set(onOrAfterDate.get(Calendar.YEAR), onOrAfterDate.get(Calendar.MONTH) + 1,
                            startDate.get(Calendar.DATE));
                } else {
                    // if the start date day of the month is on or after the forecast start date day of the month:
                    if (startDate.get(Calendar.DATE) >= onOrAfterDate.get(Calendar.DATE)) {

                        // then the first occurrence in the forecast window is in the forecast start month:
                        nextDate.set(onOrAfterDate.get(Calendar.YEAR), onOrAfterDate.get(Calendar.MONTH),
                                startDate.get(Calendar.DATE));
                    } else {
                        // else it occurs for the first time two months after the forecast start month:
                        nextDate.set(onOrAfterDate.get(Calendar.YEAR), onOrAfterDate.get(Calendar.MONTH) + 2,
                                startDate.get(Calendar.DATE));
                    }
                }
                break;

            case QUARTERLY:
                // Quarterly dates occur on the same date each year, so set the next date year to be the same as the
                // start date of the item:
                nextDate.set(onOrAfterDate.get(Calendar.YEAR), startDate.get(Calendar.MONTH), startDate.get(Calendar.DATE));

                // Increment by quarters till the nextDate is on or after the forecast start date:
                while (nextDate.before(onOrAfterDate)) nextDate.add(Calendar.MONTH, 3);

                // Decrement by quarters till the nextDate is less than 3 months ahead of the forecast start date:
                Calendar nextQuarter = (Calendar) onOrAfterDate.clone();
                nextQuarter.add(Calendar.MONTH, 3);
                while (nextDate.compareTo(nextQuarter) >= 0) nextDate.add(Calendar.MONTH, -3);
                break;

            case SEMIANNUALLY:
                // Semi-annual dates occur on the same date each year, so set the next date year to be the same as the
                // start date of the forecast:
                nextDate.set(onOrAfterDate.get(Calendar.YEAR), startDate.get(Calendar.MONTH), startDate.get(Calendar.DATE));

                // Increment by half-years till the nextDate is on or after the forecast start date:
                while (nextDate.before(onOrAfterDate)) nextDate.add(Calendar.MONTH, 6);

                // Decrement by half-years till the nextDate is less than 6 months ahead of the forecast start date:
                while (nextDate.get(Calendar.MONTH) >= onOrAfterDate.get(Calendar.MONTH) + 6)
                    nextDate.add(Calendar.MONTH, -6);
                break;

            case ANNUALLY:
                // Annual dates occur on the same date each year, so set the next-date-year to be the same as the
                // forecast-start-date year:
                nextDate.set(onOrAfterDate.get(Calendar.YEAR), startDate.get(Calendar.MONTH), startDate.get(Calendar.DATE));

                // If the next date this year is before the forecast start date, the move it to next year:
                if (nextDate.before(onOrAfterDate)) nextDate.add(Calendar.YEAR, 1);
                break;

            case ON_DEMAND:
                nextDate = null;
                break;

            default:
                throw new ForecastException("Unrecognized period type " + period + " in the " + payee + "forecast item.");
        }

        // Check post-conditions:
        if (nextDate != null && nextDate.compareTo(onOrAfterDateParm) < 0) {
            throw new ForecastException("Next date (" + Utility.calendarDateToStringDate(nextDate) + ") must be on or " +
                    "after the specified date (" + Utility.calendarDateToStringDate(onOrAfterDateParm) + ").  " +
                    "item is:  " + this.toString());
        }

        return nextDate;
    }


    // Compute the first occurrence of this item in an arbitrary window:
    Calendar getFirstDateInWindow(Calendar startDate, Calendar endDate) throws ForecastException {
        Calendar firstDate = null;

        // If the window doesn't end before this item's start date, or start after this budget item's end date:
        if (
                (endDate == null || !(endDate.compareTo(this.startDate) < 0)) &&
                        (this.endDate == null || startDate.compareTo(this.endDate) > 0)
        ) {
            firstDate = getFirstDateOnOrAfter(startDate);
        }
        return firstDate;
    }


    // Get the date of the next occurrence of this forecast item:
    public Calendar getNextDateOfOccurrence(Calendar previousDate) throws ForecastException {

        Calendar nextDate = (Calendar) previousDate.clone();
        if (nextDate != null) {
            switch (period) {

                case DAILY:
                    // Increment the date by the length of a week, e.g. 7 days:
                    nextDate.add(Calendar.DATE, 1);
                    break;

                case WEEKLY:
                    // Increment the date by the length of a week, e.g. 7 days:
                    nextDate.add(Calendar.DATE, 7);
                    break;

                case BIWEEKLY:
                    // Increment the date by the length of two weeks, e.g. 14 days:
                    nextDate.add(Calendar.DATE, 14);
                    break;

                case SEMIMONTHLY:
                    // For now, semi-monthly items always occur on the 1st and the 15th:
                    if (nextDate.get(Calendar.DATE) == 1) {
                        nextDate.set(Calendar.DATE, 15);
                    } else {
                        nextDate.add(Calendar.MONTH, 1);
                        nextDate.set(Calendar.DATE, 1);
                    }
                    break;

                case SCHOOLYEARSEMIMONTHLY:
                    // For now, semi-monthly items always occur on the 1st and the 15th:
                    if (nextDate.get(Calendar.DATE) == 1) {
                        nextDate.set(Calendar.DATE, 15);
                    } else {
                        nextDate.add(Calendar.MONTH, 1);
                        nextDate.set(Calendar.DATE, 1);
                    }
                    int month = nextDate.get(Calendar.MONTH);
                    if (month >= 6 && month <= 8) {
                        nextDate.set(Calendar.MONTH, Calendar.SEPTEMBER);
                    }
                    break;

                case MONTHLY:
                    // Increment the date by one month:
                    nextDate.add(Calendar.MONTH, 1);
                    break;

                case SIXWEEKS:
                    // Increment the date by the length of six weeks, e.g. 42 days:
                    nextDate.add(Calendar.DATE, 42);
                    break;

                case BIMONTHLY:
                    // Increment the date by three months:
                    nextDate.add(Calendar.MONTH, 2);
                    break;

                case QUARTERLY:
                    // Increment the date by three months:
                    nextDate.add(Calendar.MONTH, 3);
                    break;

                case SEMIANNUALLY:
                    // Increment the date by six months:
                    nextDate.add(Calendar.MONTH, 6);
                    break;

                case ANNUALLY:
                    // Increment the date by one year:
                    nextDate.add(Calendar.YEAR, 1);
                    break;

                default:
                    throw new ForecastException("Can't get the next date because period " + period + " is unrecognized.");
            }
        } else {
            throw new ForecastException("Can't get the next date before calling get the first date.");
        }

        // Post-conditions:
        if (nextDate != null) {
            if (nextDate.compareTo(previousDate) <= 0) {
                throw new ForecastException("Next date is the same as, or prior to, the previous date.");
            }

            // If the next date is after the end date of this budget item, then return no next date:
            if (endDate != null && nextDate.compareTo(endDate) > 0) nextDate = null;
        }

        // TODO:  Make into a logging statement:
        //  System.out.println("The next date of this budget item is " + Utility.calendarDateToStringDate(nextDate));

        return nextDate;
    }


    // Get the date of the next occurrence of this forecast item or or before the given date:
    public Calendar getNextDateOnOrBefore(Calendar previousDate, Calendar endDate) throws ForecastException {
        Calendar nextDate = getNextDateOfOccurrence(previousDate);
        return (nextDate != null && nextDate.compareTo(endDate) <= 0) ? nextDate : null;
    }


    // Calculate the previous date of occurrence of this forecast item given the date of occurrence of this item:
    public Calendar getPreviousDateOfOccurrence(Calendar dateOfItemOccurrence) throws ForecastException {
        Calendar previousDateOfItemOccurrence = (Calendar) dateOfItemOccurrence.clone();
        if (dateOfItemOccurrence != null) {
            switch (period) {

                case DAILY:
                    // Decrement the date by one day:
                    previousDateOfItemOccurrence.add(Calendar.DATE, -1);
                    break;

                case WEEKLY:
                    // Decrement the date by the length of a week, e.g. 7 days:
                    previousDateOfItemOccurrence.add(Calendar.DATE, -7);
                    break;

                case BIWEEKLY:
                    // Decrement the date by the length of two weeks, e.g. 14 days:
                    previousDateOfItemOccurrence.add(Calendar.DATE, -14);
                    break;

                case SEMIMONTHLY:
                    // For now, semi-monthly items always occur on the 1st and the 15th:
                    if (previousDateOfItemOccurrence.get(Calendar.DATE) == 1) {
                        previousDateOfItemOccurrence.add(Calendar.MONTH, -1);
                        previousDateOfItemOccurrence.set(Calendar.DATE, 15);
                    } else {
                        previousDateOfItemOccurrence.set(Calendar.DATE, 1);
                    }
                    break;

                case SCHOOLYEARSEMIMONTHLY:
                    // For now, semi-monthly items always occur on the 1st and the 15th:
                    if (previousDateOfItemOccurrence.get(Calendar.DATE) == 1) {
                        previousDateOfItemOccurrence.add(Calendar.MONTH, -1);
                        previousDateOfItemOccurrence.set(Calendar.DATE, 15);
                    } else {
                        previousDateOfItemOccurrence.set(Calendar.DATE, 1);
                    }
                    int month = previousDateOfItemOccurrence.get(Calendar.MONTH);
                    if (month >= 6 && month <= 8) {
                        previousDateOfItemOccurrence.set(Calendar.MONTH, Calendar.MAY);
                    }
                    break;

                case MONTHLY:
                    // Decrement the date by one month:
                    previousDateOfItemOccurrence.add(Calendar.MONTH, -1);
                    break;

                case SIXWEEKS:
                    // Decrement the date by the length of six weeks, e.g. 42 days:
                    previousDateOfItemOccurrence.add(Calendar.DATE, -42);
                    break;

                case BIMONTHLY:
                    // Decrement the date by two months:
                    previousDateOfItemOccurrence.add(Calendar.MONTH, -2);
                    break;

                case QUARTERLY:
                    // Decrement the date by three months:
                    previousDateOfItemOccurrence.add(Calendar.MONTH, -3);
                    break;

                case SEMIANNUALLY:
                    // Decrement the date by six months:
                    previousDateOfItemOccurrence.add(Calendar.MONTH, -6);
                    break;

                case ANNUALLY:
                    // Decrement the date by one year:
                    previousDateOfItemOccurrence.add(Calendar.YEAR, -1);
                    break;

                default:
                    throw new ForecastException("Can't get the next date because period unrecognized.");
            }
        } else {
            throw new ForecastException("Can't get the next date before calling get the first date.");
        }

        // Post-conditions:
        if (previousDateOfItemOccurrence != null) {
            if (previousDateOfItemOccurrence.compareTo(dateOfItemOccurrence) >= 0) {
                throw new ForecastException("Previous date is the same as, or after, the passed in date.");
            }

            // If the next date is before the first date of this budget item, then return no previous date:
            if (startDate != null && previousDateOfItemOccurrence.compareTo(startDate) < 0)
                previousDateOfItemOccurrence = null;
        }

        // TODO:  Make into a logging statement:
        //  System.out.println("The next date of this budget item is " + Utility.calendarDateToStringDate(previousDateOfItemOccurrence));

        return previousDateOfItemOccurrence;
    }


    // Format an item as a string:
    public String toString() {

        String endDate = null;
        if (this.endDate != null) {
            endDate = Utility.calendarDateToStringDate(this.endDate);
        } else {
            endDate = "null";
        }
        String line = "Item:  \t\nid = " + id + ", \t\ncategory = " + category + ", \t\npayee = " + payee +
                ", \t\nperiod = " + period + ", \t\namount = " + amount + ", \t\nrunning balance = " + runningBalance +
                ", \t\nstart date = " + Utility.calendarDateToStringDate(startDate) + " \t\nnumber of payments = " +
                numberOfPayments + ", \t\nend date = " + endDate + ", \t\nitem type = " + itemType + ", \t\nhow important = " +
                howImportant + ", \t\nhow occurs = " + howOccurs + ", \t\nhow paid = " + howPaid + ".";
        return line;
    }


    // Format an item as a string:
    public String toStringShort() {

        String endDate = null;
        if (this.endDate != null) {
            endDate = Utility.calendarDateToStringDate(this.endDate);
        } else {
            endDate = "null";
        }
        String line = "Item: Category = " + category + ", Payee = " + payee + ", Amount = " + amount + ", Start Date = " +
                Utility.calendarDateToStringDate(startDate) + ".";
        return line;
    }
}
