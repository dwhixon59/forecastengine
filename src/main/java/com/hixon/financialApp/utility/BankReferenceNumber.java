package com.hixon.financialApp.utility;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the bank's own reference number for a transfer out of a transaction payee.
 *
 * <p>Wells Fargo issues a reference for a transfer and writes <b>the same string into both
 * sides</b>:
 *
 * <pre>
 * Bill Pay Dave    -30.00  ONLINE TRANSFER TO   HIXON D ... REF #IB0ZBFJRYR ON 08/11/26
 * Bill Pay Danni   +30.00  ONLINE TRANSFER FROM HIXON D ... REF #IB0ZBFJRYR ON 08/11/26
 * </pre>
 *
 * <p>Where both sides carry it, that is an exact identity for "these two rows are the same movement
 * of money" -- no scoring involved.  But it is present on only about 43% of transfers and there is
 * no column for it, so:
 *
 * <blockquote>
 * <b>The reference confirms a match.  It never gates one.</b>
 * </blockquote>
 *
 * <p>Nothing may be conditional on a reference being present, and no counterpart may be suppressed
 * for want of one.  Every method here returns null or false rather than throwing when there is no
 * reference to read, so a caller that forgets the rule fails in the harmless direction.
 */
public final class BankReferenceNumber {

    /**
     * {@code REF #IB0ZBFJRYR}.  The space after {@code REF} and around the {@code #} are optional
     * because the payee text is not perfectly consistent across import formats.
     */
    private static final Pattern REFERENCE_PATTERN =
            Pattern.compile("\\bREF\\s*#\\s*([A-Za-z0-9]{4,})", Pattern.CASE_INSENSITIVE);

    private BankReferenceNumber() {
    }

    /**
     * Extract the bank reference from a raw transaction payee.
     *
     * @param payee the raw payee string as downloaded from the bank
     * @return the reference (e.g. {@code IB0ZBFJRYR}), or null if the payee carries none -- which is
     *         the majority case and is entirely normal
     */
    public static String extract(String payee) {
        if (payee == null || payee.isEmpty()) {
            return null;
        }
        Matcher matcher = REFERENCE_PATTERN.matcher(payee);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        return null;
    }

    /**
     * Whether two references are known to describe the same movement of money.
     *
     * <p>Only true when both are present and equal.  A missing reference on either side is not
     * evidence of anything.
     */
    public static boolean areSameMovement(String one, String other) {
        return one != null && other != null && one.equalsIgnoreCase(other);
    }

    /**
     * Whether two references are known to describe <em>different</em> movements of money.
     *
     * <p>This is the half that earns its keep:  a differing reference rules a candidate out
     * outright, which scoring alone cannot do.  It prevents the matcher from taking a plausible but
     * wrong candidate for the same money and stranding the right one.
     *
     * <p>Only true when both are present and differ.
     */
    public static boolean areDifferentMovements(String one, String other) {
        return one != null && other != null && !one.equalsIgnoreCase(other);
    }
}
