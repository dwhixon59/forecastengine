package com.hixon.financialApp.model.csv;

import com.hixon.financialApp.model.parser.TransactionData;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Represents a transaction extracted from a CSV file.
 *
 * <p>This class is an immutable DTO that holds transaction data from CSV format files.
 * It implements {@link TransactionData} to provide a common interface for all transaction formats.
 *
 * <p><strong>CSV-Specific Fields:</strong>
 * <ul>
 *   <li>checkNumber - Check number (0 if not a check)</li>
 *   <li>cleared - Whether transaction is cleared or provisional</li>
 * </ul>
 *
 * <p><strong>Immutability:</strong> All fields are final and set via builder pattern.
 * Thread-safe and can be safely shared between threads.
 *
 * @see TransactionData
 * @see CsvParser
 */
public class CsvTransaction implements TransactionData {

    private final LocalDate postDate;
    private final LocalDate authorizationDate;
    private final double amount;
    private final String payee;
    private final boolean cleared;
    private final int checkNumber;
    private final String importRecordId;

    private CsvTransaction(Builder builder) {
        this.postDate = Objects.requireNonNull(builder.postDate, "Post date cannot be null");
        this.authorizationDate = builder.authorizationDate;
        this.amount = builder.amount;
        this.payee = Objects.requireNonNull(builder.payee, "Payee cannot be null");
        this.cleared = builder.cleared;
        this.checkNumber = builder.checkNumber;
        this.importRecordId = Objects.requireNonNull(builder.importRecordId, "Import record ID cannot be null");
    }

    // TransactionData interface methods

    @Override
    public LocalDate getPostDate() {
        return postDate;
    }

    @Override
    public LocalDate getAuthorizationDate() {
        // CSV may or may not have auth date separate from post date
        return authorizationDate != null ? authorizationDate : postDate;
    }

    @Override
    public double getAmount() {
        return amount;
    }

    @Override
    public String getPayee() {
        return payee;
    }

    @Override
    public boolean isCleared() {
        return cleared;
    }

    @Override
    public String getImportRecordId() {
        return importRecordId;
    }

    // CSV-specific getters

    /**
     * Gets the check number.
     *
     * @return the check number, or 0 if not a check
     */
    public int getCheckNumber() {
        return checkNumber;
    }

    // Builder pattern

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private LocalDate postDate;
        private LocalDate authorizationDate;
        private double amount;
        private String payee;
        private boolean cleared;
        private int checkNumber;
        private String importRecordId;

        public Builder postDate(LocalDate postDate) {
            this.postDate = postDate;
            return this;
        }

        public Builder authorizationDate(LocalDate authorizationDate) {
            this.authorizationDate = authorizationDate;
            return this;
        }

        public Builder amount(double amount) {
            this.amount = amount;
            return this;
        }

        public Builder payee(String payee) {
            this.payee = payee;
            return this;
        }

        public Builder cleared(boolean cleared) {
            this.cleared = cleared;
            return this;
        }

        public Builder checkNumber(int checkNumber) {
            this.checkNumber = checkNumber;
            return this;
        }

        public Builder importRecordId(String importRecordId) {
            this.importRecordId = importRecordId;
            return this;
        }

        public CsvTransaction build() {
            return new CsvTransaction(this);
        }
    }

    @Override
    public String toString() {
        return "CsvTransaction{" +
                "postDate=" + postDate +
                ", amount=" + amount +
                ", payee='" + payee + '\'' +
                ", cleared=" + cleared +
                ", checkNumber=" + checkNumber +
                ", importRecordId='" + importRecordId + '\'' +
                '}';
    }
}

