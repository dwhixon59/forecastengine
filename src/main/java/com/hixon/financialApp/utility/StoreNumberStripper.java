package com.hixon.financialApp.utility;

import com.hixon.financialApp.model.merchant.Merchant;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes store, reference and phone numbers from a merchant payee so that every location of the
 * same brand normalizes to one merchant payee.
 *
 * <p>Card networks append a store number (and often a truncated location) to the merchant name, so
 * the same brand arrives as a different payee at every store: {@code "STARBUCKS STORE 08"},
 * {@code "STARBUCKS 15"}, {@code "TACO BELL 03"}, {@code "RACETRAC2381"},
 * {@code "CHICK-FIL-A #03238"}.  Each variant looks like a brand-new payee, so the user is asked to
 * identify the merchant again and the {@code merchant_payee} table fills up with near-duplicates.
 * Stripping the store number collapses them all to {@code "STARBUCKS"}, {@code "TACO BELL"},
 * {@code "RACETRAC"} and {@code "CHICK-FIL-A"}.</p>
 *
 * <p><strong>The hard part is that a number is sometimes part of the merchant's actual name</strong>
 * &mdash; "Pier 1 Imports", "Seasons 52", "Pub 32 Irish Gastropub", "7-Eleven", "Home2 Suites".
 * Before removing a number this class asks a {@link MerchantNameLookup} whether the text up to and
 * including that number is (or begins) a known merchant name; if it is, the number is left alone and
 * the scan moves on to the next candidate.  That is what lets
 * {@code "PIER 1 IMPORTS 494 FORT WORTH TX"} normalize to {@code "PIER 1 IMPORTS"} rather than
 * {@code "PIER"}.</p>
 *
 * <p>Transfer payees are never touched: their masked counterparty account number
 * (e.g. {@code XXXXXX8249}) is meaningful data, not noise.</p>
 *
 * @see com.hixon.financialApp.model.financialinstitution.WellsFargoBank
 * @see com.hixon.financialApp.model.financialinstitution.CitiBank
 * @see com.hixon.financialApp.model.financialinstitution.BarclaysBank
 */
public final class StoreNumberStripper {

    /**
     * Looks up whether a candidate string is, or begins, the name of a known merchant.  Abstracted
     * behind an interface so the stripping logic can be unit tested without a live database.
     */
    @FunctionalInterface
    public interface MerchantNameLookup {
        /**
         * @param candidate the payee text up to and including a numeric token
         * @return true if a merchant's name equals the candidate or starts with it followed by a
         *         word boundary (so "PIER 1" matches the merchant "Pier 1 Imports")
         */
        boolean isKnownMerchantNamePrefix(String candidate);
    }

    /** A token that is nothing but digits, e.g. the "126" in "RACETRAC 126". */
    private static final Pattern PURE_NUMBER = Pattern.compile("\\d+");

    /**
     * A token introduced by "#" and at least two digits, e.g. "#1863" or "#03238".  Two digits are
     * required so a reference code such as "#IB0VM5S5WL" is not mistaken for a store number.
     */
    private static final Pattern HASH_NUMBER = Pattern.compile("#\\d{2,}.*");

    /**
     * A run of three or more digits, used to find a store/reference/phone number glued onto the
     * merchant name (e.g. the "2381" in "RACETRAC2381").  Three digits are required because shorter
     * runs occur inside real names ("HOME2 SUITES", "ROW8", "7-ELEVEN").
     */
    private static final Pattern LONG_DIGIT_RUN = Pattern.compile("\\d{3,}");

    /**
     * A masked account number such as {@code XXXXXX8249} or {@code ****8249}.  These identify the
     * counterparty of a transfer and must never be stripped.
     */
    private static final Pattern MASKED_ACCOUNT = Pattern.compile("[xX*]{2,}\\d+");

    /** A payee that describes a transfer between accounts; left untouched entirely. */
    private static final Pattern TRANSFER_PAYEE =
            Pattern.compile("(?i)^(?:online\\s+|recurring\\s+|atm\\s+|save\\s+as\\s+you\\s+)?transfer\\b.*");

    /**
     * Labels that introduce a store number and are therefore dropped along with it, so
     * "STARBUCKS STORE 08" becomes "STARBUCKS" rather than "STARBUCKS STORE".  Deliberately narrow:
     * "STORES" is excluded because it is part of real names such as "BURLINGTON STORES".
     */
    private static final Set<String> STORE_NUMBER_LABELS = Set.of("STORE", "STR", "STORE#", "#");

    /** Trailing separator characters left behind after a store number is removed. */
    private static final Pattern TRAILING_SEPARATORS = Pattern.compile("[\\s*#,\\-/]+$");

    /** Leading/trailing characters that are neither letters nor digits, trimmed from a fragment. */
    private static final Pattern EDGE_NON_ALPHANUMERIC = Pattern.compile("^[^\\p{Alnum}]+|[^\\p{Alnum}]+$");

