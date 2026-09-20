package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.register.Transaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the notice that names the charge a refund reverses.
 *
 * <p>A refund's payee is one the merchant map knows, so the import identifies the merchant and then offers
 * every budget item that merchant has ever been used for -- nineteen of them for Amazon on 09-20-2026,
 * twice over.  The $18.18 refund reversed a charge the register had held since 08-28 under
 * "Other - Travel charger", and nothing on screen said so.
 */
@DisplayName("The refunded-charge notice")
class RefundedChargeNoticeTest {

    private static Transaction charge(double amount, int month, int day) {
        Transaction charge = mock(Transaction.class);
        when(charge.getAmount()).thenReturn(amount);
        when(charge.getDate()).thenReturn(new GregorianCalendar(2026, month, day));
        return charge;
    }

    @Test
    @DisplayName("The charge and how it was categorized are named")
    void namesTheChargeAndItsCategorization() {
        List<Transaction> charges = List.of(charge(-18.18, Calendar.AUGUST, 28));

        assertEquals("\nThis looks like a refund of the 08-28-2026 charge of $18.18, which went to " +
                        "Other - Travel charger.",
                BudgetController.refundedChargeNotice(charges, "Other - Travel charger"));
    }

    @Test
    @DisplayName("A charge with no categorization is still named")
    void namesAnUncategorizedCharge() {
        List<Transaction> charges = List.of(charge(-34.75, Calendar.SEPTEMBER, 15));

        assertEquals("\nThis looks like a refund of the 09-15-2026 charge of $34.75.",
                BudgetController.refundedChargeNotice(charges, null));
    }

    @Test
    @DisplayName("Several charges of the same amount say which one this is")
    void saysWhichOfSeveralCharges() {
        List<Transaction> charges = List.of(
                charge(-12.99, Calendar.SEPTEMBER, 2),
                charge(-12.99, Calendar.AUGUST, 28));

        assertEquals("\nThis looks like a refund of the 09-02-2026 charge of $12.99, which went to " +
                        "OTC Medicine - Benadryl.  (2 charges of that amount;  this is the most recent.)",
                BudgetController.refundedChargeNotice(charges, "OTC Medicine - Benadryl"));
    }

    @Test
    @DisplayName("Nothing is said when no charge matches")
    void saysNothingWithoutAMatch() {
        assertNull(BudgetController.refundedChargeNotice(List.of(), "Other"));
        assertNull(BudgetController.refundedChargeNotice(null, null));
    }
}
