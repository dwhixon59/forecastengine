package com.hixon.financialApp.model.financialinstitution;

import com.hixon.financialApp.controller.SessionController;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.model.register.TransactionUtilities;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Calendar;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests that a posted charge is matched to the pending row it was imported as, for both institutions
 * with a pending file.  Before 09-23 Citi returned no match at all, so a pending Citi charge that
 * posted would have stayed in the register beside its posted copy.  See
 * CITI_PENDING_TRANSACTIONS_DESIGN.md &sect;3.5.
 */
class PendingRowMatchingTest {

    private static final UUID REGISTER_ID = UUID.fromString("6781379b-7dbb-4f27-a6ee-d4c83e4f1f0f");

    @Test
    void citiLooksForThePendingRowByRegisterAmountDateAndPayee() throws Exception {
        CitiBank bank = new CitiBank(sessionWithRegister());
        Calendar postedDate = day(2026, 9, 16);
        Transaction posted = postedCharge("LA FITNESS", -80.23, postedDate);
        Transaction pending = mock(Transaction.class);

        try (MockedStatic<TransactionUtilities> matcher = mockStatic(TransactionUtilities.class)) {
            matcher.when(() -> TransactionUtilities.findMatchingProvisionalTransaction(
                    eq(REGISTER_ID), eq(-80.23), eq(postedDate), eq("LA FITNESS"))).thenReturn(pending);

            assertSame(pending, bank.getMatchingProvisionalTransaction(posted));
        }
    }

    @Test
    void citiReportsNoMatchWhenTheRegisterHoldsNoPendingRow() throws Exception {
        CitiBank bank = new CitiBank(sessionWithRegister());

        try (MockedStatic<TransactionUtilities> matcher = mockStatic(TransactionUtilities.class)) {
            matcher.when(() -> TransactionUtilities.findMatchingProvisionalTransaction(any(), anyDouble(), any(),
                    any())).thenReturn(null);

            assertNull(bank.getMatchingProvisionalTransaction(
                    postedCharge("NETFLIX", -22.56, day(2026, 9, 20))));
        }
    }

    @Test
    void wellsFargoStillLooksTheSameWay() throws Exception {
        WellsFargoBank bank = new WellsFargoBank(sessionWithRegister());
        Calendar postedDate = day(2026, 9, 24);
        Transaction posted = postedCharge("PUBLIX", -51.49, postedDate);
        Transaction pending = mock(Transaction.class);

        try (MockedStatic<TransactionUtilities> matcher = mockStatic(TransactionUtilities.class)) {
            matcher.when(() -> TransactionUtilities.findMatchingProvisionalTransaction(
                    eq(REGISTER_ID), eq(-51.49), eq(postedDate), eq("PUBLIX"))).thenReturn(pending);

            assertSame(pending, bank.getMatchingProvisionalTransaction(posted));
        }
    }

    // ---- helpers ----

    private static SessionController sessionWithRegister() {
        Register register = mock(Register.class);
        when(register.getId()).thenReturn(REGISTER_ID);
        SessionController session = mock(SessionController.class);
        when(session.getRegister()).thenReturn(register);
        return session;
    }

    private static Transaction postedCharge(String merchantPayee, double amount, Calendar date) {
        Transaction posted = mock(Transaction.class);
        when(posted.getMerchantPayee()).thenReturn(merchantPayee);
        when(posted.getAmount()).thenReturn(amount);
        when(posted.getDate()).thenReturn(date);
        return posted;
    }

    private static Calendar day(int year, int month, int dayOfMonth) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month - 1, dayOfMonth);
        return c;
    }
}
