package com.hixon.financialApp.model.csv;

import com.hixon.financialApp.model.parser.TransactionParser;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Parser for CSV (Comma-Separated Values) transaction files.
 *
 * <p>This parser implements {@link TransactionParser} to provide iterator-based
 * access to CSV transactions. It uses Apache Commons CSV library internally.
 *
 * <p><strong>CSV Format Configuration:</strong>
 * The parser is configured with a specific CSV format (headers, delimiter, etc.)
 * via the constructor. Different financial institutions may have different CSV formats.
 *
 * <p><strong>Usage:</strong>
 * <pre>{@code
 * // Create parser with format specification
 * CsvParser parser = new CsvParser(csvFormat, dateFormatter, headerMapping);
 *
 * try {
 *     parser.open(new FileInputStream("transactions.csv"));
 *     while (parser.hasNext()) {
 *         CsvTransaction txn = parser.getNext();
 *         // Process transaction...
 *     }
 * } finally {
 *     parser.close();
 * }
 * }</pre>
 *
 * <p><strong>Thread Safety:</strong> NOT thread-safe. Create separate instances per thread.
 *
 * @see CsvTransaction
 * @see TransactionParser
 */
public class CsvParser implements TransactionParser<CsvTransaction> {

    private final CSVFormat csvFormat;
    private final DateTimeFormatter dateFormatter;
    private final CsvColumnMapping columnMapping;

    // State management
    private boolean isOpen = false;
    private CSVParser apacheParser;
    private Iterator<CSVRecord> recordIterator;
    private Reader reader;

    /**
     * Creates a new CsvParser with specified format configuration.
     *
     * @param csvFormat the Apache Commons CSV format
     * @param dateFormatter the date format used in the CSV file
     * @param columnMapping the column name mapping for this CSV format
     */
    public CsvParser(CSVFormat csvFormat, DateTimeFormatter dateFormatter, CsvColumnMapping columnMapping) {
        this.csvFormat = csvFormat;
        this.dateFormatter = dateFormatter;
        this.columnMapping = columnMapping;
    }

    /**
     * Creates a new CsvParser with default configuration for Wells Fargo format.
     *
     * @return a CsvParser configured for Wells Fargo CSV files
     */
    public static CsvParser createWellsFargoParser() {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .build();

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");

        CsvColumnMapping mapping = new CsvColumnMapping(
                "Date",           // postDateColumn
                "Amount",         // amountColumn
                "Payee",          // payeeColumn
                "Cleared",        // clearedColumn
                "Check Number"    // checkNumberColumn
        );

        return new CsvParser(format, dateFormatter, mapping);
    }

    @Override
    public void open(InputStream input) throws Exception {
        if (input == null) {
            throw new IllegalArgumentException("Input stream cannot be null");
        }

        if (isOpen) {
            throw new IllegalStateException("Parser is already open. Call close() first.");
        }

        try {
            // Create reader
            this.reader = new InputStreamReader(input, StandardCharsets.UTF_8);

            // Create Apache CSV parser
            this.apacheParser = csvFormat.parse(reader);

            // Create iterator
            this.recordIterator = apacheParser.iterator();
            this.isOpen = true;

        } catch (Exception e) {
            // Clean up on failure
            closeReader();
            throw new CsvParseException("Failed to open CSV file: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean hasNext() {
        if (!isOpen) {
            throw new IllegalStateException("Parser is not open. Call open() first.");
        }

        return recordIterator.hasNext();
    }

    @Override
    public CsvTransaction getNext() throws Exception {
        if (!isOpen) {
            throw new IllegalStateException("Parser is not open. Call open() first.");
        }

        if (!hasNext()) {
            throw new NoSuchElementException("No more transactions available");
        }

        try {
            CSVRecord record = recordIterator.next();
            return convertToCsvTransaction(record);

        } catch (Exception e) {
            throw new CsvParseException("Error parsing CSV record: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() throws Exception {
        try {
            if (apacheParser != null) {
                apacheParser.close();
            }
            closeReader();
        } finally {
            isOpen = false;
            recordIterator = null;
            apacheParser = null;
        }
    }

    /**
     * Closes the reader if open.
     */
    private void closeReader() {
        if (reader != null) {
            try {
                reader.close();
            } catch (Exception e) {
                // Log but don't throw - we're cleaning up
                System.err.println("Warning: Error closing reader: " + e.getMessage());
            } finally {
                reader = null;
            }
        }
    }

    /**
     * Converts a CSV record to a CsvTransaction.
     *
     * @param record the CSV record
     * @return a CsvTransaction
     * @throws Exception if conversion fails
     */
    private CsvTransaction convertToCsvTransaction(CSVRecord record) throws Exception {
        // Parse date
        String dateStr = record.get(columnMapping.getPostDateColumn());
        LocalDate postDate = LocalDate.parse(dateStr, dateFormatter);

        // Parse amount
        String amountStr = record.get(columnMapping.getAmountColumn());
        double amount = Double.parseDouble(amountStr);

        // Get payee
        String payee = record.get(columnMapping.getPayeeColumn());

        // Parse cleared status
        boolean cleared = false;
        if (columnMapping.getClearedColumn() != null) {
            String clearedStr = record.get(columnMapping.getClearedColumn());
            cleared = "*".equals(clearedStr) || "true".equalsIgnoreCase(clearedStr);
        }

        // Parse check number
        int checkNumber = 0;
        if (columnMapping.getCheckNumberColumn() != null) {
            String checkNumStr = record.get(columnMapping.getCheckNumberColumn());
            if (checkNumStr != null && !checkNumStr.trim().isEmpty()) {
                try {
                    checkNumber = Integer.parseInt(checkNumStr.trim());
                } catch (NumberFormatException e) {
                    // Not a number, leave as 0
                }
            }
        }

        // Create import record ID (simple version - can be customized per institution)
        String importRecordId = dateStr + "\t" + amount + "\t" + (cleared ? "*" : "") + "\t" + payee;

        return CsvTransaction.builder()
                .postDate(postDate)
                .amount(amount)
                .payee(payee)
                .cleared(cleared)
                .checkNumber(checkNumber)
                .importRecordId(importRecordId)
                .build();
    }

    /**
     * Simple column mapping configuration for CSV files.
     */
    public static class CsvColumnMapping {
        private final String postDateColumn;
        private final String amountColumn;
        private final String payeeColumn;
        private final String clearedColumn;
        private final String checkNumberColumn;

        public CsvColumnMapping(String postDateColumn, String amountColumn, String payeeColumn,
                               String clearedColumn, String checkNumberColumn) {
            this.postDateColumn = postDateColumn;
            this.amountColumn = amountColumn;
            this.payeeColumn = payeeColumn;
            this.clearedColumn = clearedColumn;
            this.checkNumberColumn = checkNumberColumn;
        }

        public String getPostDateColumn() { return postDateColumn; }
        public String getAmountColumn() { return amountColumn; }
        public String getPayeeColumn() { return payeeColumn; }
        public String getClearedColumn() { return clearedColumn; }
        public String getCheckNumberColumn() { return checkNumberColumn; }
    }
}

