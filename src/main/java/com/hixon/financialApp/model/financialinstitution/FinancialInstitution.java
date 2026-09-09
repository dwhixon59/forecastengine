package com.hixon.financialApp.model.financialinstitution;

import com.hixon.financialApp.controller.SessionController;
import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.TransactionSplit;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.parser.TransactionParser;
import com.hixon.financialApp.model.qfx.QfxParser;
import com.hixon.financialApp.model.qfx.QfxStatement;
import com.hixon.financialApp.model.qfx.QfxTransaction;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.base.ViewInt;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import static com.hixon.financialApp.utility.Utility.formatDollarAmount;

/**
 * Abstract base class for financial institution controllers that handle transaction imports,
 * provisional transaction reconciliation, and institution-specific parsing logic.
 *
 * This class provides common functionality shared across different financial institution
 * implementations, including register management, budget tracking, and generic provisional
 * transaction reconciliation with tip detection.
 *
 * Specific financial institutions should extend this class and implement the abstract methods
 * defined in FinancialInstitutionInt to provide institution-specific behavior.
 */
public abstract class FinancialInstitution implements FinancialInstitutionInt {

    /**
     * The session controller.
     */
    protected SessionController sessionController;

    /**
     * The budget used for categorizing and tracking transactions.
     */
    protected Budget budget;

    /**
     * The forecast used for planning and projecting future transactions.
     */
    protected Forecast forecast;

    /**
     * The view interface for interacting with the user.
     */
    protected ViewInt view;

    /**
     * The notification service for sending asynchronous notifications.
     */
    protected NotificationServiceInt notificationService;

    /**
     * Gets the register from the session controller.
     * This ensures we always use the current register, even if it changes during the session.
     *
     * @return The current register from the session controller
     */
    protected Register getRegister() {
        return sessionController.getRegister();
    }

    // QFX import fields
    private TransactionParser<QfxTransaction> qfxParser;
    private boolean isQfxOpen = false;
    private Double cachedLedgerBalance = null;
    /** When the bank said the cached ledger balance was true, or null if it did not say. */
    private java.util.Calendar cachedLedgerBalanceAsOf = null;

    // CSV import fields (using Apache Commons CSV directly)
    private org.apache.commons.csv.CSVParser csvApacheParser;
    private Iterator<CSVRecord> csvRecordIterator;
    private InputStreamReader csvReader;
    private boolean isCsvOpen = false;

    /**
     * Constructs a new FinancialInstitution with the specified dependencies.
     *
     * @param sessionController The session controller containing register, budget, forecast, view, and notificationService.
     */
    protected FinancialInstitution(SessionController sessionController) {
        this.sessionController = sessionController;
        this.budget = sessionController.getBudget();
        this.forecast = sessionController.getForecast();
        this.view = sessionController.getView();
        this.notificationService = sessionController.getNotificationService();
    }

