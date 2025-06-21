package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.merchant.Merchant;
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

import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

/**
 * The WellsFargoBankController class is responsible for handling and processing
 * Wells Fargo bank transaction data imported from CSV files. This class extends
 * FinancialInstitutionController and provides specific implementations for
 * managing transactions, parsing details, and handling merchant data from
 * Wells Fargo CSV transaction download files.
 *
 */
/*
 * This class analyzes transactions from a Wells Fargo CSV download file:
 */
public class WellsFargoBankController extends FinancialInstitutionController {

    /*
     * Constants for the Wells Fargo download file transaction classifier:
     */
    public static final String CHECKING = "Checking";
    public static final String SAVINGS = "Savings";
    public static final String[] ACCOUNT_TYPES = {CHECKING, SAVINGS};


    /*
     * Fields in the Wells Fargo download file transaction classifier:
     */
    //TODO:  Had to remove UT because it clashed with the abbreviation for "utilities".
    private static final String states = "|AL|AK|AS|AZ|AR|CA|CO|CT|DE|DC|FM|FL|GA|GU|HI|ID|IL|IN|IA|KS|KY|LA|ME|MH|MD|MA|MI|" +
            "MN|MS|MO|MT|NE|NV|NH|NJ|NM|NY|NC|ND|MP|OH|OK|OR|PW|PA|PR|RI|SC|SD|TN|TX|VT|VA|WA|WV|WI|WY|";
    private final SimpleDateFormat sdf = new SimpleDateFormat("M/dd/yyyy", Locale.ENGLISH);
    private String[] payeeTokens;


    /*
     * Getters and setters for the Wells Fargo download file transaction classifier:
     */


    /*
     * Constructors for the Wells Fargo download file transaction classifier:
     */
    public WellsFargoBankController(Register register, Budget budget, Forecast forecast, ViewInt view,
                                    NotificationServiceInt notificationService) {

        super(register, budget, forecast, view, notificationService);
    }


    /*
     * Helper methods:
     */
    @Override
    public String getRegisterImportRecordBaseName(CSVRecord record) {
        return record.get(Transaction.Headers.TRANSACTION_DATE) + "\t" + record.get(Transaction.Headers.AMOUNT) + "\t" +
                record.get(Transaction.Headers.CLEARED) + "\t" + record.get(Transaction.Headers.CHECK_NUMBER) + "\t" +
                record.get(Transaction.Headers.PAYEE);
    }

    /**
     * Extracts the account type from the given payee string.
     * The method checks for specific keywords in the payee string
     * to determine the account type (e.g., "Checking", "Savings").
     *
     * @param payee The input string containing the payee information
     * @return A string representing the extracted account type
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
     * Main methods for the Wells Fargo download file transaction classifier:
     */
    // Load up a Transaction from a Wells Fargo CSV transaction download file:
    @Override
    public Transaction createFromCSVRecord(CSVRecord record, String importRecordId) throws Exception {

        Transaction transaction = loadFromCsvRecord(record, importRecordId);
        TransactionHistory.getInstance().add(transaction);
        return transaction;
    }

    // Load a transaction from a posted transaction CSV record:
    public Transaction loadFromCsvRecord(CSVRecord record, String importRecordId) throws Exception {

        // Compute the fields of the transaction from the tokens in the record:
        Calendar postDate = Calendar.getInstance();
        postDate.setTime(sdf.parse(record.get(Transaction.Headers.TRANSACTION_DATE)));
        String payee = record.get(Transaction.Headers.PAYEE);
        double amount = Double.parseDouble(record.get(Transaction.Headers.AMOUNT));
        boolean cleared = record.get(Transaction.Headers.CLEARED).contentEquals("*");
        String checkNumberString = record.get(Transaction.Headers.CHECK_NUMBER);
        int checkNumber = 0;
        if (checkNumberString != null && !checkNumberString.isEmpty()) {
            checkNumber = Integer.parseInt(record.get(Transaction.Headers.CHECK_NUMBER));
        }

        // Create the transaction:
        Transaction transaction = new Transaction(register, postDate, payee, amount, cleared, checkNumber, importRecordId);

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

    @Override
    public Transaction getMatchingProvisionalTransaction(CSVRecord record, Merchant merchant) throws SQLException,
            EntityException {

        return TransactionUtilities.getFirstProvisionalTransaction(merchant.getId(),
                Double.parseDouble(record.get(Transaction.Headers.AMOUNT)));
    }

    // Load a transaction from a CSV provisional transaction record:
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

        // Figure out which merchant the transaction is associated with:
        String merchantPayee = parseMerchantPayee(postDate, amount, tokens[1 + iOffset]);

        // Create a transaction based on the provisional record:
        return new Transaction(register, tokens[iOffset], tokens[1 + iOffset], amount, merchantPayee);
    }

