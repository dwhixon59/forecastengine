package com.hixon.financialApp.model.qfx;
import java.time.LocalDate;
import java.util.Objects;
public class QfxTransaction {
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
    public TransactionType getType() { return type; }
    public LocalDate getPostedDate() { return postedDate; }
    public LocalDate getUserDate() { return userDate; }
    public double getAmount() { return amount; }
    public String getFitId() { return fitId; }
    public String getName() { return name; }
    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private TransactionType type;
        private LocalDate postedDate;
        private LocalDate userDate;
        private double amount;
        private String fitId;
        private String name;
        public Builder type(TransactionType type) { this.type = type; return this; }
        public Builder postedDate(LocalDate postedDate) { this.postedDate = postedDate; return this; }
        public Builder userDate(LocalDate userDate) { this.userDate = userDate; return this; }
        public Builder amount(double amount) { this.amount = amount; return this; }
        public Builder fitId(String fitId) { this.fitId = fitId; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public QfxTransaction build() { return new QfxTransaction(this); }
    }
}
