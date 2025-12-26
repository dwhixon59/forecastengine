package com.hixon.financialApp.model.qfx;
import com.hixon.financialApp.model.parser.TransactionParser;
import com.webcohesion.ofx4j.io.AggregateUnmarshaller;
import com.webcohesion.ofx4j.domain.data.ResponseEnvelope;
import com.webcohesion.ofx4j.domain.data.MessageSetType;
import com.webcohesion.ofx4j.domain.data.ResponseMessageSet;
import com.webcohesion.ofx4j.domain.data.creditcard.CreditCardStatementResponseTransaction;
import com.webcohesion.ofx4j.domain.data.creditcard.CreditCardResponseMessageSet;
import com.webcohesion.ofx4j.domain.data.common.Transaction;
import com.webcohesion.ofx4j.domain.data.common.TransactionType;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Parser for QFX (Quicken Financial Exchange) files.
 * 
 * <p>This parser implements {@link TransactionParser} to provide iterator-based
 * access to QFX transactions. It uses the ofx4j library internally to parse
 * the OFX/QFX format.
 * 
 * <p><strong>Usage:</strong>
 * <pre>{@code
 * QfxParser parser = new QfxParser();
 * try {
 *     parser.open(new FileInputStream("statement.qfx"));
 *     while (parser.hasNext()) {
 *         QfxTransaction txn = parser.getNext();
 *         // Process transaction...
 *     }
 * } finally {
 *     parser.close();
 * }
 * }</pre>
 * 
 * <p><strong>Thread Safety:</strong> NOT thread-safe. Create separate instances per thread.
 * 
 * @see QfxTransaction
 * @see TransactionParser
 */
