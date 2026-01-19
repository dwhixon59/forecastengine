package com.hixon.financialApp.model.financialinstitution;

import com.hixon.financialApp.controller.RegisterController;
import com.hixon.financialApp.controller.SessionController;
import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.model.register.TransactionUtilities;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.utility.CityStateChecker;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.base.TransactionHistory;
import com.hixon.financialApp.view.base.ViewInt;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

import static com.hixon.financialApp.utility.ForecastTransactionMatcher.findMatchingForecastTransaction;

/**
 * The WellsFargoBank class handles Wells Fargo-specific transaction processing logic.
 * This class extends FinancialInstitution and provides specific implementations for
 * managing transactions, parsing details, and handling merchant data from
 * Wells Fargo CSV transaction download files.
 *
 * <p>Wells Fargo transactions have specific formats for:
 * <ul>
 *   <li>Purchase transactions with authorization dates</li>
 *   <li>Transfer transactions between accounts</li>
 *   <li>Recurring payments</li>
 *   <li>Bill payments</li>
 *   <li>ATM transactions</li>
 *   <li>Check transactions</li>
 * </ul>
 *
 * <p>This class handles provisional transaction matching to reconcile pending transactions
 * with posted transactions, including tip detection for restaurant purchases.
 */
public class WellsFargoBank extends FinancialInstitution {

    private static final Logger logger = LogManager.getLogger(WellsFargoBank.class);

    /*
     * Constants for Wells Fargo:
     */

    /**
     * Constant for checking account type.
     */
    public static final String CHECKING = "Checking";

    /**
     * Constant for savings account type.
     */
    public static final String SAVINGS = "Savings";

    /**
     * Array of supported account types.
     */
    public static final String[] ACCOUNT_TYPES = {CHECKING, SAVINGS};

    /**
     * A pipe-delimited string containing all U.S. state abbreviations used for parsing
     * city/state information from transaction descriptions. Used to identify and remove
     * location data from merchant names.
     *
     * <p>Note: UT (Utah) was removed because it clashed with the abbreviation for "utilities".
     */
    private static final String STATES = "|AL|AK|AS|AZ|AR|CA|CO|CT|DE|DC|FM|FL|GA|GU|HI|ID|IL|IN|IA|KS|KY|LA|ME|MH|MD|MA|MI|" +
            "MN|MS|MO|MT|NE|NV|NH|NJ|NM|NY|NC|ND|MP|OH|OK|OR|PW|PA|PR|RI|SC|SD|TN|TX|VT|VA|WA|WV|WI|WY|";

    /**
     * Set of common stopwords to filter out when extracting user descriptions from transaction text.
     * These words are typically bank-related terminology that don't contribute to meaningful
     * user-provided descriptions.
     */
    private static final Set<String> STOPWORDS = Set.of(
            "RECURRING", "TRANSFER", "TO", "FROM", "REF", "#",
            "EVERYDAY", "CHECKING", "SAVINGS", "WAY2SAVE",
            "ACCOUNT", "JOINT", "BANKING", "BA", "ONLINE"
    );

    /**
     * Pattern to match masked account numbers in Wells Fargo transaction descriptions.
     * Format: Multiple X's followed by 2 or more digits (e.g., "XXXX1234").
     */
    private static final Pattern MASKED_ACCOUNT_PATTERN = Pattern.compile("X{4,}\\d{2,}");

    /**
     * Pattern to match and remove reference codes at the start of transaction descriptions.
     * Matches alphanumeric codes (10+ characters) optionally followed by "ON" and a date.
     * Example: "#ABC1234567890 ON 11/13/2024"
     */
    private static final Pattern REF_PREFIX_PATTERN = Pattern.compile("^#?[A-Z0-9]{10,}(\\s+ON\\s+\\d{2}/\\d{2}/\\d{2,4})?", Pattern.CASE_INSENSITIVE);