    // Parse out the merchant name from a Wells Fargo CSV transaction download file:
    public String parseMerchantPayee(Calendar date, double amount, String payee) throws Exception {

        // Construct the merchant payee string from portions of the bank payee string:
        String merchantPayee;
        String firstFewWords;
        payeeTokens = payee.split(" ");
        if (payeeTokens[0].equalsIgnoreCase("CHECK")) {
            firstFewWords = payeeTokens[0];
        } else {
            if (payeeTokens.length >= 3) {
                firstFewWords = payeeTokens[0] + " " + payeeTokens[1] + " " + payeeTokens[2];
            } else {
                firstFewWords = payeeTokens[0] + " " + payeeTokens[1];
            }
        }

        int i;
        int start = 0;
        switch (firstFewWords) {

            // If the transaction is a purchase:
            case "PURCHASE AUTHORIZED ON":
            case "PURCHASE RETURN AUTHORIZED":
            case "PURCHASE WITH CASH":
            case "RECURRING PAYMENT AUTHORIZED":

                // Beginning with the token after the date of the transaction, skip over any tokens starting with a digit:
                start = switch (payeeTokens[2]) {
                    case "ON" -> 4;
                    case "AUTHORIZED" -> 5;
                    case "CASH" -> 9;
                    default -> start;
                };

                // Derive a payee from the remaining tokens:
                merchantPayee = makePayeeFromTokens(start);
                break;

            // If this is a transfer:
            case "ONLINE TRANSFER TO":
            case "ONLINE TRANSFER FROM":
            case "RECURRING TRANSFER TO":
            case "RECURRING TRANSFER FROM":
            case "ATM TRANSFER AUTHORIZED":
            case "Transfer in Branch/Store":
            case "SAVE AS YOU":

                // Find the register by the last four digits of the account number, or if the account number is not
                // present, then have the user tell us which register it came from. The reason we only use the last four
                // digits is that only the last four digits of the account number are provided by Wells Fargo in the
                // payee string.
                //noinspection StatementWithEmptyBody
                for (i = 0; i < payeeTokens.length && !payeeTokens[i].matches("^X{4,}[0-9]{4}"); i++) ;
                Register transferRegister;
                String accountNumber;
                if (i == payeeTokens.length) {

                    // The account number isn't in the payee string, so ask the user which register it came from:
                    RegisterController registerController = new RegisterController(register, this,
                            budget, forecast, view, notificationService);
                    transferRegister = registerController.resolveUnmatchedAccount(date, amount, payee);
                    accountNumber = transferRegister.getAccountNumber();
                }
                else {
                    // The account number is in the payee string, so use it to find the register:
                    accountNumber = payeeTokens[i];
                    String lastFourDigits = accountNumber.substring(accountNumber.length() - 4);
                    transferRegister = Register.getByLastFourDigits(lastFourDigits);
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
                            register.getName();
                } else {
                    merchantPayee = "Transfer " + toFrom1 + " " + accountNumber + " " + toFrom2 + " " +
                            register.getName();
                }
                break;

            // If this is an interest payment:
            case "INTEREST PAYMENT":

                // then the merchant is Interest Payment:
                merchantPayee = "Interest Payment";
                break;

            // Overdraft fees:
            case "OVERDRAFT FEE FOR":

                // then Wells Fargo is the merchant:
                merchantPayee = "Overdraft Fee";
                break;

            case "ATM CASH DEPOSIT":
                merchantPayee = "ATM Cash Deposit";
                break;

            case "CHECK":
                merchantPayee = "Check";
                break;

            default: // One-time online payments:

                // Skip over certain words at the beginning if they are present:
                if (payeeTokens[0].equalsIgnoreCase("BILL") && payeeTokens[1].equalsIgnoreCase("PAY")) {
                    start = 2;
                } else if (payeeTokens[0].equalsIgnoreCase("PURCHASE") ||
                        payeeTokens[0].equalsIgnoreCase("REVERSAL")) {
                    start = 1;
                }

                // Derive a payee from the remaining tokens:
                merchantPayee = makePayeeFromTokens(start);

                break;
        }
        return merchantPayee;
    }