public class QfxParser implements TransactionParser<QfxTransaction> {
    private final AggregateUnmarshaller<ResponseEnvelope> unmarshaller;
    // State management
    private boolean isOpen = false;
    private Iterator<QfxTransaction> transactionIterator;
    private InputStream currentInputStream;
    private QfxStatement statement;
    /**
     * Creates a new QfxParser with default configuration.
     */
    public QfxParser() {
        this.unmarshaller = new AggregateUnmarshaller<>(ResponseEnvelope.class);
    }
    /**
     * Opens and parses a QFX file from an input stream.
     * 
     * <p>This method parses the entire QFX file and prepares an iterator
     * over the transactions. The input stream will be closed when {@link #close()}
     * is called.
     * 
     * @param input the input stream containing QFX data
     * @throws QfxParseException if the file cannot be parsed
     * @throws IllegalArgumentException if input is null
     * @throws IllegalStateException if parser is already open
     */
    @Override
    public void open(InputStream input) throws Exception {
        if (input == null) {
            throw new IllegalArgumentException("Input stream cannot be null");
        }
        if (isOpen) {
            throw new IllegalStateException("Parser is already open. Call close() first.");
        }
        this.currentInputStream = input;
        try {
            // Parse the QFX file using ofx4j
            ResponseEnvelope envelope = unmarshaller.unmarshal(input);

            // Extract transactions from the envelope
            List<QfxTransaction> transactions = extractTransactions(envelope);

            // Extract statement metadata
            String accountNumber = extractAccountNumber(envelope);
            String currency = extractCurrency(envelope);
            double ledgerBalance = extractLedgerBalance(envelope);

            this.statement = QfxStatement.builder()
                    .accountNumber(accountNumber)
                    .currency(currency)
                    .ledgerBalance(ledgerBalance)
                    .transactions(transactions)
                    .build();

            // Create iterator over transactions
            this.transactionIterator = transactions.iterator();
            this.isOpen = true;
        } catch (Exception e) {
            // Clean up on failure
            closeInputStream();
            throw new QfxParseException("Failed to parse QFX file: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts transactions from the OFX ResponseEnvelope.
     */
    private List<QfxTransaction> extractTransactions(ResponseEnvelope envelope) {
        List<QfxTransaction> transactions = new ArrayList<>();

        // Get credit card message set
        ResponseMessageSet messageSet = envelope.getMessageSet(MessageSetType.creditcard);
        if (messageSet == null) {
            return transactions; // No credit card transactions
        }

        CreditCardResponseMessageSet ccMessageSet = (CreditCardResponseMessageSet) messageSet;

        // Get statement response transactions
        List<CreditCardStatementResponseTransaction> statementResponses =
            ccMessageSet.getStatementResponses();

        if (statementResponses == null || statementResponses.isEmpty()) {
            return transactions;
        }

        // Process each statement response
        for (CreditCardStatementResponseTransaction statementResponse : statementResponses) {
            if (statementResponse.getMessage() == null ||
                statementResponse.getMessage().getTransactionList() == null) {
                continue;
            }

            List<Transaction> ofxTransactions =
                statementResponse.getMessage().getTransactionList().getTransactions();

            if (ofxTransactions == null) {
                continue;
            }

            // Convert each OFX transaction to QfxTransaction
            for (Transaction ofxTxn : ofxTransactions) {
                QfxTransaction qfxTxn = convertTransaction(ofxTxn);
                if (qfxTxn != null) {
                    transactions.add(qfxTxn);
                }
            }
        }

        return transactions;
    }

    /**
     * Converts an ofx4j Transaction to a QfxTransaction.
     */
    private QfxTransaction convertTransaction(Transaction ofxTxn) {
        try {
            // Determine transaction type
            com.hixon.financialApp.model.qfx.TransactionType type =
                convertTransactionType(ofxTxn.getTransactionType());

            // Convert dates
            LocalDate postedDate = convertDate(ofxTxn.getDatePosted());
            LocalDate userDate = convertDate(ofxTxn.getDateInitiated()); // Use DateInitiated instead of DateUser

            return QfxTransaction.builder()
                    .type(type)
                    .postedDate(postedDate)
                    .userDate(userDate)
                    .amount(ofxTxn.getAmount())
                    .fitId(ofxTxn.getId())
                    .name(ofxTxn.getName())
                    .build();
        } catch (Exception e) {
            System.err.println("Warning: Failed to convert transaction: " + e.getMessage());
            return null;
        }
    }

    /**
     * Converts ofx4j TransactionType to our TransactionType enum.
     */
    private com.hixon.financialApp.model.qfx.TransactionType convertTransactionType(
            TransactionType ofxType) {
        if (ofxType == null) {
            return com.hixon.financialApp.model.qfx.TransactionType.DEBIT;
        }

        switch (ofxType) {
            case CREDIT:
            case DEP:
            case INT:
            case DIV:
                return com.hixon.financialApp.model.qfx.TransactionType.CREDIT;
            case DEBIT:
            case CHECK:
            case ATM:
            case POS:
            case XFER:
            case PAYMENT:
            case CASH:
            case DIRECTDEP:
            case DIRECTDEBIT:
            case REPEATPMT:
            case FEE:
            case SRVCHG:
            case OTHER:
            default:
                return com.hixon.financialApp.model.qfx.TransactionType.DEBIT;
        }
    }

    /**
     * Converts a Date to LocalDate.
     */
    private LocalDate convertDate(Date date) {
        if (date == null) {
            return null;
        }
        return Instant.ofEpochMilli(date.getTime())
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }

    /**
     * Extracts account number from the envelope.
     */
    private String extractAccountNumber(ResponseEnvelope envelope) {
        try {
            ResponseMessageSet messageSet = envelope.getMessageSet(MessageSetType.creditcard);
            if (messageSet == null) {
                return "UNKNOWN";
            }

            CreditCardResponseMessageSet ccMessageSet = (CreditCardResponseMessageSet) messageSet;
            List<CreditCardStatementResponseTransaction> responses =
                ccMessageSet.getStatementResponses();

            if (responses != null && !responses.isEmpty() &&
                responses.get(0).getMessage() != null &&
                responses.get(0).getMessage().getAccount() != null) {
                return responses.get(0).getMessage().getAccount().getAccountNumber();
            }
        } catch (Exception e) {
            System.err.println("Warning: Failed to extract account number: " + e.getMessage());
        }
        return "UNKNOWN";
    }

    /**
     * Extracts currency from the envelope.
     */
    private String extractCurrency(ResponseEnvelope envelope) {
        try {
            ResponseMessageSet messageSet = envelope.getMessageSet(MessageSetType.creditcard);
            if (messageSet == null) {
                return "USD";
            }

            CreditCardResponseMessageSet ccMessageSet = (CreditCardResponseMessageSet) messageSet;
            List<CreditCardStatementResponseTransaction> responses =
                ccMessageSet.getStatementResponses();

            if (responses != null && !responses.isEmpty() &&
                responses.get(0).getMessage() != null) {
                String currency = responses.get(0).getMessage().getCurrencyCode();
                return currency != null ? currency : "USD";
            }
        } catch (Exception e) {
            System.err.println("Warning: Failed to extract currency: " + e.getMessage());
        }
        return "USD";
    }

    /**
     * Extracts ledger balance from the envelope.
     */
    private double extractLedgerBalance(ResponseEnvelope envelope) {
        try {
            ResponseMessageSet messageSet = envelope.getMessageSet(MessageSetType.creditcard);
            if (messageSet == null) {
                return 0.0;
            }

            CreditCardResponseMessageSet ccMessageSet = (CreditCardResponseMessageSet) messageSet;
            List<CreditCardStatementResponseTransaction> responses =
                ccMessageSet.getStatementResponses();

            if (responses != null && !responses.isEmpty() &&
                responses.get(0).getMessage() != null &&
                responses.get(0).getMessage().getLedgerBalance() != null) {
                return responses.get(0).getMessage().getLedgerBalance().getAmount();
            }
        } catch (Exception e) {
            System.err.println("Warning: Failed to extract ledger balance: " + e.getMessage());
        }
        return 0.0;
    }

    /**
     * Checks if there are more transactions to read.
     * 
     * @return true if more transactions are available
     * @throws IllegalStateException if parser is not open
     */
    @Override
    public boolean hasNext() {
        if (!isOpen) {
            throw new IllegalStateException("Parser is not open. Call open() first.");
        }
        return transactionIterator.hasNext();
    }
    /**
     * Returns the next transaction from the QFX file.
     * 
     * @return the next QfxTransaction
     * @throws NoSuchElementException if no more transactions are available
     * @throws IllegalStateException if parser is not open
     */
    @Override
    public QfxTransaction getNext() throws Exception {
        if (!isOpen) {
            throw new IllegalStateException("Parser is not open. Call open() first.");
        }
        if (!hasNext()) {
            throw new NoSuchElementException("No more transactions available");
        }
        return transactionIterator.next();
    }
    /**
     * Closes the parser and releases all resources.
     * 
     * <p>After calling this method, the parser must be reopened with
     * {@link #open(InputStream)} before it can be used again.
     * 
     * <p>This method is idempotent - calling it multiple times is safe.
     */
    @Override
    public void close() throws Exception {
        try {
            closeInputStream();
        } finally {
            isOpen = false;
            transactionIterator = null;
            statement = null;
        }
    }
    /**
     * Closes the input stream if it's open.
     */
    private void closeInputStream() {
        if (currentInputStream != null) {
            try {
                currentInputStream.close();
            } catch (Exception e) {
                // Log but don't throw - we're cleaning up
                System.err.println("Warning: Error closing input stream: " + e.getMessage());
            } finally {
                currentInputStream = null;
            }
        }
    }
    /**
     * Gets the parsed statement (for compatibility with existing code).
     * 
     * @return the QfxStatement
     * @throws IllegalStateException if parser is not open
     * @deprecated Use iterator methods (hasNext/getNext) instead
     */
    @Deprecated
    public QfxStatement getStatement() {
        if (!isOpen) {
            throw new IllegalStateException("Parser is not open. Call open() first.");
        }
        return statement;
    }
}
