package com.hixon.financialApp.model.forecast;

import com.hixon.financialApp.model.budget.Item;

import java.util.Calendar;

public class ForecastUtilities {


    /*
     * Fields:
     */
    /**
     * The pay period type of the forecast item. This is used to determine the start and end dates of the pay periods
     * for the forecast item.
     */
    protected Item.PeriodType payPeriod = Item.PeriodType.SEMIMONTHLY;

    /*
     * Constructors:
     */


    /*
     * Main methods:
     */
    /**
     * Get the start date of the first pay period that contains the planned date. The algorithm is to first determine
     * the pay period type of the forecast item. If the period type is "monthly", then the start date of the first pay
     * period is the first day of the month of the planned date. If the period type is "semi-monthly", then the start
     * date is the first day of the month if the planned date is between the 1st and the 14th day of the month
     * (inclusive), else the start date is the 15th day of the month. If the period type is "bi-weekly", then the start
     * date is the first Sunday on or after the planned date. If the period type is "weekly", then the start date is the
     * first Sunday on or after the planned date.
     *
     * @param date The planned date of the forecast transaction.
     * @return The start date of the first pay period that contains the planned date.
     */
    public Calendar getFirstPayPeriodStartDate(Calendar date) {
        Calendar payPeriodStartDate = (Calendar) date.clone();

        // If the forecast item's period type is "monthly":
        if (payPeriod.equals(Item.PeriodType.MONTHLY)) {
            payPeriodStartDate.set(Calendar.DAY_OF_MONTH, 1);
            return payPeriodStartDate;
        }

        // If the forecast item's period type is "semi-monthly":
        if (payPeriod.equals(Item.PeriodType.SEMIMONTHLY)) {
            if (date.get(Calendar.DAY_OF_MONTH) <= 14) {
                payPeriodStartDate.set(Calendar.DAY_OF_MONTH, 1);
            } else {
                payPeriodStartDate.set(Calendar.DAY_OF_MONTH, 15);
            }
            return payPeriodStartDate;
        }

        // If the forecast item's period type is "bi-weekly":
        if (payPeriod.equals(Item.PeriodType.BIWEEKLY)) {
            while (payPeriodStartDate.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
                payPeriodStartDate.add(Calendar.DATE, 1);
            }
            return payPeriodStartDate;
        }

        // If the forecast item's period type is "weekly":
        if (payPeriod.equals(Item.PeriodType.WEEKLY)) {
            while (payPeriodStartDate.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
                payPeriodStartDate.add(Calendar.DATE, 1);
            }
            return payPeriodStartDate;
        }

        // Unknown period type:
        throw new IllegalArgumentException("Unknown period type: " + payPeriod);
    }

    /**
     * Get the end date of the pay period that starts on the current pay period start date. The algorithm is to first
     * determine the pay period type of the forecast item. If the period type is "monthly", then the end date is the last
     * day of the month of the current pay period start date. If the period type is "semi-monthly", then the end date is
     * the 14th day of the month if the current pay period start date is the 1st day of the month, else the end date is
     * the last day of the month. If the period type is "bi-weekly", then the end date is 14 days after the current pay
     * period start date. If the period type is "weekly", then the end date is 7 days after the current pay period start
     * date.
     *
     * @return The end date of the pay period that starts on the current pay period start date.
     */
    public Calendar getPayPeriodEndDate(Calendar payPeriodStartDate) {

        Calendar payPeriodEndDateDate = (Calendar) payPeriodStartDate.clone();

        // If the forecast item's period type is "monthly":
        if (payPeriod.equals(Item.PeriodType.MONTHLY)) {
            payPeriodEndDateDate.set(Calendar.DAY_OF_MONTH, payPeriodEndDateDate.getActualMaximum(Calendar.DAY_OF_MONTH));
            return payPeriodEndDateDate;
        }

        // If the forecast item's period type is "semi-monthly":
        if (payPeriod.equals(Item.PeriodType.SEMIMONTHLY)) {
            if (payPeriodStartDate.get(Calendar.DAY_OF_MONTH) == 1) {
                payPeriodEndDateDate.set(Calendar.DAY_OF_MONTH, 14);
            } else {
                payPeriodEndDateDate.set(Calendar.DAY_OF_MONTH, payPeriodEndDateDate.getActualMaximum(Calendar.DAY_OF_MONTH));
            }
            return payPeriodEndDateDate;
        }

        // If the forecast item's period type is "bi-weekly":
        if (payPeriod.equals(Item.PeriodType.BIWEEKLY)) {
            payPeriodEndDateDate.add(Calendar.DATE, 13);
            return payPeriodEndDateDate;
        }

        // If the forecast item's period type is "weekly":
        if (payPeriod.equals(Item.PeriodType.WEEKLY)) {
            payPeriodEndDateDate.add(Calendar.DATE, 6);
            return payPeriodEndDateDate;
        }

        // Unknown period type:
        throw new IllegalArgumentException("Unknown period type: " + payPeriod);
    }


}
