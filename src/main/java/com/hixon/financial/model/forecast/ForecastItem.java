package com.hixon.financial.model.forecast;

import com.hixon.financial.Utility;
import com.hixon.financial.model.budget.BudgetItem;
import com.sun.istack.internal.NotNull;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.UUID;

import static java.lang.Math.abs;

public class ForecastItem {

    // Primary key for a ForecastItem:
    private final UUID id = UUID.randomUUID();

    // The forecast this item is a part of:
    private final Forecast forecast;

    // Primary key of the BudgetItem this ForecastItem was created from:
    protected UUID idBudgetItem = null;

    protected String category = null;
    protected String payee;
    private ForecastItem nextForecastItem = null;

    protected BudgetItem.PeriodType period;

    protected double amount = 0;

    // The first date that this item can occur as a ForecastTransaction:
    protected Calendar startDate = new GregorianCalendar();

    // Last computed date this budget item will occur (used to find the next time it will occur):
    protected Calendar nextDate = new GregorianCalendar();

    protected int numberOfPayments = 0;

    // Last date that this ForecastItem can occur as a ForecastTransaction:
    protected Calendar endDate = null;

    protected String itemType;
    protected String howPaid;
    protected String searchString;

    // Pointer to a Forecast Transaction in the current forecast that is the first instance of this item:
    protected ForecastTransaction firstInstance = null;

    // Constructor that builds a forecast item from a row in the budget item table:
    ForecastItem(Forecast forecast, ResultSet rs) throws ForecastException, SQLException {
        this.forecast = forecast;
        load(rs);
    }