    /**
     * Pattern to match standalone date expressions like "ON 11/13/2024".
     * Used to reject descriptions that contain only a date after cleaning.
     */
    private static final Pattern DATE_ONLY_PATTERN = Pattern.compile("^ON\\s+\\d{2}/\\d{2}/\\d{2,4}$", Pattern.CASE_INSENSITIVE);

    /**
     * Enum representing the CSV column headers used in Wells Fargo transaction download files.
     * These headers correspond to the columns in the CSV format provided by Wells Fargo.
     */
    public enum Headers {
        /** The transaction date column */
        TRANSACTION_DATE,
        /** The transaction amount column */
        AMOUNT,
        /** The cleared status column (typically "*" for cleared transactions) */
        CLEARED,
        /** The check number column (if applicable) */
        CHECK_NUMBER,
        /** The payee/description column */
        PAYEE
    }

    /*
     * Fields:
     */

    /**
     * Date formatter for parsing transaction dates from Wells Fargo CSV files.
     * Wells Fargo uses the format "M/dd/yyyy" (e.g., "1/15/2025" or "11/3/2024").
     */
    private final SimpleDateFormat sdf = new SimpleDateFormat("M/dd/yyyy", Locale.ENGLISH);

    /**
     * Array of tokens extracted from the payee string during transaction parsing.
     * Used to analyze and extract merchant names, dates, and other transaction details.
     */
    private String[] payeeTokens;

    /*
     * Constructors:
     */

    /**
     * Constructs a new WellsFargoBank instance for processing Wells Fargo transactions.
     *
     * @param sessionController The session controller containing register, budget, forecast, view, and notificationService
     */
    public WellsFargoBank(SessionController sessionController) {
        super(sessionController);
    }

    /*
     * Interface implementation methods:
     */

    /**
     * Returns the Wells Fargo CSV headers enum class.
     * This is used by CSV parsers to map Wells Fargo CSV column names to enum values.
     *
     * @return the Headers enum class for Wells Fargo CSV files
     */
    @Override
    public Class<? extends Enum<?>> getCsvHeadersClass() {
        return Headers.class;
    }

    /**
     * Returns the CSV format configuration for Wells Fargo transaction files.
     * Wells Fargo CSV files use RFC4180 format (standard CSV) with:
     * - Comma delimiter
     * - Headers in first row that match the Headers enum
     * - Values trimmed of whitespace
     *
     * @return the CSVFormat configuration for parsing Wells Fargo CSV files
     */
    @Override
    public org.apache.commons.csv.CSVFormat getCsvFormat() {
        return org.apache.commons.csv.CSVFormat.RFC4180.builder()
                .setHeader(getCsvHeadersClass())
                .setTrim(true)
                .get();
    }

    /**
     * Generates a base name for a register import record using fields from the Wells Fargo CSV record.
     * The base name is used to uniquely identify and track imported transactions, helping to prevent
     * duplicate imports.
     *
     * @param record The CSV record containing Wells Fargo transaction data
     * @return A tab-separated string containing: transaction date, amount, cleared status,
     *         check number, and payee information
     */
    @Override
    public String getRegisterImportRecordBaseName(CSVRecord record) {
        String clearedStatus = "*".equals(record.get(Headers.CLEARED)) ? "true" : "false";
        return record.get(Headers.TRANSACTION_DATE) + "\t" +
               record.get(Headers.AMOUNT) + "\t" +
               clearedStatus + "\t" +
               record.get(Headers.CHECK_NUMBER) + "\t" +
               record.get(Headers.PAYEE);
    }