    /**
     * Generic implementation of provisional transaction reconciliation.
     * This method handles the common logic for reconciling a cleared transaction with
     * its provisional counterpart, including tip detection and balance adjustments.
     *
     * When a cleared transaction comes through, this method:
     * 1. Transfers properties (ID, flags) from the provisional to the cleared transaction
     * 2. Detects if there's a tip (difference in amount between provisional and cleared)
     * 3. Adjusts the register balance by the tip amount if a tip is detected
     * 4. Updates the first split to include the tip amount
     * 5. Logs the tip information for the user
     *
     * Financial institutions can override this method if they have specific
     * reconciliation requirements.
     *
     * @param clearedTransaction The cleared transaction from CSV import
     * @param provisionalTransaction The matching provisional transaction (or null)
     * @param register The register to update
     * @param splits The splits list (will be updated in place if tip detected)
     * @return true if provisional transaction was found and reconciled
     * @throws Exception if an error occurs during reconciliation
     * @throws IllegalArgumentException if either transaction is null
     */
    @Override
    public boolean reconcileProvisionalTransaction(Transaction clearedTransaction,
                                                   Transaction provisionalTransaction,
                                                   Register register,
                                                   List<TransactionSplit> splits) throws Exception {

        if (provisionalTransaction == null || clearedTransaction == null) {
            throw new IllegalArgumentException("Provisional or cleared transaction is null in reconcileProvisionalTransaction.");
        }

        // Transfer properties from provisional to cleared transaction
        clearedTransaction.setId(provisionalTransaction.getId());
        clearedTransaction.setIdMerchant(provisionalTransaction.getIdMerchant());
        clearedTransaction.setMerchant(provisionalTransaction.getMerchant());
        clearedTransaction.setMerchantPayee(provisionalTransaction.getMerchantPayee());
        clearedTransaction.setIsImproper(provisionalTransaction.getIsImproper());
        clearedTransaction.setIsNew(false);

        // Check if there's a tip (cleared amount differs from provisional amount)
        double tipAmount = clearedTransaction.getAmount() - provisionalTransaction.getAmount();
        boolean hasTip = Math.abs(tipAmount) > 0.01; // More than 1 cent difference

        // If there's a tip, handle the balance adjustment and split updates
        if (hasTip) {
            // Adjust the register balance by the tip amount (difference between cleared and provisional)
            register.setBalance(register.getBalance() + tipAmount);
            register.update();

            // Store the tip information on the transaction for display later
            // (after the transaction log heading is shown)
            clearedTransaction.setTipAmount(tipAmount);
            clearedTransaction.setProvisionalAmount(provisionalTransaction.getAmount());

            // If splits exist, we need to adjust them to account for the tip
            // The tip will be added to the first split (typically the meal/service charge)
            if (splits != null && !splits.isEmpty()) {
                TransactionSplit firstSplit = splits.get(0);
                firstSplit.setAmount(firstSplit.getAmount() + tipAmount);
                firstSplit.setDirty(true);
            }
        }

        return true;
    }

    // ========================================
    // Transaction File Import Methods
    // ========================================

    /**
     * Imports transactions from the register's configured import file.
     *
     * <p>This method reads the import file path from the register,
     * determines the file type by extension, and creates the appropriate parser
     * (CSV, QFX, etc.) to read transactions.
     *
     * <p><strong>Supported File Extensions:</strong>
     * <ul>
     *   <li>.qfx - QFX/OFX format (uses QfxParser)</li>
     *   <li>.csv, .tsv - CSV format (institution-specific, not yet implemented here)</li>
     * </ul>
     *
     * <p>Subclasses can override this method to provide custom import logic
     * or to handle additional file formats.
     *
     * @throws Exception if the file cannot be found, opened, or parsed
     * @throws IllegalStateException if register doesn't have import file configured
     */
    public void importRegisterTrxFile() throws Exception {
        // Get full import file path from register
        String fullPath = getRegister().getTrxImportFilePath();

        if (fullPath == null || fullPath.trim().isEmpty()) {
            throw new IllegalStateException(
                "Register '" + getRegister().getName() + "' does not have an import file path configured. " +
                "Please set trxImportFileName and trxImportFileDirectory fields."
            );
        }

        // Determine file type by extension
        String extension = "";
        int lastDot = fullPath.lastIndexOf('.');
        if (lastDot > 0) {
            extension = fullPath.substring(lastDot + 1).toLowerCase();
        }

        // Create appropriate parser based on file extension
        switch (extension) {
            case "qfx", "ofx" -> {
                // QFX/OFX format - use QFX parser
                importQfxRegisterTrxFile(fullPath);
            }
            case "csv", "tsv" -> {
                // CSV/TSV format - use CSV parser
                importCsvRegisterTrxFile(fullPath);
            }
            default -> {
                throw new IllegalArgumentException(
                    "Unsupported import file format: '" + extension + "'. " +
                    "Supported formats: .qfx, .ofx, .csv, .tsv. " +
                    "File: " + fullPath
                );
            }
        }
    }

    // ========================================
    // QFX Import Methods (shared by all institutions using QFX format)
    // ========================================

    /**
     * Imports transactions from a QFX file.
     * This method is used by any financial institution that supports QFX/OFX format.
     *
     * The QFX parser loads all transactions into memory during the open() call,
     * so we can safely close the FileInputStream immediately after parsing.
     *
     * @param filename the QFX file to import
     * @throws Exception if the file cannot be opened or parsed
     */
    protected void importQfxRegisterTrxFile(String filename) throws Exception {
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("QFX filename cannot be null or empty");
        }

