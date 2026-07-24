package com.hixon.financialApp.model.financialinstitution;

import com.hixon.financialApp.controller.SessionController;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.utility.CityStateChecker;
import org.apache.commons.csv.CSVRecord;

import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Citi (Citibank) financial institution implementation.
 *
 * <p>Handles transaction imports from Citi credit card accounts (e.g. the Citi AAdvantage
 * Mastercard) using QFX (Quicken Financial Exchange) format files.
 *
 * <p><strong>Supported Formats:</strong>
 * <ul>
 *   <li>QFX/OFX files (inherited from {@link FinancialInstitution})</li>
 * </ul>
 *
 * <p><strong>Iterator Pattern:</strong> This class uses the inherited iterator implementation
 * from {@link FinancialInstitution} to provide sequential access to transactions.
 *
 * @see FinancialInstitution
 * @see BarclaysBank
 */
public class CitiBank extends FinancialInstitution {

    /**
     * Creates a new CitiBank instance.
     *
     * @param sessionController the session controller containing register, budget, forecast, view, and notificationService
     */
    public CitiBank(SessionController sessionController) {
        super(sessionController);
    }

    // ========================================
    // CSV Methods (Not Supported)
    // ========================================

    @Override
    public Class<? extends Enum<?>> getCsvHeadersClass() {
        throw new UnsupportedOperationException("Citi uses QFX format, not CSV");
    }

    @Override
    public org.apache.commons.csv.CSVFormat getCsvFormat() {
        throw new UnsupportedOperationException("Citi uses QFX format, not CSV");
    }

    @Override
    public String getRegisterImportRecordBaseName(CSVRecord record) throws ParseException {
        throw new UnsupportedOperationException("Citi uses QFX format, not CSV");
    }

    @Override
    public Transaction createFromCSVRecord(CSVRecord record, String importRecordId) throws Exception {
        throw new UnsupportedOperationException("Citi uses QFX format, not CSV");
    }

    @Override
    public Transaction loadProvisionalTransactionFromCSV(String line, Register register) throws Exception {
        throw new UnsupportedOperationException("Citi uses QFX format, not CSV. QFX does not support provisional transactions.");
    }

    @Override
    public Transaction getMatchingProvisionalTransaction(Transaction clearedTransaction)
            throws RegisterException, SQLException, EntityException, ParseException, Exception {
        // QFX transactions are always cleared, no provisional transactions
        return null;
    }

    // ========================================
    // Citi-Specific Methods
    // ========================================

    /**
     * Two-letter U.S. state abbreviations, used to strip a trailing location (city + state) from a
     * Citi credit-card descriptor.  Only the very last token is ever tested against this set, so
     * abbreviations that could clash with real words elsewhere in a name are not a concern here.
     */
    private static final Set<String> STATE_ABBREVIATIONS = Set.of(
            "AL", "AK", "AS", "AZ", "AR", "CA", "CO", "CT", "DE", "DC", "FM", "FL", "GA", "GU", "HI",
            "ID", "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MH", "MD", "MA", "MI", "MN", "MS", "MO",
            "MT", "NE", "NV", "NH", "NJ", "NM", "NY", "NC", "ND", "MP", "OH", "OK", "OR", "PW", "PA",
            "PR", "RI", "SC", "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY");

    /**
     * Matches a token that is (or contains) a store number, reference number, or phone number - any
     * token with a run of three or more digits (e.g. "800420166", "2-K205051", "SECURITY*320925392")
     * or a "#"-prefixed store number (e.g. "#1863").  Everything from the first such token onward is
     * treated as trailing noise/location and removed.
     */
    private static final Pattern REFERENCE_TOKEN = Pattern.compile(".*\\d{3,}.*");

    /**
     * TLD tokens that, when they appear as a standalone word, are dropped (e.g. the "COM" in
     * "HEADSHOP COM ENCINITAS CA").  Restricted to the unambiguous ones — two-letter TLDs such as
     * "CO"/"IO"/"US" are intentionally excluded because they clash with real words/state codes.
     */
    private static final Set<String> STANDALONE_TLDS = Set.of("COM", "NET", "ORG", "INFO", "BIZ");

    /**
     * Matches a trailing domain suffix on a token so that a web-style merchant descriptor is reduced
     * to its brand name (e.g. "NETFLIX.COM" &rarr; "NETFLIX", "VXNBILL.COM" &rarr; "VXNBILL").
     */
    private static final Pattern DOMAIN_SUFFIX = Pattern.compile("(?i)\\.(com|net|org|co|io|biz|info|us)$");