    /**
     * Creates a Transaction object from a Wells Fargo CSV record and adds it to the transaction history.
     * This is the main entry point for importing posted transactions from Wells Fargo CSV files.
     *
     * @param record The CSV record containing the Wells Fargo transaction data
     * @param importRecordId A unique identifier for this import operation, used to track which
     *                       transactions were imported together
     * @return The newly created Transaction object
     * @throws Exception If an error occurs during transaction creation or parsing
     */
    @Override
    public Transaction createFromCSVRecord(CSVRecord record, String importRecordId) throws Exception {
        Transaction transaction = loadFromCsvRecord(record, importRecordId);
        TransactionHistory.getInstance().add(transaction);
        return transaction;
    }

    /**
     * Parses the merchant name from a Wells Fargo CSV transaction description.
     * Wells Fargo transaction descriptions contain various metadata including authorization dates,
     * location information, reference numbers, and the actual merchant name. This method extracts
     * just the merchant name by analyzing the transaction type and parsing patterns.
     *
     * <p>Supported transaction types:
     * <ul>
     *   <li>Purchase transactions (PURCHASE AUTHORIZED ON, PURCHASE WITH CASH, etc.)</li>
     *   <li>Recurring payments</li>
     *   <li>Transfers between accounts (ONLINE TRANSFER TO/FROM, ATM TRANSFER, etc.)</li>
     *   <li>Interest payments</li>
     *   <li>Overdraft fees</li>
     *   <li>ATM cash deposits</li>
     *   <li>Checks</li>
     *   <li>Bill payments</li>
     *   <li>One-time online payments</li>
     * </ul>
     *
     * <p>For transfer transactions, this method attempts to identify the destination/source register
     * by matching the last 4 digits of the account number, or prompts the user if needed.
     *
     * @param date The transaction date (used for identifying transfer register if needed)
     * @param amount The transaction amount (used for identifying transfer register if needed)
     * @param payee The raw payee string from the Wells Fargo CSV file
     * @return A cleaned merchant name suitable for display and matching
     * @throws Exception If an error occurs during parsing or register lookup
     */
    @Override
    public String parseMerchantPayee(Calendar date, double amount, String payee) throws Exception {
        logger.debug("");
        logger.debug("=== parseMerchantPayee Debug ===");
        logger.debug("Input:");
        logger.debug("  Date:   {}", date.getTime());
        logger.debug("  Amount: {}", amount);
        logger.debug("  Payee:  '{}'", payee);

        // Construct the merchant payee string from portions of the bank payee string:
        String merchantPayee;
        String firstFewWords;
        payeeTokens = payee.split(" ");
        logger.debug("  Tokenized into {} tokens", payeeTokens.length);

        if (payeeTokens[0].equalsIgnoreCase("CHECK")) {
            firstFewWords = payeeTokens[0];
        } else {
            if (payeeTokens.length >= 3) {
                firstFewWords = payeeTokens[0] + " " + payeeTokens[1] + " " + payeeTokens[2];
            } else if (payeeTokens.length == 2) {
                firstFewWords = payeeTokens[0] + " " + payeeTokens[1];
            } else {
                // Only 1 token
                firstFewWords = payeeTokens[0];
            }
        }
        logger.debug("  First few words: '{}'", firstFewWords);

        int i;
        int start = 0;
        boolean recurring = false;
        switch (firstFewWords) {

            // If the transaction is a purchase:
            case "PURCHASE AUTHORIZED ON":
            case "PURCHASE RETURN AUTHORIZED":
            case "PURCHASE WITH CASH":
            case "RECURRING PAYMENT AUTHORIZED":
                logger.debug("Processing as PURCHASE/RECURRING PAYMENT ('{}')", firstFewWords);

                // Beginning with the token after the date of the transaction, skip over any tokens starting with a digit:
                start = switch (payeeTokens[2]) {
                    case "ON" -> 4;
                    case "AUTHORIZED" -> 5;
                    case "CASH" -> 9;
                    default -> start;
                };
                logger.debug("  Start token index: {}", start);

                // Derive a payee from the remaining tokens:
                merchantPayee = makePayeeFromTokens(start);
                logger.debug("  Derived merchantPayee: '{}'", merchantPayee);
                break;

                // If this is a transfer:
            case "RECURRING TRANSFER TO":
            case "RECURRING TRANSFER FROM":

                recurring = true;

            case "ONLINE TRANSFER TO":
            case "ONLINE TRANSFER FROM":
            case "ATM TRANSFER AUTHORIZED":
            case "Transfer in Branch/Store":
            case "SAVE AS YOU":
                logger.debug("Processing as TRANSFER ('{}')", firstFewWords);

                // Find the register by the last four digits of the account number, or if the account number is not
                // present, then have the user tell us which register it came from. The reason we only use the last four
                // digits is that only the last four digits of the account number are provided by Wells Fargo in the
                // payee string.
                //noinspection StatementWithEmptyBody
                for (i = 0; i < payeeTokens.length && !payeeTokens[i].matches("^X{4,}[0-9]{4}"); i++) ;
                Register transferRegister;
                String accountNumber = "";
                if (i == payeeTokens.length) {
                    logger.debug("  Account number not found in payee string - will ask user");

                    // The account number isn't in the payee string, so ask the user which register it came from:
                    SessionController tempSessionController = new SessionController(getRegister(), budget, forecast, view, notificationService);
                    tempSessionController.setFinancialInstitution(this);
                    RegisterController registerController = new RegisterController(tempSessionController);
                    transferRegister = registerController.resolveUnmatchedAccount(date, amount, payee, recurring);

                    // If we were able to determine the register this transaction was transferred to/from:
                    if (transferRegister != null) {

                        // Then get the account number from the register:
                        accountNumber = transferRegister.getAccountNumber();
                        logger.debug("  User selected register: {} (account: {})", transferRegister.getName(), accountNumber);
                    }
                }
                else {
                    // The account number is in the payee string, so use it to find the register:
                    accountNumber = payeeTokens[i];
                    String lastFourDigits = accountNumber.substring(accountNumber.length() - 4);
                    logger.debug("  Found account number in payee: {} (last 4: {})", accountNumber, lastFourDigits);
                    transferRegister = Register.getByLastFourDigits(lastFourDigits);
                    if (transferRegister != null) {
                        logger.debug("  Matched to register: {}", transferRegister.getName());
                    } else {
                        logger.debug("  No register found for last 4 digits: {}", lastFourDigits);
                    }
                }

                // Construct a string that describes the transfer for the user
                String toFrom1, toFrom2;
                if (payeeTokens[0].equalsIgnoreCase("ATM")) {
                    toFrom1 = (payeeTokens[5].equalsIgnoreCase("TO")) ? "to" : "from";
                    toFrom2 = (payeeTokens[5].equalsIgnoreCase("TO")) ? "from" : "to";
                } else {
                    toFrom1 = (payeeTokens[2].equalsIgnoreCase("TO")) ? "to" : "from";
                    toFrom2 = (payeeTokens[2].equalsIgnoreCase("TO")) ? "from" : "to";
                }
                if (transferRegister != null) {
                    merchantPayee = "Transfer " + toFrom1 + " " + transferRegister.getName() + " " + toFrom2 + " " +
                            getRegister().getName();
                } else {
                    merchantPayee = "Transfer " + toFrom1 + " " + accountNumber + " " + toFrom2 + " " +
                            getRegister().getName();
                }
                logger.debug("  Constructed merchantPayee: '{}'", merchantPayee);
                break;

            // If this is an interest payment:
            case "INTEREST PAYMENT":
                logger.debug("Processing as INTEREST PAYMENT");

                // then the merchant is Interest Payment:
                merchantPayee = "Interest Payment";
                break;

            // Overdraft fees:
            case "OVERDRAFT FEE FOR":
                logger.debug("Processing as OVERDRAFT FEE");

                // then Wells Fargo is the merchant:
                merchantPayee = "Overdraft Fee";
                break;

            case "ATM CASH DEPOSIT":
                logger.debug("Processing as ATM CASH DEPOSIT");
                merchantPayee = "ATM Cash Deposit";
                break;

            case "CHECK":
                logger.debug("Processing as CHECK");
                merchantPayee = "Check";
                break;

            default: // One-time online payments:
                logger.debug("Processing as DEFAULT (one-time online payment or other)");

                // Skip over certain words at the beginning if they are present:
                if (payeeTokens[0].equalsIgnoreCase("BILL") && payeeTokens[1].equalsIgnoreCase("PAY")) {
                    start = 2;
                } else if (payeeTokens[0].equalsIgnoreCase("PURCHASE") ||
                        payeeTokens[0].equalsIgnoreCase("REVERSAL")) {
                    start = 1;
                }
                logger.debug("  Start token index: {}", start);

                // Derive a payee from the remaining tokens:
                merchantPayee = makePayeeFromTokens(start);
                logger.debug("  Derived merchantPayee: '{}'", merchantPayee);

                break;
        }

        logger.debug("Final parsed merchantPayee: '{}'", merchantPayee);
        logger.debug("=== End parseMerchantPayee Debug ===");
        logger.debug("");

        return merchantPayee;
    }