    // Getters and setters:
    public UUID getId() {
        return id;
    }
    public UUID getIdBudgetItem() {
        return idBudgetItem;
    }
    public void setIdBudgetItem(UUID idBudgetItem) {
        this.idBudgetItem = idBudgetItem;
    }
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
    public BudgetItem.PeriodType getPeriod() {
        return period;
    }
    public void setPeriod(BudgetItem.PeriodType period) {
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
    public String getItemType() {
        return itemType;
    }
    public void setItemType(String itemType) {
        this.itemType = itemType;
    }
    public String getHowPaid() {
        return howPaid;
    }
    public void setHowPaid(String howPaid) {
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
    public void setNextItem(ForecastItem forecastItem) { nextForecastItem = forecastItem; }
    public ForecastItem getNextItem() { return nextForecastItem; }


    // Create a forecast item from a budget item:
    void load(@NotNull ResultSet rs) throws SQLException, ForecastException {
        try {
            if (rs == null) throw new ForecastException("Result set to load from must not be null.");

            idBudgetItem = UUID.fromString(rs.getString(1));
            category = rs.getString("category");
            payee = rs.getString("payee");
            String dbPeriod = rs.getString("period");
            switch (dbPeriod) {
                case "Daily":
                    period = BudgetItem.PeriodType.DAILY;
                    break;
                case "Weekly":
                    period = BudgetItem.PeriodType.WEEKLY;
                    break;
                case "Bi-Weekly":
                    period = BudgetItem.PeriodType.BIWEEKLY;
                    break;
                case "Semi-Monthly":
                    period = BudgetItem.PeriodType.SEMIMONTHLY;
                    break;
                case "School-Year-Semi-Monthly":
                    period = BudgetItem.PeriodType.SCHOOLYEARSEMIMONTHLY;
                    break;
                case "Monthly":
                    period = BudgetItem.PeriodType.MONTHLY;
                    break;
                case "Six-Weeks":
                    period = BudgetItem.PeriodType.SIXWEEKS;
                    break;
                case "Bi-Monthly":
                    period = BudgetItem.PeriodType.BIMONTHLY;
                    break;
                case "Quarterly":
                    period = BudgetItem.PeriodType.QUARTERLY;
                    break;
                case "Semi-Annually":
                    period = BudgetItem.PeriodType.SEMIANNUALLY;
                    break;
                case "Annually":
                    period = BudgetItem.PeriodType.ANNUALLY;
                    break;
                default:
                    throw new ForecastException("Invalid period type of " + dbPeriod + " in the database.");
            }
            amount = rs.getDouble("AMOUNT");
            startDate.setTime(rs.getDate("startDate"));
            Date tempDate = rs.getDate("endDate");
            if (tempDate != null) {
                endDate = new GregorianCalendar();
                endDate.setTime(tempDate);
            }
            numberOfPayments = rs.getInt("numberOfPayments");
            itemType = rs.getString("ItemType");
            howPaid = rs.getString("howPaid");
            searchString = rs.getString("searchString");

        } catch (SQLException e) {
            System.out.println("Error reading in the Budget Item row.");
            e.printStackTrace();
            throw e;
        }
    }  // End load().

    // Compute the first occurrence of this budget item after an arbitrary date:
    Calendar getFirstDateOnOrAfter(Calendar forecastStartDate) throws ForecastException {

        Calendar tempDate = new GregorianCalendar();

        // Check pre-conditions:
        if (forecastStartDate == null) throw new ForecastException("Date to supersede cannot be null");

        // If the forecast window isn't after this budget item's end date:
        if (this.endDate == null || ( this.endDate != null && forecastStartDate.compareTo(this.endDate) <= 0)){

            // To begin, set the next date to the first date of the forecast:
            nextDate.set(forecastStartDate.get(Calendar.YEAR), forecastStartDate.get(Calendar.MONTH),
                    forecastStartDate.get(Calendar.DATE));

            // Get the day of the week of the first occurrence of this budget item:
            int budgetItemStartDateDayOfWeek = startDate.get(Calendar.DAY_OF_WEEK);

            // Get the day of the week of the forecast start date:
            int forecastStartDayOfWeek = forecastStartDate.get(Calendar.DAY_OF_WEEK);

            // Set firstTime to the first occurrence of the budget item on or after the forecast date:
            switch (period) {

                case DAILY:
                    // Next date is already set to the first date of the forecast
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
                    // If the day of the month of the start date is on or after the day of the month of the forecast start date:
                    if (startDate.get(Calendar.DATE) >= forecastStartDate.get(Calendar.DATE)) {

                        // Then start this transaction on that date in the year and month of the forecast start date:
                        nextDate.set(forecastStartDate.get(Calendar.YEAR), forecastStartDate.get(Calendar.MONTH),
                                startDate.get(Calendar.DATE));

                    } else {

                        // if the forecast start date day of the month is less than or equal to two weeks after the item
                        // start date:
                        if (forecastStartDate.get(Calendar.DATE) <= (startDate.get(Calendar.DATE) + 14))
                        {
                            // then make the next date two weeks after the start date day of the month:
                            nextDate.set(forecastStartDate.get(Calendar.YEAR), forecastStartDate.get(Calendar.MONTH),
                                    startDate.get(Calendar.DATE) + 14);

                        } else {
                            // the forecast start date is more than two weeks after the item start date so add 4 weeks:
                            nextDate.set(forecastStartDate.get(Calendar.YEAR), forecastStartDate.get(Calendar.MONTH),
                                    startDate.get(Calendar.DATE) + 28);
                        }
                    }
                    break;

                case SEMIMONTHLY:
                    // At the moment semi-monthly means the 1st or the 15th, so pick the first one to occur on or after the
                    // forecast start date:
                    if (forecastStartDate.get(Calendar.DATE) > 1 && forecastStartDate.get(Calendar.DATE) <= 15) {
                        nextDate.set(forecastStartDate.get(Calendar.YEAR), forecastStartDate.get(Calendar.MONTH), 15);
                    } else {
                        nextDate.set(forecastStartDate.get(Calendar.YEAR), forecastStartDate.get(Calendar.MONTH), 1);
                    }
                    break;

                case SCHOOLYEARSEMIMONTHLY:
                    // At the moment semi-monthly means the 1st or the 15th, so pick the first one to occur on or after the
                    // forecast start date:
                    if (forecastStartDate.get(Calendar.DATE) > 1 && forecastStartDate.get(Calendar.DATE) <= 15) {
                        nextDate.set(forecastStartDate.get(Calendar.YEAR), forecastStartDate.get(Calendar.MONTH), 15);
                    } else {
                        nextDate.set(forecastStartDate.get(Calendar.YEAR), forecastStartDate.get(Calendar.MONTH), 1);
                    }
                    int month = nextDate.get(Calendar.MONTH);
                    if (month >= 6 && month <= 8) {
                        nextDate.set(Calendar.MONTH, Calendar.SEPTEMBER);
                    }
                    break;

                case MONTHLY:
                    // If the item start date is after the first month of the forecast:
                    tempDate.set(forecastStartDate.get(Calendar.YEAR), forecastStartDate.get(Calendar.MONTH),1);
                    tempDate.add(Calendar.MONTH, 1);
                    if (startDate.compareTo(tempDate) >= 0) {

                        // then it's next date is it's start date:
                        nextDate.set(startDate.get(Calendar.YEAR), startDate.get(Calendar.MONTH),
                                startDate.get(Calendar.DATE));
                    }else {

                        // Make the item start on it's day of the month, this month:
                        nextDate.set(forecastStartDate.get(Calendar.YEAR), forecastStartDate.get(Calendar.MONTH),
                                startDate.get(Calendar.DATE));

                        // If the item start date day-of-the-month occurs before the forecast start date day-of-the-month:
                        if (startDate.get(Calendar.DATE) < forecastStartDate.get(Calendar.DATE)) {

                            // then make it the start in the second month in the forecast window:
                            nextDate.add(Calendar.MONTH, 1);
                        }
                    }
                        break;

                case SIXWEEKS:
                    // Compute the number of six-week increments occur between the item start date and the forecast start
                    // date.
                    long sixWeekUnits = abs(ChronoUnit.DAYS.between(startDate.toInstant(), forecastStartDate.toInstant()) / (7 * 6));

                    // Set next date to the item start data + that many six-week increments:
                    nextDate.set(startDate.get(Calendar.YEAR), startDate.get(Calendar.MONTH), startDate.get(Calendar.DATE));
                    nextDate.add(Calendar.DATE, (int) sixWeekUnits * 42);

                    // We used an integer value for the division, so we should be on the forecast start date, or less than
                    // six weeks before or after it.  If we are before it, then increment by six weeks to get into the
                    // forecast:
                    if (nextDate.before(forecastStartDate)) nextDate.add(Calendar.DATE, 42);
                    break;

                case BIMONTHLY:
                    // If one of the start months is odd and the other is even:
                    if ( (forecastStartDate.get(Calendar.MONTH) & 1) != (startDate.get(Calendar.MONTH) & 1)) {

                        // then the first occurrence is in the month after the forecast start month:
                        nextDate.set(forecastStartDate.get(Calendar.YEAR), forecastStartDate.get(Calendar.MONTH) + 1,
                                startDate.get(Calendar.DATE));
                    } else {
                        // if the start date day of the month is on or after the forecast start date day of the month:
                        if (startDate.get(Calendar.DATE) >= forecastStartDate.get(Calendar.DATE)) {

                            // then the first occurrence in the forecast window is in the forecast start month:
                            nextDate.set(forecastStartDate.get(Calendar.YEAR), forecastStartDate.get(Calendar.MONTH),
                                    startDate.get(Calendar.DATE));
                        } else {
                            // else it occurs for the first time two months after the forecast start month:
                            nextDate.set(forecastStartDate.get(Calendar.YEAR), forecastStartDate.get(Calendar.MONTH) + 2,
                                    startDate.get(Calendar.DATE));
                        }
                    }
                    break;

                case QUARTERLY:
                    // Quarterly dates occur on the same date each year, so set the next date year to be the same as the
                    // start date of the forecast:
                    nextDate.set(forecastStartDate.get(Calendar.YEAR), startDate.get(Calendar.MONTH), startDate.get(Calendar.DATE));

                    // Increment by quarters till the nextDate is on or after the forecast start date:
                    while (nextDate.before(forecastStartDate)) nextDate.add(Calendar.MONTH, 3);

                    // Decrement by quarters till the nextDate is less than 3 months ahead of the forecast start date:
                    while (nextDate.get(Calendar.MONTH) >= forecastStartDate.get(Calendar.MONTH) +3) nextDate.add(Calendar.MONTH, -3);
                    break;

                case SEMIANNUALLY:
                    // Semi-annual dates occur on the same date each year, so set the next date year to be the same as the
                    // start date of the forecast:
                    nextDate.set(forecastStartDate.get(Calendar.YEAR), startDate.get(Calendar.MONTH), startDate.get(Calendar.DATE));

                    // Increment by half-years till the nextDate is on or after the forecast start date:
                    while (nextDate.before(forecastStartDate)) nextDate.add(Calendar.MONTH, 6);

                    // Decrement by half-years till the nextDate is less than 6 months ahead of the forecast start date:
                    while (nextDate.get(Calendar.MONTH) >= forecastStartDate.get(Calendar.MONTH) +3) nextDate.add(Calendar.MONTH, -6);
                    break;

                case ANNUALLY:
                    // Annual dates occur on the same date each year, so set the next-date-year to be the same as the
                    // forecast-start-date year:
                    nextDate.set(forecastStartDate.get(Calendar.YEAR), startDate.get(Calendar.MONTH), startDate.get(Calendar.DATE));

                    // If the next date this year is before the forecast start date, the move it to next year:
                    if (nextDate.before(forecastStartDate)) nextDate.add(Calendar.YEAR, 1);
                    break;

                default:
                    throw new ForecastException("Unrecognized period type " + period + " in the " + payee + "forecast item.");
            }

                // Check post-conditions:
                System.out.println("First date of this budget item in the forecast window is " + Utility.calendarDateToStringDate(nextDate));
                if (nextDate.compareTo(startDate) < 0) {
                    throw new ForecastException("Next date must be on or after forecast start date.");
                }

        } // if the end date of the budget item isn't before the start of the forecast window.

        else {

            // else there is no next date inside the forecast window:
            nextDate = null;

        } // End no next date.

        return nextDate;
    }

    Calendar getNextDate() throws ForecastException {

        Calendar previousDate = (Calendar) nextDate.clone();
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
                    }
                    else {
                        nextDate.add(Calendar.MONTH, 1);
                        nextDate.set(Calendar.DATE, 1);
                    }
                    break;

                case SCHOOLYEARSEMIMONTHLY:
                    // For now, semi-monthly items always occur on the 1st and the 15th:
                    if (nextDate.get(Calendar.DATE) == 1) {
                        nextDate.set(Calendar.DATE, 15);
                    }
                    else {
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
                    throw new ForecastException("Can't get the next date because period unrecognized.");
            }
        } else {
            throw new ForecastException("Can't get the next date before calling get the first date.");
        }

        // Post-conditions:
        if (nextDate != null) {
            if (nextDate.compareTo(previousDate) <= 0) {
                throw new ForecastException("Next date is the same as, or prior to, the previous date.");
            }

            // If the next date is after the end date of this budget item, then return no next date?
            if (endDate != null && nextDate.compareTo(endDate) > 0) nextDate = null;
        }

        // TODO:  Make into a logging statement:
        //  System.out.println("The next date of this budget item is " + Utility.calendarDateToStringDate(nextDate));

        return nextDate;
    }

    // A convenience method to print out a ForecastItem object:
    @Override
    public String toString() {
        return "Category = " + category + ",  Payee = " + payee + ", Period = " + period + ", Amount = " + amount +
                ", Start Date = " + Utility.calendarDateToStringDate(startDate) + ", Number of Payments = " + numberOfPayments +
                ", End Date = " + Utility.calendarDateToStringDate(endDate) + ", Item Type = " + itemType + ", How Paid = " +
                howPaid + ", Search String = " + "searchString + ID = " + id + ", Budget ID = " + idBudgetItem;
    }
}
