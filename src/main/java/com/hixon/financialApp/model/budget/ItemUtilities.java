package com.hixon.financialApp.model.budget;

import com.hixon.financialApp.utility.Utility;

import java.util.Calendar;

public class ItemUtilities {


    /**
     * Calculates and returns the date of the closest occurrence of the specified budget item
     * relative to the provided reference date.
     *
     * @param item    the budget item containing details such as start date, end date,
     *                and recurrence period used to determine occurrences
     * @param instant the reference date used to calculate the closest occurrence of the budget item
     * relative to the provided reference date
     * @return a Calendar object representing the date of the closest occurrence of the budget item
     * relative to the provided reference date
     */
    public static Calendar getClosestOccurrence(Item item, Calendar instant) {

        if (item == null || instant == null) {
            throw new IllegalArgumentException("Item and instant must not be null");
        }
        // If the budget item isn't active, then there is no closest occurrence:
        if (item.getEndDate() != null && item.getEndDate().before(instant)) {
            return null;
        }

        // If the budget item hasn't started yet, then the closet occurrence is the start date:
        Calendar startDate = (Calendar) item.getStartDate().clone();
        if (instant.before(startDate)) {
            return startDate;
        }

        // Based on the start date of the budget item, and the period, calculate the occurrence of the budget item that
        // is closest to the specified date both before and after. The algorithm is based on the period type and the s
        // tart date of the budget item.
        Item.PeriodType period = item.getPeriod();
        Calendar lastOccurrenceBefore = (Calendar) startDate.clone();
        Calendar firstOccurrenceAfter = (Calendar) startDate.clone();
        int occurrences = 0;
        switch (period) {
            case DAILY:
                // If the period is daily, then both the last occurrence and the next occurrence are the same day as the
                // specified date.
                Utility.copyDate(instant, lastOccurrenceBefore);
                Utility.copyDate(instant, firstOccurrenceAfter);
                break;

            case WEEKLY:
                // If the period is weekly, then the last occurrence before the specified date is the start date plus
                // the number of whole weeks between the start date and the specified date.  The last occurrence is one
                // week after that date:
                occurrences = Utility.daysBetween(startDate, instant) / 7;
                lastOccurrenceBefore.add(Calendar.DATE, occurrences * 7);
                firstOccurrenceAfter.add(Calendar.DATE, (occurrences + 1) * 7);
                break;

            case BIWEEKLY:
                occurrences = Utility.daysBetween(startDate, instant) / 14;
                lastOccurrenceBefore.add(Calendar.DATE, occurrences * 14);
                firstOccurrenceAfter.add(Calendar.DATE, (occurrences + 1) * 14);
                break;

            case SEMIMONTHLY:
                lastOccurrenceBefore = (Calendar) instant.clone();
                firstOccurrenceAfter = (Calendar) instant.clone();
                if (instant.get(Calendar.DAY_OF_MONTH) <= 14) {
                    lastOccurrenceBefore.set(Calendar.DAY_OF_MONTH, 1);
                    firstOccurrenceAfter.set(Calendar.DAY_OF_MONTH, 15);
                } else {
                    lastOccurrenceBefore.set(Calendar.DAY_OF_MONTH, 15);
                    firstOccurrenceAfter.set(Calendar.DAY_OF_MONTH, 1);
                    firstOccurrenceAfter.add(Calendar.MONTH, 1);
                }
                break;

            case SCHOOL_YEAR_SEMIMONTHLY:
                lastOccurrenceBefore = (Calendar) instant.clone();
                firstOccurrenceAfter = (Calendar) instant.clone();
                if (instant.get(Calendar.DAY_OF_MONTH) <= 14) {
                    lastOccurrenceBefore.set(Calendar.DAY_OF_MONTH, 1);
                    firstOccurrenceAfter.set(Calendar.DAY_OF_MONTH, 15);
                } else {
                    lastOccurrenceBefore.set(Calendar.DAY_OF_MONTH, 15);
                    firstOccurrenceAfter.set(Calendar.DAY_OF_MONTH, 1);
                    firstOccurrenceAfter.add(Calendar.MONTH, 1);
                }
                // Adjust for the school year:
                if (lastOccurrenceBefore.get(Calendar.MONTH) < Calendar.AUGUST) {
                    lastOccurrenceBefore.set(Calendar.MONTH, Calendar.AUGUST);
                    lastOccurrenceBefore.set(Calendar.DATE, 15);
                }
                if (firstOccurrenceAfter.get(Calendar.MONTH) > Calendar.MAY) {
                    firstOccurrenceAfter.set(Calendar.MONTH, Calendar.MAY);
                    firstOccurrenceAfter.set(Calendar.DATE, 15);                }
                break;

            case THREE_WEEKS:
                occurrences = Utility.daysBetween(startDate, instant) / 21;
                lastOccurrenceBefore.add(Calendar.DATE, occurrences * 21);
                firstOccurrenceAfter.add(Calendar.DATE, (occurrences + 1) * 21);
                break;

            case FOUR_WEEKS:
                occurrences = Utility.daysBetween(startDate, instant) / 28;
                lastOccurrenceBefore.add(Calendar.DATE, occurrences * 28);
                firstOccurrenceAfter.add(Calendar.DATE, (occurrences + 1) * 28);
                break;

            case MONTHLY:
                // If the period is monthly, then if the day of the month of the start date is prior to the day of the
                // month of the specified date then the last occurrence before is the day of the month of the start date
                // in the month of the specified date.  Otherwise it is in the month previous to the specified date:.
                occurrences = Utility.monthsBetween(startDate, instant);
                lastOccurrenceBefore.add(Calendar.MONTH, occurrences);
                firstOccurrenceAfter = (Calendar) lastOccurrenceBefore.clone();
                if (lastOccurrenceBefore.before(instant)) {
                    firstOccurrenceAfter.add(Calendar.MONTH, 1);
                } else {
                    lastOccurrenceBefore.add(Calendar.MONTH, -1);
                }
                break;

            case SIX_WEEKS:
                occurrences = Utility.daysBetween(startDate, instant) / 42;
                lastOccurrenceBefore.add(Calendar.DATE, occurrences * 42);
                firstOccurrenceAfter.add(Calendar.DATE, (occurrences + 1) * 42);
                break;

            case BIMONTHLY:
                occurrences = Utility.monthsBetween(startDate, instant) / 2;
                lastOccurrenceBefore.add(Calendar.MONTH, occurrences * 2);
                firstOccurrenceAfter = (Calendar) lastOccurrenceBefore.clone();
                if (lastOccurrenceBefore.before(instant)) {
                    firstOccurrenceAfter.add(Calendar.MONTH, 2);
                } else {
                    lastOccurrenceBefore.add(Calendar.MONTH, -2);
                }
                break;

            case QUARTERLY:
                occurrences = Utility.monthsBetween(startDate, instant) / 3;
                lastOccurrenceBefore.add(Calendar.MONTH, occurrences * 3);
                firstOccurrenceAfter = (Calendar) lastOccurrenceBefore.clone();
                if (lastOccurrenceBefore.before(instant)) {
                    firstOccurrenceAfter.add(Calendar.MONTH, 3);
                } else {
                    lastOccurrenceBefore.add(Calendar.MONTH, -3);
                }
                break;

            case FOUR_MONTHS:
                occurrences = Utility.monthsBetween(startDate, instant) / 4;
                lastOccurrenceBefore.add(Calendar.MONTH, occurrences * 4);
                firstOccurrenceAfter = (Calendar) lastOccurrenceBefore.clone();
                if (lastOccurrenceBefore.before(instant)) {
                    firstOccurrenceAfter.add(Calendar.MONTH, 4);
                } else {
                    lastOccurrenceBefore.add(Calendar.MONTH, -4);
                }
                break;

            case SEMIANNUALLY:
                occurrences = Utility.monthsBetween(startDate, instant) / 6;
                lastOccurrenceBefore.add(Calendar.MONTH, occurrences * 6);
                firstOccurrenceAfter = (Calendar) lastOccurrenceBefore.clone();
                if (lastOccurrenceBefore.before(instant)) {
                    firstOccurrenceAfter.add(Calendar.MONTH, 6);
                } else {
                    lastOccurrenceBefore.add(Calendar.MONTH, -6);
                }
                break;

            case ANNUALLY:
                lastOccurrenceBefore = (Calendar) instant.clone();
                lastOccurrenceBefore.set(Calendar.MONTH, startDate.get(Calendar.MONTH));
                lastOccurrenceBefore.set(Calendar.DAY_OF_MONTH, startDate.get(Calendar.DAY_OF_MONTH));
                firstOccurrenceAfter = (Calendar) lastOccurrenceBefore.clone();
                if (startDate.get(Calendar.MONTH) < instant.get(Calendar.MONTH) ||
                        (startDate.get(Calendar.MONTH) == instant.get(Calendar.MONTH) &&
                                startDate.get(Calendar.DAY_OF_MONTH) <= instant.get(Calendar.DAY_OF_MONTH))) {
                    firstOccurrenceAfter.add(Calendar.YEAR, 1);
                } else {
                    lastOccurrenceBefore.add(Calendar.YEAR, -1);
                }
                break;

            case ON_DEMAND:
                // For on-demand items, their is no closest occurrence:
                return null;

            default:
                throw new IllegalArgumentException("Unsupported period type: " + period);
        }

        // Now determine which of the two occurrences is closest to the specified date:
        long diffToLast = Math.abs(instant.getTimeInMillis() - lastOccurrenceBefore.getTimeInMillis());
        long diffToNext = Math.abs(firstOccurrenceAfter.getTimeInMillis() - instant.getTimeInMillis());
        if (diffToLast <= diffToNext) {
            return lastOccurrenceBefore;
        } else {
            return firstOccurrenceAfter;
        }
    }
}