    /**
     * Generates a matching provisional transaction for the given cleared transaction.
     * Uses fuzzy payee matching within a ±5 day window to find provisional transactions
     * that likely correspond to the cleared transaction.
     *
     * @param clearedTransaction The cleared transaction to match
     * @return The matching provisional Transaction, or null if no match is found
     * @throws SQLException If a database error occurs
     * @throws EntityException If an entity-related error occurs
     * @throws Exception If an error occurs
     */
    @Override
    public Transaction getMatchingProvisionalTransaction(Transaction clearedTransaction)
            throws SQLException, EntityException, Exception {

        // Get the merchant payee from the already-parsed transaction
        String merchantPayee = clearedTransaction.getMerchantPayee();

        // Use fuzzy matching based on payee, date, and amount only (no merchant ID)
        return TransactionUtilities.findMatchingProvisionalTransaction(
                getRegister().getId(),
                clearedTransaction.getAmount(),
                clearedTransaction.getDate(),
                merchantPayee
        );
    }

    /**
     * Extracts a user-provided description from the raw Wells Fargo CSV transaction description.
     * Wells Fargo transactions often include user-provided notes or memo fields embedded in the
     * payee string, typically after "REF #" markers. This method attempts to extract and clean
     * those user descriptions by:
     *
     * <ol>
     *   <li>Removing leading reference codes and dates</li>
     *   <li>Finding text after the last "REF #" marker</li>
     *   <li>Filtering out bank terminology, account numbers, and duplicate reference codes</li>
     *   <li>Rejecting results that are only dates</li>
     * </ol>
     *
     * @param rawText The raw transaction description from Wells Fargo CSV
     * @return The extracted user description, or null if no valid description could be found
     */
    @Override
    public String extractUserDescription(String rawText) {
        if (rawText == null || rawText.isBlank()) return null;

        rawText = rawText.trim();

        // Step 1: Remove leading #REFCODE and optional date
        rawText = REF_PREFIX_PATTERN.matcher(rawText).replaceFirst("").trim();

        // Step 2: Look for the last occurrence of "REF #" and take everything after it
        int refIndex = rawText.toUpperCase().lastIndexOf("REF #");
        if (refIndex == -1) return null;

        String afterRef = rawText.substring(refIndex + 5).trim();
        String[] words = afterRef.split("\\s+");

        List<String> cleaned = new ArrayList<>();
        for (String word : words) {
            String upper = word.toUpperCase();

            if (STOPWORDS.contains(upper) || MASKED_ACCOUNT_PATTERN.matcher(upper).matches()) {
                continue;
            }

            if (word.matches("^[A-Z]{2}\\d[A-Z0-9]{7,}$")) {
                continue; // Skip duplicate reference codes
            }

            cleaned.add(word);
        }

        if (cleaned.isEmpty()) return null;

        String result = String.join(" ", cleaned);

        // Step 3: Reject if the remaining description is just a date
        if (DATE_ONLY_PATTERN.matcher(result.toUpperCase()).matches()) {
            return null;
        }

        return result;
    }

