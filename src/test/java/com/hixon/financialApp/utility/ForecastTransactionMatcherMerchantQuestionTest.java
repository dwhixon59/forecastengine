package com.hixon.financialApp.utility;

import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.model.register.Transaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.GregorianCalendar;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The merchant question {@link ForecastTransactionMatcher} asks when a transaction lines up with a forecast
 * occurrence on date and amount but not on merchant.
 *
 * <p>It is asked before the transaction has printed anything, so it has to say which transaction it is about.
 * On 09-18-2026 it named neither, came straight after "Already imported a debit to LA Fitness for $80.23", and
 * read as a question about LA Fitness when it was about the $1,600.00 payment that followed.
 */
@DisplayName("Forecast Transaction Matcher - merchant question")
class ForecastTransactionMatcherMerchantQuestionTest {

    private static Transaction transaction(double amount, String payee) {
        Transaction transaction = mock(Transaction.class);
        when(transaction.getAmount()).thenReturn(amount);
        when(transaction.getPayee()).thenReturn(payee);
        when(transaction.getDate()).thenReturn(new GregorianCalendar(2026, 8, 17));
        return transaction;
    }

    private static BudgetItem budgetItem(String payee) {
        BudgetItem budgetItem = mock(BudgetItem.class);
        when(budgetItem.getPayee()).thenReturn(payee);
        return budgetItem;
    }

    @Test
    @DisplayName("A deposit is named by its date, amount and the payee the bank sent")
    void describesADeposit() {
        assertEquals("The 09-17-2026 deposit of $1,600.00 from 'ELECTRONIC PAYMENT-THANK YO'",
                ForecastTransactionMatcher.describeTransaction(transaction(1600.00, "ELECTRONIC PAYMENT-THANK YO")));
    }

    @Test
    @DisplayName("A debit is named with its amount unsigned")
    void describesADebit() {
        assertEquals("The 09-17-2026 debit of $80.23 to 'LA FITNESS'",
                ForecastTransactionMatcher.describeTransaction(transaction(-80.23, "LA FITNESS")));
    }

    @Test
    @DisplayName("The heading names the transaction and the occurrence it lines up with")
    void headingNamesTransactionAndOccurrence() {
        ForecastTransaction candidate = mock(ForecastTransaction.class);
        when(candidate.getPlannedDate()).thenReturn(new GregorianCalendar(2026, 8, 17));
        when(candidate.getRemainingAmount()).thenReturn(1600.00);

        assertEquals("The 09-17-2026 deposit of $1,600.00 from 'ELECTRONIC PAYMENT-THANK YO' matches " +
                        "'Payment - Dave', due 09-17-2026 for $1,600.00, on date and amount.",
                ForecastTransactionMatcher.merchantMismatchHeading(
                        "The 09-17-2026 deposit of $1,600.00 from 'ELECTRONIC PAYMENT-THANK YO'",
                        candidate, budgetItem("Payment - Dave")));
    }

    @Test
    @DisplayName("An unknown payee is asked about as the budget item, not as 'another merchant'")
    void questionForAnUnknownPayee() {
        assertEquals("Its payee is not linked to any of the merchants of 'Payment - Dave'. " +
                        "Is this transaction 'Payment - Dave'?",
                ForecastTransactionMatcher.merchantMismatchQuestion(List.of(), budgetItem("Payment - Dave")));
    }

    @Test
    @DisplayName("A known merchant is named in the question")
    void questionForAKnownMerchant() {
        Merchant merchant = mock(Merchant.class);
        when(merchant.getName()).thenReturn("Anthropic");

        assertEquals("Its merchant ('Anthropic') is not one of the merchants of 'LinkedIn'. " +
                        "Is this transaction 'LinkedIn'?",
                ForecastTransactionMatcher.merchantMismatchQuestion(List.of(merchant), budgetItem("LinkedIn")));
    }
}
