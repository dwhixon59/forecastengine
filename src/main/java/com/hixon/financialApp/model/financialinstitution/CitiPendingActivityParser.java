package com.hixon.financialApp.model.financialinstitution;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the pending charges out of a copy of the Citi portal's <em>Your Activity</em> page.
 *
 * <p>Citi's downloads (QFX and CSV) carry posted activity only, so pending charges come from the
 * user pasting the activity table into a text file.  A paste spans one line per field, and usually
 * a lot of page around the table:  offers, the credit score, the statement dates.  This class picks
 * the pending rows out of that.  It does no I/O, so it can be tested on real pastes directly.  See
 * CITI_PENDING_TRANSACTIONS_DESIGN.md &sect;3.3.</p>
 *
 * <p>A row reads, one field per line:</p>
 * <pre>
 * Sep 15, 2026
 * LA FITNESS IRVINE USA
 * DAVID W HIXON
 * $80.23
 * -----
 * </pre>
 * <p>The date starts a record, the first two text lines are the description and the cardholder, and
 * the amount completes it.  What follows the amount is the running balance ({@code -----} for a
 * pending row, a dollar figure for a posted one), which is ignored along with any other amount that
 * no date precedes, such as the section totals.</p>
 */
public final class CitiPendingActivityParser {

    private static final Logger logger = LogManager.getLogger(CitiPendingActivityParser.class);

    /** A transaction date, the whole line:  "Sep 15, 2026". */
    private static final Pattern DATE = Pattern.compile("[A-Z][a-z]{2} \\d{1,2}, \\d{4}");

    /** An amount, the whole line:  "$80.23", "-$750.00", "$11,610.12". */
    private static final Pattern AMOUNT = Pattern.compile("(-?)\\$([\\d,]+\\.\\d{2})");

    /** The time period "Since Sep 11, 2026". */
    private static final Pattern SINCE = Pattern.compile("Since (" + DATE.pattern() + ")");

    /** The time period "Sep 14, 2026 - Sep 15, 2026" (a hyphen or an en dash). */
    private static final Pattern BETWEEN =
            Pattern.compile("(" + DATE.pattern() + ")\\s*[-–]\\s*(" + DATE.pattern() + ")");

    private static final String PENDING_HEADING = "Pending Total";
    private static final String POSTED_HEADING = "Posted Total";

    /** What the Name column shows when no cardholder is attached to a row. */
    private static final String NO_CARDHOLDER = "N/A";

    /** How many text lines may follow a date before a record with no amount is given up on. */
    static final int MAX_LINES_BEFORE_AMOUNT = 4;

    /** One pending charge or credit read from the paste. */
    public record PendingRecord(Calendar date, double amount, String description, String cardholder) {
    }

    /**
     * What a paste holds:  its pending records in paste order, and the days the paste covers.
     *
     * @param records      the pending records, in the order they appear
     * @param coveredRange the days the paste speaks for; a register row outside it cannot have
     *                     "fallen off"
     */
    public record Result(List<PendingRecord> records, DateRange coveredRange) {
    }

    private enum Section { NONE, PENDING, POSTED }

    private CitiPendingActivityParser() {
    }

