package com.hixon.financialApp.model.financialinstitution;

import java.util.Calendar;

/**
 * The days a pending-transactions file covers, both ends inclusive and compared by calendar day only.
 *
 * <p>A pending import may treat a register row as "fallen off the bank's list" only when the row's
 * date is inside the range the file covers.  A Citi paste filtered to Sep 14 - Sep 15 says nothing
 * about a charge dated Sep 10, and must not offer it for deletion.  Wells Fargo files cover
 * everything pending, so their range is {@link #unbounded()}.</p>
 */
public final class DateRange {

    private static final DateRange UNBOUNDED = new DateRange(null, null, false);
    private static final DateRange EMPTY = new DateRange(null, null, true);

    private final Calendar start;
    private final Calendar end;
    private final boolean empty;

    private DateRange(Calendar start, Calendar end, boolean empty) {
        this.start = start == null ? null : (Calendar) start.clone();
        this.end = end == null ? null : (Calendar) end.clone();
        this.empty = empty;
    }

    /** A range that contains every date. */
    public static DateRange unbounded() {
        return UNBOUNDED;
    }

    /** A range that contains no date:  a file that covers nothing lets nothing fall off. */
    public static DateRange empty() {
        return EMPTY;
    }

    /**
     * The days from {@code start} through {@code end}, inclusive.
     *
     * @throws IllegalArgumentException if either end is null or {@code end} is before {@code start}
     */
    public static DateRange of(Calendar start, Calendar end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("A date range needs both a start and an end.");
        }
        if (dayKey(end) < dayKey(start)) {
            throw new IllegalArgumentException("A date range cannot end before it starts.");
        }
        return new DateRange(start, end, false);
    }

    /**
     * Whether {@code date} falls on a day inside the range.
     *
     * @param date the date to test; a null date is never inside a bounded or empty range
     */
    public boolean contains(Calendar date) {
        if (empty) {
            return false;
        }
        if (start == null) {
            return true;  // unbounded
        }
        if (date == null) {
            return false;
        }
        int day = dayKey(date);
        return day >= dayKey(start) && day <= dayKey(end);
    }

    /** The first day of the range, or null when the range is unbounded or empty. */
    public Calendar getStart() {
        return start == null ? null : (Calendar) start.clone();
    }

    /** The last day of the range, or null when the range is unbounded or empty. */
    public Calendar getEnd() {
        return end == null ? null : (Calendar) end.clone();
    }

    public boolean isUnbounded() {
        return this == UNBOUNDED;
    }

    public boolean isEmpty() {
        return empty;
    }

    /** Whether the range names specific days:  neither unbounded nor empty. */
    public boolean isBounded() {
        return start != null;
    }

    /** A calendar day as a sortable yyyymmdd integer, ignoring the time of day. */
    private static int dayKey(Calendar c) {
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH);
    }

    @Override
    public String toString() {
        if (empty) {
            return "(no dates)";
        }
        if (start == null) {
            return "(all dates)";
        }
        return String.format("%1$tY-%1$tm-%1$td .. %2$tY-%2$tm-%2$td", start, end);
    }
}