    /**
     * Merchant names that contain a digit, lower-cased, cached for the life of the process.  Only
     * names containing a digit can ever protect a numeric token, so the rest are not worth loading.
     * Invalidated by {@link Merchant#save()} whenever a merchant is added or renamed.
     */
    private static volatile List<String> cachedMerchantNames;

    /** Production lookup backed by the {@code merchant} table. */
    private static final MerchantNameLookup DB_MERCHANT_LOOKUP = StoreNumberStripper::isKnownMerchantNamePrefix;

    private StoreNumberStripper() {
    }

    /**
     * Removes a store/reference number (and any trailing location noise that follows it) from a
     * merchant payee, using the {@code merchant} table to recognize numbers that are part of a real
     * merchant name.
     *
     * @param payee the merchant payee produced by an institution's parser
     * @return the payee with the store number removed, or the payee unchanged when there is nothing
     *         safe to remove
     */
    public static String strip(String payee) {
        return strip(payee, DB_MERCHANT_LOOKUP);
    }

    /**
     * Removes a store/reference number from a merchant payee.  This method is pure (it performs no
     * I/O of its own; any database access is delegated to the supplied {@link MerchantNameLookup})
     * so its behavior can be unit tested directly.
     *
     * <p>Rules, applied to the first numeric token that is not protected by the merchant lookup:</p>
     * <ol>
     *   <li>A token that is nothing but digits ("RACETRAC <b>126</b>"), or a "#"-introduced number
     *       ("CHICK-FIL-A <b>#03238</b>"), is dropped.</li>
     *   <li>A number glued onto the name ("<b>RACETRAC2381</b>", "<b>LINKEDIN-600</b>") is split at
     *       the first run of three or more digits and the leading fragment is kept &mdash; but only
     *       when that fragment looks like a name rather than part of a reference code (see
     *       {@link #storeNumberFragment(String)}).</li>
     *   <li>Everything after the number is dropped: for every institution, what follows a store
     *       number is location or reference noise ("STARBUCKS STORE 08 PALM", "SURF STYLE 840
     *       MIAMI").  This does not apply when the number is in the very first token, which carries
     *       no preceding merchant name to attach a store to.</li>
     *   <li>A "STORE"/"STR"/"#" label immediately before the number is dropped too.</li>
     * </ol>
     *
     * <p>If there is no store number to remove, or removing it would empty the payee, the payee is
     * returned byte-for-byte unchanged so that payees already mapped in {@code merchant_payee} keep
     * matching.</p>
     *
     * @param payee  the merchant payee produced by an institution's parser
     * @param lookup the merchant-name lookup used to protect numbers that belong to a real name;
     *               may be null to skip that protection
     * @return the payee with the store number removed, or the payee unchanged
     */
    static String strip(String payee, MerchantNameLookup lookup) {
        if (payee == null) {
            return null;
        }
        String collapsed = payee.trim().replaceAll("\\s+", " ");
        if (collapsed.isEmpty()) {
            return payee;
        }

        // A transfer's masked account number is data, not noise - leave transfers completely alone.
        if (TRANSFER_PAYEE.matcher(collapsed).matches() || MASKED_ACCOUNT.matcher(collapsed).find()) {
            return payee;
        }

        String[] tokens = collapsed.split(" ");
        boolean modified = false;
        for (int i = 0; i < tokens.length; i++) {
            String fragment = storeNumberFragment(tokens[i]);
            if (fragment == null) {
                continue;  // not a store/reference number
            }

            // The number may be part of the merchant's real name ("PIER 1", "SEASONS 52"). If so,
            // keep it and look for a later number instead.
            if (lookup != null && lookup.isKnownMerchantNamePrefix(join(tokens, i + 1))) {
                continue;
            }

            if (i == 0) {
                // Nothing precedes the number, so it is not a store number appended to a merchant
                // name: it is either the name itself ("RACETRAC2381") or a processor prefix
                // ("PPC8013 EE Dir Dep").  Clean the token but keep the words that follow, and keep
                // scanning for a real store number further along.
                if (!fragment.isEmpty() && !fragment.equals(tokens[0])) {
                    tokens[0] = fragment;
                    modified = true;
                }
                continue;
            }

            int end = i;  // exclusive end of the tokens kept before the number
            if (fragment.isEmpty()) {
                // Drop a "STORE"/"STR"/"#" label that only exists to introduce the number.
                while (end > 0 && STORE_NUMBER_LABELS.contains(trimEdges(tokens[end - 1]).toUpperCase(Locale.ROOT))) {
                    end--;
                }
            }

            String kept = join(tokens, end);
            if (!fragment.isEmpty()) {
                kept = kept.isEmpty() ? fragment : kept + " " + fragment;
            }
            kept = TRAILING_SEPARATORS.matcher(kept).replaceAll("");
            if (!kept.isEmpty()) {
                return kept;
            }
            // Stripping here would leave nothing; keep looking for a later, safely removable number.
        }

        // Nothing was removed.  Return the payee exactly as it came in rather than the whitespace-
        // collapsed form, so a payee without a store number keeps matching its merchant_payee row.
        return modified ? join(tokens, tokens.length) : payee;
    }

