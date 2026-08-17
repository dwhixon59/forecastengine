package com.hixon.financialApp.model.merchant;

import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.utility.Utility;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.hixon.financialApp.utility.Utility.getDbConnection;

/**
 * Utility class for merchant-related operations that don't belong in the core Merchant entity class.
 * Contains helper methods for merchant lookup, matching, and analysis.
 */
public class MerchantUtilities {

    /**
     * Matches a masked (partially-hidden) account number embedded in a transfer payee/memo, such as
     * Wells Fargo's {@code XXXXXX8249} or a {@code ****8249} style mask. Two or more masking
     * characters (X, x, or *) immediately followed by at least four digits. The trailing four digits
     * are captured as the account's last four. Kept institution-agnostic on purpose so it works for
     * every bank's transfer format (see {@link #getTransferMerchantByLastFour(String, Register)}).
     */
    private static final Pattern MASKED_ACCOUNT_PATTERN = Pattern.compile("[xX*]{2,}(\\d{4,})");

    /**
     * Get a list of possible merchants for a given transaction payee.
     * This method attempts to identify merchants without user interaction.
     *
     * <p>The matching process:
     * <ol>
     *   <li>First tries exact match in merchant_payee table</li>
     *   <li>Falls back to fuzzy matching on merchant names:
     *     <ul>
     *       <li>Checks if merchant name is contained in payee (e.g., "PURCHASE AT WALMART" matches "Walmart")</li>
     *       <li>Checks if payee is contained in merchant name (e.g., "TARGET" matches "Target Corporation")</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * @param payee The transaction payee string
     * @return A list of possible merchants:
     *         <ul>
     *           <li>Empty list if payee is null/empty or no matches found</li>
     *           <li>Single item if exact match found</li>
     *           <li>Multiple items if fuzzy matching finds several possibilities</li>
     *         </ul>
     * @throws RegisterException if a database error occurs
     */
    public static List<Merchant> getPossibleMerchantsByPayee(String payee) throws RegisterException {
        List<Merchant> possibleMerchants = new ArrayList<>();

        if (payee == null || payee.trim().isEmpty()) {
            return possibleMerchants;
        }

        // First try exact match
        Merchant exactMatch = Merchant.getByPayee(payee);
        if (exactMatch != null) {
            possibleMerchants.add(exactMatch);
            return possibleMerchants;
        }

        // If no exact match, try fuzzy matching on merchant names within the payee
        // This handles cases like "PURCHASE AT WALMART" -> "Walmart"
        String escapedPayee = Utility.escapeSqlString(payee.toLowerCase());
        String query = Merchant.getSelectQuery() + " where LOWER(m.name) LIKE '%" + escapedPayee + "%' " +
                "OR '" + escapedPayee + "' LIKE CONCAT('%', LOWER(m.name), '%')";

        try (Statement statement = getDbConnection().createStatement()) {
            ResultSet rs = statement.executeQuery(query);
            while (rs.next()) {
                possibleMerchants.add(new Merchant(rs));
            }
            return possibleMerchants;
        } catch (SQLException e) {
            RegisterException re = new RegisterException("Database error occurred trying to get possible Merchants for the " +
                    "payee " + payee + "\nSQL statement was:  " + query, e);
            throw re;
        }
    }

    /**
     * Extracts the last four digits of a masked account number embedded in a transfer payee.
     *
     * <p>Bank transfer payees/memos typically identify the counterparty account with a masked
     * number such as {@code XXXXXX8249} or {@code ****8249}. This returns {@code "8249"} for either.
     * Returns {@code null} when the payee contains no masked account token (e.g. an ordinary
     * purchase), which is exactly how we distinguish "this is a transfer" without any
     * institution-specific flag.
     *
     * @param payee the raw payee/memo string for the transaction
     * @return the last four digits of the masked account, or {@code null} if none is present
     */
    public static String extractMaskedAccountLastFour(String payee) {
        if (payee == null || payee.isEmpty()) {
            return null;
        }
        Matcher matcher = MASKED_ACCOUNT_PATTERN.matcher(payee);
        if (matcher.find()) {
            String digits = matcher.group(1);
            return digits.substring(digits.length() - 4);
        }
        return null;
    }

    /**
     * Resolves the merchant for a transfer transaction from the masked counterparty account number
     * in its raw payee, with no user interaction.
     *
     * <p>Because transfer merchants are keyed by register name everywhere in the application
     * (e.g. {@code Register.getByName(merchant.getName())} is used to resolve a transfer's other
     * side), a transfer's merchant is simply the counterparty register's name. This method:
     * <ol>
     *   <li>Extracts the last four digits from the payee's masked account token (returns
     *       {@code null} — "not a transfer we can resolve this way" — if there is none).</li>
     *   <li>Looks up the register whose account number ends in those four digits.</li>
     *   <li>Ignores a match to the account currently being imported (a transfer's masked number is
     *       the <em>counterparty</em>, never the source register).</li>
     *   <li>Returns the {@link Merchant} named after that register, creating and saving one if it
     *       does not yet exist — so subsequent imports resolve the same transfer deterministically.</li>
     * </ol>
     *
     * <p>This is institution-agnostic: any bank whose transfer payee carries a masked account number
     * benefits without institution-specific code.
     *
     * @param payee          the raw transaction payee/memo (should still contain the masked number,
     *                       i.e. before normalization to a friendly "Transfer to X from Y" string)
     * @param sourceRegister the register currently being imported (excluded from matching); may be null
     * @return the resolved (or newly created) transfer merchant, or {@code null} if the payee has no
     *         masked account number or no register matches those last four digits
     * @throws RegisterException if a database error occurs during lookup
     * @throws EntityException   if creating/saving a new merchant fails
     * @throws SQLException      if a database error occurs resolving the register by name
     */
    public static Merchant getTransferMerchantByLastFour(String payee, Register sourceRegister)
            throws RegisterException, EntityException, SQLException {

        String lastFour = extractMaskedAccountLastFour(payee);
        if (lastFour == null) {
            return null;
        }

        Register transferRegister = Register.getByLastFourDigits(lastFour);
        if (transferRegister == null) {
            return null;
        }

        // A transfer's masked number identifies the counterparty, not the account being imported.
        if (sourceRegister != null && transferRegister.getId().equals(sourceRegister.getId())) {
            return null;
        }

        // Transfer merchants are keyed by register name; create one if it does not exist yet so the
        // import stays prompt-free and future imports of this transfer resolve the same way.
        Merchant merchant = Merchant.getByName(transferRegister.getName());
        if (merchant == null) {
            merchant = new Merchant(transferRegister.getName());
            merchant.save();
        }
        return merchant;
    }
}