    /**
     * The maximum number of words a stripped city name may span (e.g. "Los Gatos" = 2, "Salt Lake
     * City" = 3).  Multi-word city removal is only attempted up to this length.
     */
    private static final int MAX_CITY_WORDS = 3;

    /**
     * Looks up whether a (city, state) pair is a known U.S. city.  Abstracted behind an interface so
     * the normalization logic can be unit tested with an in-memory lookup instead of a live database.
     */
    @FunctionalInterface
    interface CityStateLookup {
        boolean isCity(String city, String stateAbbreviation);
    }

    /**
     * Production city lookup backed by the {@code cities} table.  Any error (including no database
     * connection) is treated as "not a known city" so normalization degrades gracefully.
     */
    private static final CityStateLookup DB_CITY_LOOKUP = (city, state) -> {
        try {
            return CityStateChecker.exists(city, state);
        } catch (Exception e) {
            return false;
        }
    };

    /**
     * Parses merchant/payee information from a Citi credit-card transaction descriptor.
     *
     * <p>Citi (like most card networks) appends store numbers, reference/phone numbers, a web-domain
     * suffix, and a truncated city/state to the merchant name, e.g. {@code "THE HOME DEPOT #1863
     * BRADEN"}, {@code "NETFLIX.COM LOS GATOS CA"}, {@code "MANATEECOUNTYUTIL 800420166"}, or
     * {@code "VXNBILL.COM CAMDEN DE"}.  If that raw string were used as the merchant payee, every
     * store location would look like a brand-new payee and the fuzzy merchant search could not find
     * an existing merchant.  This method normalizes the descriptor to a stable merchant name so that
     * (a) the fuzzy search surfaces the right merchant, and (b) the saved payee&rarr;merchant mapping
     * matches future transactions from the same merchant regardless of store number or location.</p>
     *
     * @param date   the transaction date (unused for Citi)
     * @param amount the transaction amount (unused for Citi)
     * @param payee  the raw payee/descriptor string from the Citi QFX file
     * @return a normalized merchant name suitable for matching and display
     */
    @Override
    public String parseMerchantPayee(Calendar date, double amount, String payee) throws Exception {
        return normalizeCitiPayee(payee);
    }

    /**
     * Normalizes a Citi credit-card descriptor into a stable merchant name using the production city
     * lookup (the {@code cities} table).  See
     * {@link #parseMerchantPayee(Calendar, double, String)} for the rationale.
     *
     * @param payee the raw payee/descriptor string
     * @return the normalized merchant name, or the original payee if normalization yields nothing
     */
    static String normalizeCitiPayee(String payee) {
        return normalizeCitiPayee(payee, DB_CITY_LOOKUP);
    }

    /**
     * Normalizes a Citi credit-card descriptor into a stable merchant name.  This method is pure (it
     * performs no I/O of its own; any database access is delegated to the supplied
     * {@link CityStateLookup}) so its behavior can be unit tested directly.
     *
     * <p>Rules, applied in order:</p>
     * <ol>
     *   <li>Strip web-domain suffixes: "NETFLIX.COM" &rarr; "NETFLIX", and drop standalone TLD tokens
     *       such as "COM".</li>
     *   <li>If a store/reference/phone-number token is present (a run of 3+ digits, or a
     *       "#"-prefixed number), keep only the tokens before it plus any leading letters of that
     *       token when they form a meaningful fragment of at least three letters (e.g.
     *       {@code "SECURITY*320925392"} contributes {@code "SECURITY"}, but the {@code "P"} in
     *       {@code "P3E6A1283E"} is dropped), and drop everything after it (which is trailing
     *       location).</li>
     *   <li>Otherwise, if the last token is a U.S. state abbreviation, drop it and the city that
     *       precedes it.  A multi-word city (e.g. "LOS GATOS") is recognized via the supplied city
     *       lookup; if no known multi-word city matches, a single (possibly truncated) city token is
     *       dropped as a fallback.</li>
     * </ol>
     *
     * <p>If normalization would remove everything, the original payee is returned unchanged.</p>
     *
     * @param payee      the raw payee/descriptor string
     * @param cityLookup the (city, state) lookup used to recognize multi-word cities
     * @return the normalized merchant name, or the original payee if normalization yields nothing
     */
    static String normalizeCitiPayee(String payee, CityStateLookup cityLookup) {
        if (payee == null) {
            return null;
        }
        String trimmed = payee.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) {
            return payee;
        }

        List<String> tokens = new ArrayList<>(Arrays.asList(trimmed.split(" ")));

        // Rule 1: strip web-domain suffixes and standalone TLD tokens.
        tokens = stripDomainSuffixes(tokens);
        if (tokens.isEmpty()) {
            return trimmed;
        }