    /**
     * Extracts a user based on specific patterns in the given payee string.
     * The method parses the input string to identify tokens indicating potential
     * user information, such as "TO" or "FROM", followed by a last name and a first initial.
     * It attempts to locate a matching User in the database using the provided information.
     *
     * @param payee The input string containing the payee information to extract user data from
     * @return A list of User objects that match the extracted information, or null if no match is found
     *         or the input does not contain valid user data
     */
    @Override
    public List<User> extractUsers(String payee) {
        if (payee == null || payee.isEmpty()) {
            return null;
        }

        String[] tokens = payee.split("\\s+");
        for (int i = 0; i < tokens.length - 1; i++) {
            if (tokens[i].equalsIgnoreCase("TO") || tokens[i].equalsIgnoreCase("FROM")) {
                if (i + 2 < tokens.length) {
                    String lastName = tokens[i + 1];
                    String firstInitial = tokens[i + 2];

                    if (firstInitial.length() == 1) {
                        try {
                            return User.findByLastNameAndFirstInitial(lastName, firstInitial);
                        } catch (Exception e) {
                            return null;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Extracts the account type from the given payee string.
     * The method checks for specific keywords in the payee string
     * to determine the account type (e.g., "Checking", "Savings").
     *
     * @param payee The input string containing the payee information
     * @return A string representing the extracted account type, or null if no account type is found
     */
    @Override
    public String extractAccountType(String payee) {
        if (payee == null || payee.isEmpty()) {
            return null;
        }

        // Check for specific keywords in the payee string to determine the account type
        if (payee.toLowerCase().contains("checking")) {
            return CHECKING;
        } else if (payee.toLowerCase().contains("savings")) {
            return SAVINGS;
        }

        // If no specific keywords are found, return null
        return null;
    }

    /*
     * Helper methods:
     */

    /**
     * Loads a Transaction object from a Wells Fargo CSV record (posted transaction format).
     * This method parses the CSV record fields, extracts the transaction date, amount, payee,
     * and other details, then creates a Transaction object with the parsed data.
     *
     * <p>The method also:
     * <ul>
     *   <li>Extracts the authorization date if present in the payee string</li>
     *   <li>Parses the merchant name from the complex Wells Fargo payee format</li>
     *   <li>Tokenizes the payee string for further analysis</li>
     * </ul>
     *
     * @param record The CSV record from a Wells Fargo transaction download file
     * @param importRecordId A unique identifier for tracking this import batch
     * @return The loaded Transaction object with all fields populated
     * @throws Exception If an error occurs during date parsing, amount parsing, or merchant extraction
     */
    private Transaction loadFromCsvRecord(CSVRecord record, String importRecordId) throws Exception {

        // Compute the fields of the transaction from the tokens in the record:
        Calendar postDate = Calendar.getInstance();
        postDate.setTime(sdf.parse(record.get(Headers.TRANSACTION_DATE)));
        String payee = record.get(Headers.PAYEE);
        double amount = Double.parseDouble(record.get(Headers.AMOUNT));
        // Adjust the cleared flag based on the CSV record:
        boolean cleared = false;
        String clearedField = record.get(Headers.CLEARED);
        if (clearedField != null && clearedField.trim().equals("*")) {
            cleared = true;
        } else if (clearedField != null && clearedField.trim().isEmpty()) {
            cleared = false;
        }
        String checkNumberString = record.get(Headers.CHECK_NUMBER);
        int checkNumber = 0;
        if (checkNumberString != null && !checkNumberString.isEmpty()) {
            checkNumber = Integer.parseInt(record.get(Headers.CHECK_NUMBER));
        }

        // Create the transaction:
        Transaction transaction = new Transaction(getRegister(), postDate, payee, amount, cleared, checkNumber, importRecordId);

        // Tokenize the bank payee (single blank is the separator):
        payeeTokens = transaction.getPayee().split(" ");

        // Locate the authorization date if there is one:
        int i = 2;
        for (; i < payeeTokens.length; i++) {

            // If we found a date, use it as the authorization date:
            if (payeeTokens[i].matches("^(0?[1-9]|1[0-2])/(0?[1-9]|1[0-9]|2[0-9]|3[0-1]).*")) {
                transaction.setAuthorizationDate(Utility.stringDateSlashToCalendarDate(payeeTokens[i]));
                break;
            }
        }

        // Parse out the merchant name:
        transaction.setMerchantPayee(parseMerchantPayee(transaction.getDate(), transaction.getAmount(),
                transaction.getPayee()));

        // Make sure that we update the database:
        transaction.setDirty(true);

        // Return the transaction:
        return transaction;
    }

    /**
     * Loads a provisional transaction from a CSV-formatted text line.
     * Provisional transactions are transactions that have not yet posted to the account
     * but are expected (e.g., pending transactions, scheduled payments).
     *
     * <p>This method handles two CSV formats:
     * <ul>
     *   <li>Short version: Starts with a date</li>
     *   <li>Long version: Starts with descriptive text, followed by date in second column</li>
     * </ul>
     *
     * <p>The method extracts the transaction amount from either the credit or debit column,
     * parses the merchant name, and creates a provisional Transaction object.
     *
     * @param line A tab-separated line containing provisional transaction data
     * @param register The register (bank account) to associate with this transaction
     * @return A new provisional Transaction object
     * @throws Exception If the line format is invalid, required fields are missing, or parsing fails
     */
    @Override
    public Transaction loadProvisionalTransactionFromCSV(String line, Register register) throws Exception {
        String[] tokens;

        // Split the line.  If we don't get at least three tokens, then this isn't a valid line:
        tokens = line.split("\t");
        if (tokens.length < 3) {
            throw new ParseException("Too few tokens in the line.", 0);
        }

        // There are two formats for the CSV list that it could be.  The first one starts with a date (short version).
        // The second one starts with some useless text (long version).  To figure out which format it is, test if we
        // can convert the first column to a date:
        int iOffset;
        Calendar postDate = Calendar.getInstance();
        try {
            postDate.setTime(sdf.parse(tokens[0]));
            iOffset = 0;
        } catch (ParseException e) {
            iOffset = 1;
        }

        // Determine the amount of the credit and debit:
        double amount = 0;
        if (tokens.length >= (3 + iOffset) && !tokens[2 + iOffset].isEmpty()) {
            try {
                amount = Utility.parseDollarAmount(tokens[2 + iOffset]);
            } catch (NumberFormatException ignored) {
            }
        } else {
            if (tokens.length >= (4 + iOffset) && !tokens[3 + iOffset].isEmpty()) {
                try {
                    amount = -Utility.parseDollarAmount(tokens[3 + iOffset]);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (amount == 0) {
            throw new ParseException("Could not determine the amount of the transaction.", 0);
        }

        // Don't parse merchant payee yet - defer until after duplicate checking in ImportController.
        // This avoids prompting the user for transfers without account numbers when the provisional
        // transaction already exists in the database with merchant already identified.
        // The merchant payee will be parsed later for new transactions only.

        // Create a transaction based on the provisional record (merchantPayee will be set to raw payee):
        return new Transaction(register, tokens[iOffset], tokens[1 + iOffset], amount, tokens[1 + iOffset]);
    }

    /**
     * Constructs a merchant payee string from the payee tokens starting at the specified index.
     * This method cleans the token list by removing location data and extraneous information,
     * then concatenates relevant tokens to form a clean merchant name.
     *
     * <p>The method:
     * <ol>
     *   <li>Cleans the payee token list (removes city/state, "RECURRING", etc.)</li>
     *   <li>Skips over purely numeric tokens</li>
     *   <li>Concatenates alphabetic tokens and short numbers (e.g., "PIER 1")</li>
     *   <li>Stops when encountering longer numeric sequences or special patterns</li>
     * </ol>
     *
     * @param start The index in payeeTokens array to start building the merchant name from
     * @return A cleaned merchant name string
     * @throws SQLException If a database error occurs during city/state validation
     * @throws EntityException If an entity-related error occurs
     */
    private String makePayeeFromTokens(int start) throws SQLException, EntityException {
        int i;
        StringBuilder merchantPayee;

        // Overwrite certain extraneous tokens like city and state so they won't be included in the merchant payee:
        cleanPayeeTokenList(start);

        // Skip over any numeric tokens:
        //noinspection StatementWithEmptyBody
        for (i = start; i < payeeTokens.length && payeeTokens[i].matches("^[0-9]*$"); i++) ;
        if (i < payeeTokens.length) {
            start = i;
        }

        // Always start with the first remaining token because it is always part of the merchant identification:
        merchantPayee = new StringBuilder(addCleanToken(payeeTokens[start++]));

        // Concatenate the following tokens until we find one that is not all characters or a short number (as in PIER 1):
        for (i = start;
             i < payeeTokens.length &&
                     payeeTokens[i].matches("^[A-Za-z'/*&,]*$|^[0-9]{1,3}$");
             i++) {
            merchantPayee.append(" ").append(addCleanToken(payeeTokens[i]));
        }
        return merchantPayee.toString();
    }

    /**
     * Removes serial numbers and other extraneous characters from a payee token.
     * Many Wells Fargo payee tokens contain asterisks (*) as separators between the
     * merchant name and serial numbers or reference codes. This method extracts just
     * the merchant name portion.
     *
     * @param payeeToken The token to clean (e.g., "WALMART*12345" becomes "WALMART")
     * @return The cleaned token with serial numbers and extra data removed
     */
    private String addCleanToken(String payeeToken) {
        String[] tokens = payeeToken.split("\\*");
        String token;
        if (tokens.length == 0) {
            token = "";
        } else if (tokens.length == 1) {
            token = tokens[0];
        } else {
            token = (!tokens[0].isEmpty()) ? tokens[0] : tokens[1];
        }
        return token;
    }

    /**
     * Removes extraneous data from the payee token list to clean up merchant names.
     * This method modifies the payeeTokens array in place by replacing unwanted tokens
     * with "###" markers.
     *
     * <p>Removed elements include:
     * <ul>
     *   <li>City and state combinations (e.g., "TAMPA FL" is removed if validated as a real city/state)</li>
     *   <li>The word "RECURRING" which appears in many transaction descriptions</li>
     * </ul>
     *
     * @param start The index in the payeeTokens array to start cleaning from
     * @throws SQLException If a database error occurs during city/state validation
     * @throws EntityException If an entity-related error occurs
     */
    private void cleanPayeeTokenList(int start) throws SQLException, EntityException {
        for (int i = start; i < payeeTokens.length; i++) {
            // Remove city followed by state from the merchant payee:
            if (i > 0 && payeeTokens[i].length() == 2 && STATES.indexOf(payeeTokens[i]) > 0) {
                if (CityStateChecker.exists(payeeTokens[i - 1], payeeTokens[i])) {
                    payeeTokens[i] = "###";
                    payeeTokens[i - 1] = "###";
                }
            }

            // Remove the word "RECURRING" from the merchant payee:
            if (payeeTokens[i].equalsIgnoreCase("RECURRING")) {
                payeeTokens[i] = "###";
            }
        }
    }
}