    /**
     * Reads the pending records from a paste of the activity page.
     *
     * <p>Only rows under the {@code Pending Total} heading are returned; rows under {@code Posted Total}
     * belong to the QFX import.  A paste with no headings at all is treated as pending, and the
     * import's duplicate checks catch anything that has already posted.</p>
     *
     * <p>Amounts are returned with the register's sign:  a portal purchase {@code $80.23} is
     * {@code -80.23}, and a portal credit {@code -$750.00} is {@code +750.00}.</p>
     *
     * @param lines the lines of the paste
     * @param today today's date, the end of a "Since ..." time period
     * @return the pending records and the covered range
     */
    public static Result parse(List<String> lines, Calendar today) {
        List<String> fields = splitIntoFields(lines);

        List<PendingRecord> pending = new ArrayList<>();
        DateRange period = null;
        Calendar earliest = null;
        Calendar latest = null;

        Section section = Section.NONE;
        OpenRecord open = null;

        for (String field : fields) {

            // The time period, wherever it appears.
            if (period == null) {
                DateRange parsedPeriod = parsePeriod(field, today);
                if (parsedPeriod != null) {
                    period = parsedPeriod;
                    continue;
                }
            }

            // A section heading ends any record still open and switches sections.
            if (field.equals(PENDING_HEADING) || field.equals(POSTED_HEADING)) {
                dropIfOpen(open, "a section heading");
                open = null;
                section = field.equals(PENDING_HEADING) ? Section.PENDING : Section.POSTED;
                continue;
            }

            // A date starts a record.
            if (DATE.matcher(field).matches()) {
                Calendar date = parseDate(field);
                if (date != null) {
                    dropIfOpen(open, "the next date");
                    open = new OpenRecord(date, section);
                    continue;
                }
            }

            // Outside a record nothing else counts:  totals, running balances, page text.
            if (open == null) {
                continue;
            }

            // An amount completes the record.
            Matcher amount = AMOUNT.matcher(field);
            if (amount.matches()) {
                if (open.description == null) {
                    dropIfOpen(open, "an amount with no description");
                } else {
                    earliest = earlier(earliest, open.date);
                    latest = later(latest, open.date);
                    if (open.section != Section.POSTED) {
                        pending.add(new PendingRecord(open.date, registerAmount(amount), open.description,
                                open.cardholder));
                    }
                }
                open = null;
                continue;
            }

            // Otherwise it is the description, then the cardholder.
            open.textLines++;
            if (open.textLines > MAX_LINES_BEFORE_AMOUNT) {
                dropIfOpen(open, MAX_LINES_BEFORE_AMOUNT + " lines with no amount");
                open = null;
            } else if (open.description == null) {
                open.description = field;
            } else if (open.textLines == 2) {
                open.cardholder = field.equalsIgnoreCase(NO_CARDHOLDER) ? null : field;
            }
        }
        dropIfOpen(open, "the end of the paste");

        DateRange covered;
        if (period != null) {
            covered = period;
        } else if (earliest != null) {
            covered = DateRange.of(earliest, latest);
        } else {
            covered = DateRange.empty();
        }
        return new Result(pending, covered);
    }

    /**
     * Trims every line, drops blank ones, and splits a line holding tabs into one field per cell.  A
     * browser sometimes copies a table row as one tab-separated line instead of a line per cell.
     */
    static List<String> splitIntoFields(List<String> lines) {
        List<String> fields = new ArrayList<>();
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            for (String cell : line.split("\t")) {
                String trimmed = cell.strip();
                if (!trimmed.isEmpty()) {
                    fields.add(trimmed);
                }
            }
        }
        return fields;
    }

    /**
     * The range a time-period line names, or null if the line is not one.  A "Since" period ends
     * today; a start after today (a clock that is behind) is taken as a one-day range.
     */
    static DateRange parsePeriod(String field, Calendar today) {
        Matcher since = SINCE.matcher(field);
        if (since.matches()) {
            Calendar start = parseDate(since.group(1));
            if (start == null) {
                return null;
            }
            return start.after(today) ? DateRange.of(start, start) : DateRange.of(start, today);
        }
        Matcher between = BETWEEN.matcher(field);
        if (between.matches()) {
            Calendar start = parseDate(between.group(1));
            Calendar end = parseDate(between.group(2));
            if (start == null || end == null) {
                return null;
            }
            return end.before(start) ? DateRange.of(end, start) : DateRange.of(start, end);
        }
        return null;
    }

    /** "Sep 15, 2026" as a Calendar at midnight, or null if it is not a real date. */
    static Calendar parseDate(String text) {
        SimpleDateFormat format = new SimpleDateFormat("MMM d, yyyy", Locale.US);
        format.setLenient(false);
        try {
            Calendar date = Calendar.getInstance();
            date.setTime(format.parse(text));
            return date;
        } catch (ParseException e) {
            return null;
        }
    }

    /** The portal amount with the register's sign:  purchases negative, credits positive. */
    private static double registerAmount(Matcher amount) {
        double magnitude = Double.parseDouble(amount.group(2).replace(",", ""));
        boolean portalCredit = !amount.group(1).isEmpty();
        return portalCredit ? magnitude : -magnitude;
    }

    private static void dropIfOpen(OpenRecord open, String reason) {
        if (open != null) {
            logger.debug("Dropped the Citi activity row dated {} ({}): {}",
                    String.format("%1$tY-%1$tm-%1$td", open.date), open.description, reason);
        }
    }

    private static Calendar earlier(Calendar a, Calendar b) {
        return a == null || b.before(a) ? b : a;
    }

    private static Calendar later(Calendar a, Calendar b) {
        return a == null || b.after(a) ? b : a;
    }

    /** A record whose date has been read but whose amount has not. */
    private static final class OpenRecord {
        final Calendar date;
        final Section section;
        String description;
        String cardholder;
        int textLines;

        OpenRecord(Calendar date, Section section) {
            this.date = date;
            this.section = section;
        }
    }
}