        // Rule 2: truncate at the first store/reference/phone-number token.
        int refIndex = -1;
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (REFERENCE_TOKEN.matcher(token).matches() || token.startsWith("#")) {
                refIndex = i;
                break;
            }
        }
        if (refIndex >= 0) {
            List<String> kept = new ArrayList<>(tokens.subList(0, refIndex));
            // Preserve any leading letters of the reference token only when they form a meaningful
            // fragment (e.g. "SECURITY*320925392" -> "SECURITY").  A short prefix such as the "P" in
            // "P3E6A1283E" is part of the reference code, not the merchant name, so it is dropped.
            String leadingLetters = extractLeadingLetters(tokens.get(refIndex));
            if (leadingLetters.length() >= 3) {
                kept.add(leadingLetters);
            }
            String result = String.join(" ", kept).trim();
            return result.isEmpty() ? payee : result;
        }

        // Rule 3: strip a trailing state and the city that precedes it (multi-word city aware).
        if (tokens.size() >= 2 && STATE_ABBREVIATIONS.contains(tokens.get(tokens.size() - 1).toUpperCase())) {
            String state = tokens.remove(tokens.size() - 1).toUpperCase();  // remove the state
            int cityWordsRemoved = removeTrailingCity(tokens, state, cityLookup);
            if (cityWordsRemoved == 0 && tokens.size() >= 2) {
                // No known multi-word city matched; drop a single (possibly truncated) city token.
                tokens.remove(tokens.size() - 1);
            }
            String result = String.join(" ", tokens).trim();
            return result.isEmpty() ? payee : result;
        }

        return String.join(" ", tokens);
    }

    /**
     * Strips web-domain suffixes from each token ("NETFLIX.COM" &rarr; "NETFLIX") and drops standalone
     * TLD tokens ("COM").  A token that is nothing but a suffix is kept unchanged so it cannot vanish.
     *
     * @param tokens the tokens to clean
     * @return a new list of cleaned tokens
     */
    private static List<String> stripDomainSuffixes(List<String> tokens) {
        List<String> cleaned = new ArrayList<>();
        for (String token : tokens) {
            if (STANDALONE_TLDS.contains(token.toUpperCase())) {
                continue;  // drop standalone TLD word (e.g. "COM")
            }
            String stripped = DOMAIN_SUFFIX.matcher(token).replaceAll("");
            cleaned.add(stripped.isEmpty() ? token : stripped);
        }
        return cleaned;
    }

    /**
     * Attempts to remove a trailing multi-word city (two or more words) from the token list using the
     * supplied city lookup, trying the longest candidate first.  At least one token is always left as
     * the merchant name.  Single-word cities are intentionally not looked up here — they are handled
     * by the caller's fallback so that no database call is made for the common single-word case.
     *
     * @param tokens     the tokens (with the state already removed); modified in place on a match
     * @param state      the two-letter state abbreviation
     * @param cityLookup the (city, state) lookup
     * @return the number of city words removed (0 if none matched)
     */
    private static int removeTrailingCity(List<String> tokens, String state, CityStateLookup cityLookup) {
        int maxWords = Math.min(MAX_CITY_WORDS, tokens.size() - 1);  // always leave >= 1 merchant token
        for (int len = maxWords; len >= 2; len--) {
            String candidate = String.join(" ", tokens.subList(tokens.size() - len, tokens.size()));
            if (cityLookup.isCity(candidate, state)) {
                for (int k = 0; k < len; k++) {
                    tokens.remove(tokens.size() - 1);
                }
                return len;
            }
        }
        return 0;
    }

    /**
     * Returns the leading run of ASCII letters in the given token, or an empty string if the token
     * does not start with a letter.  Used to salvage a merchant name fragment from a token that also
     * contains a reference number (e.g. {@code "SECURITY*320925392"} &rarr; {@code "SECURITY"}).
     *
     * @param token the token to inspect
     * @return the leading letters, or an empty string
     */
    private static String extractLeadingLetters(String token) {
        int i = 0;
        while (i < token.length() && Character.isLetter(token.charAt(i))) {
            i++;
        }
        return token.substring(0, i);
    }

    @Override
    public String extractUserDescription(String payee) {
        // Citi doesn't typically have user descriptions in the payee field
        return "";
    }

    @Override
    public List<User> extractUsers(String payee) {
        // Citi transactions don't contain user information
        return new ArrayList<>();
    }

    @Override
    public String extractAccountType(String payee) {
        // For Citi, it's always a credit card
        return "CREDIT_CARD";
    }
}