        this.qfxParser = new QfxParser();
        this.cachedLedgerBalance = null;
        this.cachedLedgerBalanceAsOf = null;

        // Use try-with-resources to ensure FileInputStream is properly closed after parsing
        // The parser loads all transactions into memory, so we don't need to keep the stream open
        try (FileInputStream fis = new FileInputStream(filename)) {
            // Open the parser - this reads and parses the entire file into memory
            this.qfxParser.open(fis);

            // Cache the ledger balance immediately after parsing while we have access
            if (qfxParser instanceof QfxParser) {
                QfxStatement statement = ((QfxParser) qfxParser).getStatement();
                if (statement != null) {
                    this.cachedLedgerBalance = statement.getLedgerBalance();
                    this.cachedLedgerBalanceAsOf = statement.getLedgerBalanceAsOf();
                }
            }

            // Stream is automatically closed here by try-with-resources
        } catch (Exception e) {
            // If opening fails, make sure we clean up
            this.isQfxOpen = false;
            this.qfxParser = null;
            this.cachedLedgerBalance = null;
            this.cachedLedgerBalanceAsOf = null;
            throw e;
        }

        // Mark as open only after successful parse and stream closure
        this.isQfxOpen = true;
    }

    // ========================================
    // CSV Import Methods (shared by all institutions using CSV format)
    // ========================================

    /**
     * Imports transactions from a CSV file.
     * This method is used by any financial institution that supports CSV/TSV format.
     *
     * Unlike QFX, CSV files are parsed on-demand as we iterate through records,
     * so we need to keep the file and reader open until iteration is complete.
     *
     * @param filename the CSV file to import
     * @throws Exception if the file cannot be opened or parsed
     */
    protected void importCsvRegisterTrxFile(String filename) throws Exception {
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("CSV filename cannot be null or empty");
        }

        if (isCsvOpen) {
            throw new IllegalStateException("CSV parser is already open. Call close() first.");
        }

        try {
            // Create file input stream and reader
            FileInputStream fis = new FileInputStream(filename);
            this.csvReader = new InputStreamReader(fis, StandardCharsets.UTF_8);

            // Get the institution-specific CSV format configuration
            // This allows each bank to specify its own CSV format (delimiter, headers, etc.)
            CSVFormat csvFormat = getCsvFormat();

            // Create Apache Commons CSV parser
            this.csvApacheParser = csvFormat.parse(csvReader);

            // Create iterator over CSV records
            this.csvRecordIterator = csvApacheParser.iterator();

            // Mark as open
            this.isCsvOpen = true;

        } catch (Exception e) {
            // If opening fails, make sure we clean up
            closeCsvResources();
            this.isCsvOpen = false;
            throw e;
        }
    }

    /**
     * Closes CSV parsing resources (parser and reader).
     */
    private void closeCsvResources() {
        try {
            if (csvApacheParser != null) {
                csvApacheParser.close();
            }
        } catch (IOException e) {
            // Log but don't throw
            System.err.println("Warning: Error closing CSV parser: " + e.getMessage());
        } finally {
            csvApacheParser = null;
        }

        try {
            if (csvReader != null) {
                csvReader.close();
            }
        } catch (IOException e) {
            // Log but don't throw
            System.err.println("Warning: Error closing CSV reader: " + e.getMessage());
        } finally {
            csvReader = null;
        }

        csvRecordIterator = null;
    }

    /**
     * The transfer types a Wells Fargo description can open with.  Note that {@code RECURRING
     * PAYMENT} is deliberately absent:  that is a card charge whose MEMO is the bank's own
     * annotation, not the tail of a transfer description.
     */
    private static final String[] TRANSFER_TYPES = {
            "ONLINE TRANSFER",
            "RECURRING TRANSFER",
            "ATM TRANSFER",
            "SAVE AS YOU",
            "TRANSFER IN BRANCH"
    };

    /**
     * The number of characters the bank leaves in the OFX NAME field when it has moved the transfer
     * type into the MEMO.  A NAME of exactly this length is the signature of a hard cut -- see
     * {@link #joinNameAndMemo}.
     */
    private static final int NAME_FIELD_WIDTH = 32;

    /**
     * Repair whatever this institution's exporter does to a text field on its way into a QFX file.
     *
     * <p>The default is to leave the text exactly as the OFX parser produced it, which is right for
     * any bank that writes a conforming file.  {@code WellsFargoBank} overrides it;  see there for
     * what Wells Fargo does and how it was diagnosed.
     *
     * <p>This is deliberately a hook on the institution rather than a step in the parser.  The
     * parser is shared by every bank and is already correct -- ofx4j resolves XML entities on both
     * the OFX 1.x SGML and OFX 2.x XML paths -- so a repair applied there would be applied to banks
     * that do not need it.  Citibank and Barclays files from the same period escape correctly, and
     * every corrupted row in the database came from a Wells Fargo register.
     *
     * @param text a NAME or MEMO field as the OFX parser produced it;  may be null
     * @return the text to use, unchanged by default
     */
    protected String normalizeImportedText(String text) {
        return text;
    }

    /**
     * Converts a QfxTransaction to a Transaction domain object.
     * Subclasses can override this if they need custom conversion logic.
     *
     * <p><b>The returned transaction has no merchant payee yet.</b>  Parsing it can ask the user a
     * question -- {@code WellsFargoBank.parseMerchantPayee} prompts for the counterparty register
     * when a transfer's payee carries no account number -- and at this point nobody yet knows
     * whether this row was already imported on a previous run.  Asking here means re-importing an
     * overlapping statement re-asks the register question for every transfer already in the
     * register, and the answer is thrown away.  So the parse is deferred to
     * {@code ImportController}, which calls {@link #parseMerchantPayee} only once it has looked the
     * import record id up and found nothing.  The provisional import path already works this way.
     *
     * @param qfxTxn the QFX transaction
     * @return a Transaction object, with a null merchant payee for the caller to fill in
     * @throws Exception if conversion fails
     */
    protected Transaction convertQfxToTransaction(QfxTransaction qfxTxn) throws Exception {
        // Convert LocalDate to Calendar
        Calendar postDate = Utility.localDateToCalendarDate(qfxTxn.getPostedDate());

        // Get payee from QFX transaction.  A transfer's description does not fit one OFX field, so
        // the bank splits it across NAME and MEMO and this puts it back together -- see
        // joinNameAndMemo, which is where the two shapes it uses are documented.  Both halves go
        // through normalizeImportedText first, which is where an institution repairs whatever its
        // own exporter does to the text.
        String payee = joinNameAndMemo(normalizeImportedText(qfxTxn.getName()),
                normalizeImportedText(qfxTxn.getMemo()));

        // QFX transactions are always cleared
        boolean cleared = true;

        // Credit cards don't have check numbers
        int checkNumber = 0;

        // Use FITID as import record ID
        String importRecordId = qfxTxn.getFitId();

        // Create the transaction
        Transaction transaction = new Transaction(
            getRegister(),
            postDate,
            payee,
            qfxTxn.getAmount(),
            cleared,
            checkNumber,
            importRecordId
        );

        // Record the user's memo, if they typed one.  It is the only place the user says why they
        // moved the money, and auto-matching reads it back out of user_description later.
        transaction.setUserDescription(extractUserDescription(payee));

        // The merchant payee is deliberately left unset -- see the note on this method.
        return transaction;
    }

    /**
     * Converts a CSVRecord to a Transaction domain object.
     * This delegates to the institution-specific createFromCSVRecord method.
     *
     * @param csvRecord the CSV record
     * @return a Transaction object
     * @throws Exception if conversion fails
     */
    protected Transaction convertCsvToTransaction(CSVRecord csvRecord) throws Exception {
        // Get the import record base name from the institution-specific method
        String importRecordBaseName = getRegisterImportRecordBaseName(csvRecord);

        // Create transaction using institution-specific logic
        return createFromCSVRecord(csvRecord, importRecordBaseName);
    }

    /**
     * Returns true if the QFX transaction name indicates a transfer transaction.
     * Transfer transactions benefit from having the MEMO appended to the payee string
     * because the MEMO often contains masked account numbers used for register identification.
     *
     * @param name the QFX NAME field value
     * @return true if this looks like a transfer transaction
     */
    private static boolean isTransferPayee(String name) {
        return leadingTransferType(name) != null;
    }

    /**
     * The transfer type a bank description opens with, if it opens with one.
     *
     * @param text the text to inspect, either an OFX NAME or an OFX MEMO
     * @return the matched type in its original casing, or null if the text does not start with one
     */
    private static String leadingTransferType(String text) {
        if (text == null) {
            return null;
        }

        String upper = text.toUpperCase();
        for (String type : TRANSFER_TYPES) {
            if (upper.startsWith(type)) {
                return text.substring(0, type.length());
            }
        }
        return null;
    }

    /**
     * Rebuild one transaction description from the two OFX fields the bank splits it across.
     *
     * <p>Wells Fargo writes a transfer's description into NAME and MEMO in one of two shapes, and
     * the difference is not cosmetic:  the memo the user typed lives at the end of the description,
     * so getting the join wrong loses it or mangles it.  Both shapes below are taken from real
     * statements, and the reconstruction reproduces the CSV-era payee for the same transfer exactly.
     *
     * <p><b>Shape 1 -- the type stays in the NAME.</b>  The NAME is filled with as many whole words
     * as fit and the MEMO holds the rest, so the split falls on a space and the join restores it:
     *
     * <pre>
     *   NAME  ONLINE TRANSFER FROM RYBICKI C
     *   MEMO  REF #IB0ZKYDNXN EVERYDAY CHECKING GROCERY
     *   -->   ONLINE TRANSFER FROM RYBICKI C REF #IB0ZKYDNXN EVERYDAY CHECKING GROCERY
     * </pre>
     *
     * <p><b>Shape 2 -- the type is moved into the MEMO.</b>  The NAME then holds exactly
     * {@link #NAME_FIELD_WIDTH} characters of what is left, cut wherever it lands -- <em>mid-word</em>
     * -- and the MEMO is the type followed by the remainder.  Joining these with a space is what a
     * reader would do and it is wrong:  it splits EVERYDAY into EVERY DAY, the account-type phrase
     * stops being recognizable, and {@code extractUserDescription} returns the whole garbled tail
     * instead of the memo:
     *
     * <pre>
     *   NAME  TO HIXON D REF #OP0ZLPN68K EVERY          (32 characters, cut inside EVERYDAY)
     *   MEMO  RECURRING TRANSFER DAY CHECKING MICHELE ALIMONY DWH
     *   -->   RECURRING TRANSFER TO HIXON D REF #OP0ZLPN68K EVERYDAY CHECKING MICHELE ALIMONY DWH
     *   -->   user description:  MICHELE ALIMONY DWH
     * </pre>
     *
     * <p>The 32-character test is what separates the two.  It is the signature of a hard cut:  shape
     * 1 breaks on word boundaries and lands short of the width (28 and 30 characters in the samples
     * above), so a MEMO that merely starts with a transfer type -- {@code SAVE AS YOU GO TRANSFER
     * DEBIT} against a short NAME, which is a complete phrase and not a continuation -- is left
     * alone rather than glued on.
     *
     * @param name the OFX NAME field
     * @param memo the OFX MEMO field, which may be null or blank
     * @return the reassembled description, or the name alone when the memo is not part of it
     */
    static String joinNameAndMemo(String name, String memo) {

        if (name == null || memo == null || memo.isBlank()) {
            return name;
        }

        String trimmedMemo = memo.trim();

        // Shape 1:  the words that did not fit in the NAME, split on a space.
        if (isTransferPayee(name)) {
            return name + " " + trimmedMemo;
        }

        // Shape 2:  the type was relocated, so the NAME was cut at the field width and the memo
        // carries on from that character.  No space at the seam -- there was none in the original.
        String type = leadingTransferType(trimmedMemo);
        if (type != null && name.length() == NAME_FIELD_WIDTH) {
            return type + " " + name + trimmedMemo.substring(type.length()).stripLeading();
        }

        // Everything else:  an ordinary purchase, whose MEMO is the bank's own annotation
        // ("PURCHASE 08/29 SARASOTA FL CARD 1955") and not part of the payee.
        return name;
    }

    // ========================================
    // Iterator<Transaction> Implementation
    // ========================================

    @Override
    public boolean hasNext() {
        // Check QFX parser first
        if (isQfxOpen && qfxParser != null) {
            return qfxParser.hasNext();
        }

        // Check CSV parser
        if (isCsvOpen && csvRecordIterator != null) {
            return csvRecordIterator.hasNext();
        }

        // No parser is open
        return false;
    }

    @Override
    public Transaction next() {
        try {
            // Handle QFX format
            if (isQfxOpen && qfxParser != null) {
                // Get next QFX transaction from parser
                QfxTransaction qfxTxn = qfxParser.getNext();
                // Convert QfxTransaction to Transaction
                return convertQfxToTransaction(qfxTxn);
            }

            // Handle CSV format
            if (isCsvOpen && csvRecordIterator != null) {
                // Get next CSV record from iterator
                CSVRecord csvRecord = csvRecordIterator.next();
                // Convert CSVRecord to Transaction
                return convertCsvToTransaction(csvRecord);
            }

            // No parser is open
            throw new NoSuchElementException("No parser is open");

        } catch (Exception e) {
            throw new RuntimeException("Error converting transaction: " + e.getMessage(), e);
        }
    }
    /**
     * Gets the ledger balance from the imported transaction file (if available).
     * This is only available for QFX/OFX files after calling importRegisterTrxFile().
     *
     * @return the ledger balance from the import file, or null if not available
     *         (e.g., for CSV files or before import)
     */
    /**
     * When the bank said the imported ledger balance was true (OFX DTASOF), or null if unknown.
     *
     * <p>A statement covering a wider date range is not necessarily a newer one, and without this
     * there is no way to tell.  See QfxParser.extractLedgerBalanceAsOf for the download that made
     * the difference.
     *
     * @return the as-of date of the balance from the import file, or null if not available
     */
    @Override
    public java.util.Calendar getImportedLedgerBalanceAsOf() {
        return cachedLedgerBalanceAsOf;
    }

    @Override
    public Double getImportedLedgerBalance() {
        Double balance = null;
        if (cachedLedgerBalance != null) {
            balance = cachedLedgerBalance;
        }

        if (balance == null && qfxParser != null && isQfxOpen) {
            try {
                // Cast to QfxParser to access getStatement method
                if (qfxParser instanceof QfxParser) {
                    QfxStatement statement = ((QfxParser) qfxParser).getStatement();
                    if (statement != null) {
                        balance = statement.getLedgerBalance();
                    }
                }
            } catch (Exception e) {
                // If we can't get the balance, return null
                return null;
            }
        }

        // If we have a balance, adjust it by adding provisional transactions
        if (balance != null) {
            try {
                // Add provisional transactions to the balance to match App Register Balance
                // The QFX balance is typically the "Posted Balance" (cleared transactions only).
                // The App Register Balance includes both Cleared and Provisional (pending) transactions.
                // To compare them, we must add the provisional transactions to the QFX balance.
                // Examples:
                // 1. Expense: QFX Balance = $1000. Pending Expense = -$50. App Balance = $950.
                //    Adjusted QFX = $1000 + (-$50) = $950. Match.
                // 2. Deposit: QFX Balance = $1000. Pending Deposit = +$100. App Balance = $1100.
                //    Adjusted QFX = $1000 + (+$100) = $1100. Match.
                Register register = getRegister();
                if (register != null) {
                    double provisionalBalance = register.getProvisionalBalance();
                    balance += provisionalBalance;
                }
            } catch (Exception e) {
                // Log error but return unadjusted balance to avoid blocking the flow
                System.err.println("Warning: Failed to adjust ledger balance with provisional transactions: " + e.getMessage());
            }
        }

        return balance;
    }

    @Override
    public void close() throws Exception {
        try {
            // Close QFX parser if open
            if (qfxParser != null) {
                qfxParser.close();
            }

            // Close CSV resources if open
            if (isCsvOpen) {
                closeCsvResources();
            }
        } finally {
            isQfxOpen = false;
            qfxParser = null;
            isCsvOpen = false;
        }
    }
}