    /**
     * Classifies a token as a store/reference number and returns the part of it worth keeping.
     *
     * @param token the token to classify
     * @return {@code null} if the token is not a store/reference number; {@code ""} if the whole
     *         token is one; otherwise the leading merchant-name fragment of the token (e.g.
     *         {@code "RACETRAC"} for {@code "RACETRAC2381"})
     */
    private static String storeNumberFragment(String token) {
        // A masked account number belongs to a transfer, not a store.
        if (MASKED_ACCOUNT.matcher(token).find()) {
            return null;
        }
        if (PURE_NUMBER.matcher(token).matches() || HASH_NUMBER.matcher(token).matches()) {
            return "";
        }

        Matcher digitRun = LONG_DIGIT_RUN.matcher(token);
        if (!digitRun.find()) {
            return null;  // no long digit run: not a store or reference number
        }

        String fragment = trimEdges(token.substring(0, digitRun.start()));
        if (fragment.isEmpty()) {
            return "";
        }
        if (fragment.chars().noneMatch(Character::isDigit)) {
            // A plain word before the number is the merchant name ("RACETRAC2381", "BP#6984843SUNSH"),
            // but a lone letter is part of the reference code ("MCDONALD'S F19080").
            return countLetters(fragment) >= 2 ? fragment : "";
        }
        // The fragment itself mixes letters and digits. Keep it only when it starts with a real word
        // ("ONE9_00089" -> "ONE9"); otherwise it is reference code ("P3E6A1283E", "2-K205051").
        return leadingLetterRun(fragment).length() >= 3 ? fragment : "";
    }

    /**
     * Joins the first {@code count} tokens with single spaces.
     *
     * @param tokens the token array
     * @param count  how many leading tokens to join
     * @return the joined string (empty when {@code count} is zero)
     */
    private static String join(String[] tokens, int count) {
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (joined.length() > 0) {
                joined.append(' ');
            }
            joined.append(tokens[i]);
        }
        return joined.toString();
    }

    /**
     * Removes leading and trailing characters that are neither letters nor digits, so a fragment such
     * as {@code "SECURITY*"} or {@code "BP#"} reduces to the bare word.
     *
     * @param text the text to trim
     * @return the trimmed text
     */
    private static String trimEdges(String text) {
        return EDGE_NON_ALPHANUMERIC.matcher(text).replaceAll("");
    }

    /**
     * @param text the text to inspect
     * @return the number of letters in the text
     */
    private static int countLetters(String text) {
        return (int) text.chars().filter(Character::isLetter).count();
    }

    /**
     * @param text the text to inspect
     * @return the leading run of letters, or an empty string when the text does not start with one
     */
    private static String leadingLetterRun(String text) {
        int i = 0;
        while (i < text.length() && Character.isLetter(text.charAt(i))) {
            i++;
        }
        return text.substring(0, i);
    }

    /*
     * Merchant name lookup:
     */

    /**
     * Discards the cached merchant names so a merchant added or renamed during this session is taken
     * into account by the next payee that is normalized.
     */
    public static void invalidateMerchantNameCache() {
        cachedMerchantNames = null;
    }

    /**
     * Determines whether a merchant's name equals the candidate, or starts with the candidate
     * followed by a word boundary.  The prefix form is what protects "PUB 32" (the merchant is
     * "Pub 32 Irish Gastropub") and "CLEARWATER 7" (the merchant is "Clearwater 7 Inc").
     *
     * @param candidate the payee text up to and including a numeric token
     * @return true if the candidate is, or begins, a known merchant name
     */
    private static boolean isKnownMerchantNamePrefix(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        String needle = candidate.toLowerCase(Locale.ROOT);
        for (String merchantName : getMerchantNamesContainingDigits()) {
            if (merchantName.equals(needle) || merchantName.startsWith(needle + " ")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the lower-cased names of every merchant whose name contains a digit, loading them from
     * the database on first use.  A database failure degrades to "no protected names" (and is not
     * cached) rather than breaking the import.
     *
     * @return the lower-cased merchant names containing a digit
     */
    private static List<String> getMerchantNamesContainingDigits() {
        List<String> names = cachedMerchantNames;
        if (names != null) {
            return names;
        }
        try {
            List<String> loaded = new ArrayList<>();
            for (String name : Merchant.getNamesContainingDigits()) {
                loaded.add(name.toLowerCase(Locale.ROOT));
            }
            cachedMerchantNames = loaded;
            return loaded;
        } catch (Exception e) {
            // No connection (or a transient failure): normalize without the protection this time.
            return List.of();
        }
    }
}
