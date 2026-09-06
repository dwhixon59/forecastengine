package com.hixon.financialApp.model.qfx;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
public class QfxStatement {
    private final String accountNumber;
    private final String currency;
    private final double ledgerBalance;
    /** When the bank says the ledger balance was true (OFX DTASOF), or null if it did not say. */
    private final Calendar ledgerBalanceAsOf;
    private final List<QfxTransaction> transactions;
    private QfxStatement(Builder builder) {
        this.accountNumber = Objects.requireNonNull(builder.accountNumber, "Account number cannot be null");
        this.currency = Objects.requireNonNull(builder.currency, "Currency cannot be null");
        this.ledgerBalance = builder.ledgerBalance;
        this.ledgerBalanceAsOf = builder.ledgerBalanceAsOf;
        this.transactions = builder.transactions != null ? 
                Collections.unmodifiableList(builder.transactions) : Collections.emptyList();
    }
    public String getAccountNumber() { return accountNumber; }
    public String getCurrency() { return currency; }
    public double getLedgerBalance() { return ledgerBalance; }
    public Calendar getLedgerBalanceAsOf() { return ledgerBalanceAsOf; }
    public List<QfxTransaction> getTransactions() { return transactions; }
    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private String accountNumber;
        private String currency;
        private double ledgerBalance;
        private Calendar ledgerBalanceAsOf;
        private List<QfxTransaction> transactions;
        public Builder accountNumber(String accountNumber) { this.accountNumber = accountNumber; return this; }
        public Builder currency(String currency) { this.currency = currency; return this; }
        public Builder ledgerBalance(double ledgerBalance) { this.ledgerBalance = ledgerBalance; return this; }
        public Builder ledgerBalanceAsOf(Calendar ledgerBalanceAsOf) { this.ledgerBalanceAsOf = ledgerBalanceAsOf; return this; }
        public Builder transactions(List<QfxTransaction> transactions) { this.transactions = transactions; return this; }
        public QfxStatement build() { return new QfxStatement(this); }
    }
}
