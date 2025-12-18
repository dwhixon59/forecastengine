package com.hixon.financialApp.model.qfx;
import com.hixon.financialApp.model.parser.TransactionData;
import java.time.LocalDate;
import java.util.Objects;
/**
 * Represents a transaction extracted from a QFX (Quicken Financial Exchange) file.
 * 
 * <p>This class is an immutable DTO that holds transaction data specific to the
 * QFX/OFX format. It implements {@link TransactionData} to provide a common interface
 * for all transaction formats.
 * 
 * <p><strong>QFX-Specific Fields:</strong>
 * <ul>
 *   <li>type - Transaction type (DEBIT/CREDIT)</li>
 *   <li>fitId - Financial Institution Transaction ID (unique identifier)</li>
 *   <li>userDate - Optional user date (may differ from posted date)</li>
 * </ul>
 * 
 * <p><strong>Immutability:</strong> All fields are final and set via builder pattern.
 * Thread-safe and can be safely shared between threads.
 * 
 * @see TransactionData
 * @see QfxParser
 */
public class QfxTransaction implements TransactionData {
    private final TransactionType type;
    private final LocalDate postedDate;
    private final LocalDate userDate;
    private final double amount;
    private final String fitId;
    private final String name;
    private QfxTransaction(Builder builder) {
        this.type = Objects.requireNonNull(builder.type, "Transaction type cannot be null");
        this.postedDate = Objects.requireNonNull(builder.postedDate, "Posted date cannot be null");
        this.userDate = builder.userDate;
        this.amount = builder.amount;
        this.fitId = Objects.requireNonNull(builder.fitId, "FITID cannot be null");
        this.name = Objects.requireNonNull(builder.name, "Name cannot be null");
    }
    // TransactionData interface methods
    @Override
    public LocalDate getPostDate() {
        return postedDate;
    }
    @Override
    public LocalDate getAuthorizationDate() {
        // QFX doesn't distinguish between posted and auth date in most cases
        // Use userDate if available, otherwise posted date
        return userDate != null ? userDate : postedDate;
    }
    @Override
    public double getAmount() {
        return amount;
    }
    @Override
    public String getPayee() {
        return name;
    }
    @Override
    public boolean isCleared() {
        // QFX transactions are always cleared (they come from the bank statement)
        return true;
    }
    @Override
    public String getImportRecordId() {
        return fitId;
    }
    // QFX-specific getters
    public TransactionType getType() {
        return type;
    }
    public LocalDate getPostedDate() {
        return postedDate;
    }
    public LocalDate getUserDate() {
        return userDate;
    }
    public String getFitId() {
        return fitId;
    }
    public String getName() {
        return name;
    }
    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }
    public static class Builder {
        private TransactionType type;
        private LocalDate postedDate;
        private LocalDate userDate;
        private double amount;
        private String fitId;
        private String name;
        public Builder type(TransactionType type) {
            this.type = type;
            return this;
        }
        public Builder postedDate(LocalDate postedDate) {
            this.postedDate = postedDate;
            return this;
        }
        public Builder userDate(LocalDate userDate) {
            this.userDate = userDate;
            return this;
        }
        public Builder amount(double amount) {
            this.amount = amount;
            return this;
        }
        public Builder fitId(String fitId) {
            this.fitId = fitId;
            return this;
        }
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        public QfxTransaction build() {
            return new QfxTransaction(this);
        }
    }
}