    private static final Set<String> STOPWORDS = Set.of(
            "RECURRING", "TRANSFER", "TO", "FROM", "REF", "#",
            "EVERYDAY", "CHECKING", "SAVINGS", "WAY2SAVE",
            "ACCOUNT", "JOINT", "BANKING", "BA", "ONLINE"
    );

    private static final Pattern maskedAccountPattern = Pattern.compile("X{4,}\\d{2,}");
    private static final Pattern refPrefixPattern = Pattern.compile("^#?[A-Z0-9]{10,}(\\s+ON\\s+\\d{2}/\\d{2}/\\d{2,4})?", Pattern.CASE_INSENSITIVE);
    private static final Pattern dateOnlyPattern = Pattern.compile("^ON\\s+\\d{2}/\\d{2}/\\d{2,4}$", Pattern.CASE_INSENSITIVE);

    /**
     * Extracts a user description from the raw text of a Wells Fargo CSV transaction description.
     * @param rawText The transaction description
     * @return The extracted user description, or null if no valid description could be found
     */
    @Override
    public String extractUserDescription(String rawText) {
        if (rawText == null || rawText.isBlank()) return null;

        rawText = rawText.trim();

        // Step 1: Remove leading #REFCODE and optional date
        rawText = refPrefixPattern.matcher(rawText).replaceFirst("").trim();

        // Step 2: Look for the last occurrence of "REF #" and take everything after it
        int refIndex = rawText.toUpperCase().lastIndexOf("REF #");
        if (refIndex == -1) return null;

        String afterRef = rawText.substring(refIndex + 5).trim();
        String[] words = afterRef.split("\\s+");

        List<String> cleaned = new ArrayList<>();
        for (String word : words) {
            String upper = word.toUpperCase();

            if (STOPWORDS.contains(upper) || maskedAccountPattern.matcher(upper).matches()) {
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
        if (dateOnlyPattern.matcher(result.toUpperCase()).matches()) {
            return null;
        }

        return result;
    }

    public String makePayeeFromTokens(int start) throws SQLException, EntityException {
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

    // Remove any serial number, etc. from a token:
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

    // Remove city, state and the word "RECURRING" from a token list:
    private void cleanPayeeTokenList(int start) throws SQLException, EntityException {
        for (int i = start; i < payeeTokens.length; i++) {
            // Remove city followed by state from the merchant payee:
            if (payeeTokens[i].length() == 2 && states.indexOf(payeeTokens[i]) > 0) {
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

    /**
     * Extracts a user based on specific patterns in the given payee string.
     * The method parses the input string to identify tokens indicating potential
     * user information, such as "TO" or "FROM", followed by a last name and a first initial.
     * It attempts to locate a matching User in the database using the provided information.
     *
     * @param payee The input string containing the payee information to extract user data from.
     * @return A User object that matches the extracted information or null if no match is found
     * or the input does not contain valid user data.
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
}
