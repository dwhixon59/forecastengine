package com.hixon.financialApp.model.financialinstitution;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

/**
 * Unit tests for reassembling a transaction description from the two OFX fields the bank splits it
 * across.
 *
 * <p>Every NAME/MEMO pair below is copied from a real Wells Fargo statement, and where a CSV-era
 * import of the same transfer exists the expected string is that stored payee, character for
 * character.  That is the point of the exercise:  the memo the user typed sits at the end of the
 * description, so a join that is off by one space loses it.
 */
@DisplayName("QFX NAME/MEMO Join Tests")
public class QfxNameMemoJoinTest {

    /** A WellsFargoBank whose real methods run, without the constructor's session/database setup. */
    private final WellsFargoBank bank =
            mock(WellsFargoBank.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));


    /*
     * Shape 2 -- the bank moved the transfer type into the MEMO and cut the NAME at 32 characters.
     */
    @Test
    @DisplayName("A relocated transfer type is put back, with no space at the cut")
    void testRelocatedTypeRejoinsMidWord() {

        // 09-01-2026, Bill Pay Dave. The NAME is exactly 32 characters and stops inside EVERYDAY.
        String joined = FinancialInstitution.joinNameAndMemo(
                "TO HIXON D REF #OP0ZLPN68K EVERY",
                "RECURRING TRANSFER DAY CHECKING MICHELE ALIMONY DWH");

        // Character for character, the payee the CSV import stored for this same transfer on
        // 01-02-2026 -- back when the whole description arrived in one field.
        assertEquals("RECURRING TRANSFER TO HIXON D REF #OP0ZLPN68K EVERYDAY CHECKING MICHELE ALIMONY DWH",
                joined);
    }

    @Test
    @DisplayName("The rejoined description yields the memo the user typed")
    void testRelocatedTypeYieldsTheUserMemo() {

        // The reason the join matters at all. Split on a space instead and the account-type phrase
        // reads EVERY DAY CHECKING, nothing strips it, and the memo comes out as the whole tail.
        String joined = FinancialInstitution.joinNameAndMemo(
                "TO HIXON D REF #OP0ZLPN68K EVERY",
                "RECURRING TRANSFER DAY CHECKING MICHELE ALIMONY DWH");

        assertEquals("MICHELE ALIMONY DWH", bank.extractUserDescription(joined));
    }


    /*
     * Shape 1 -- the type stays in the NAME and the split falls on a space.
     */
    @Test
    @DisplayName("A description split on a word boundary is rejoined with its space")
    void testWordBoundarySplitKeepsTheSpace() {

        // 08-31-2026, Bill Pay Danni. The NAME is 30 characters: REF would not fit, so it went to
        // the MEMO whole.
        String joined = FinancialInstitution.joinNameAndMemo(
                "ONLINE TRANSFER FROM RYBICKI C",
                "REF #IB0ZKYDNXN EVERYDAY CHECKING GROCERY");

        assertEquals("ONLINE TRANSFER FROM RYBICKI C REF #IB0ZKYDNXN EVERYDAY CHECKING GROCERY", joined);
        assertEquals("GROCERY", bank.extractUserDescription(joined));
    }

    @Test
    @DisplayName("The two sides of one transfer both yield the same memo")
    void testBothSidesOfTheRentTransfer() {

        // 09-01-2026: $1,700 left Bill Pay Danni and arrived in Bill Pay Dave. The two statements
        // describe it differently and both have to produce RENT, since the history is keyed on the
        // memo and would otherwise learn two different things from one movement of money.
        String sent = FinancialInstitution.joinNameAndMemo(
                "ONLINE TRANSFER TO HIXON D REF", "#IB032G7QWG EVERYDAY CHECKING RENT");
        String received = FinancialInstitution.joinNameAndMemo(
                "ONLINE TRANSFER FROM HIXON D REF", "#IB032G7QWG EVERYDAY CHECKING RENT");

        assertEquals("RENT", bank.extractUserDescription(sent));
        assertEquals("RENT", bank.extractUserDescription(received));
    }

    @Test
    @DisplayName("A masked account number in the MEMO is kept, as it always was")
    void testMaskedAccountNumberInTheMemoIsKept() {

        // This is what the NAME+MEMO join was originally added for: the counterparty account number
        // that identifies the far register lives in the MEMO.
        assertEquals("ONLINE TRANSFER FROM HIXON D WAY2SAVE SAVINGS XXXXXX4442 REF #IB0ZKYDRXQ ON 08/30/26",
                FinancialInstitution.joinNameAndMemo(
                        "ONLINE TRANSFER FROM HIXON D",
                        "WAY2SAVE SAVINGS XXXXXX4442 REF #IB0ZKYDRXQ ON 08/30/26"));
    }


    /*
     * What must not be joined:
     */
    @Test
    @DisplayName("An ordinary purchase keeps the bank's annotation out of its payee")
    void testPurchaseMemoIsNotAppended() {

        // The MEMO here is the bank describing the charge, not the rest of the payee. Appending it
        // would break every merchant lookup in the register.
        assertEquals("AMAZON MKTPL*5O14E", FinancialInstitution.joinNameAndMemo(
                "AMAZON MKTPL*5O14E", "PURCHASE 08/26 Amzn.com/bill WA CARD 0148"));
        assertEquals("PUBLIX #1553", FinancialInstitution.joinNameAndMemo(
                "PUBLIX #1553", "PURCHASE 08/29 BRADENTON FL CARD 0148"));
    }

    @Test
    @DisplayName("A recurring card payment is not mistaken for a recurring transfer")
    void testRecurringPaymentIsNotATransfer() {

        // RECURRING PAYMENT is a card charge; only RECURRING TRANSFER relocates a description.
        assertEquals("HELLOFRESH", FinancialInstitution.joinNameAndMemo(
                "HELLOFRESH", "RECURRING PAYMENT 08/28 347-200-0291 NY CARD 0148"));
    }

    @Test
    @DisplayName("A short NAME with a transfer type in the MEMO is left alone")
    void testCompleteMemoPhraseIsNotGluedOn() {

        // 'SAVE AS YOU GO TRANSFER DEBIT' is a complete phrase describing the transfer, not the tail
        // of a cut description -- and the 18-character NAME says so, since a hard cut would have
        // left 32. Gluing it on would rewrite a payee whose masked account number already resolves
        // the far register.
        assertEquals("TO XXXXXXXXXXX4442", FinancialInstitution.joinNameAndMemo(
                "TO XXXXXXXXXXX4442", "SAVE AS YOU GO TRANSFER DEBIT"));
    }

    @Test
    @DisplayName("A missing or empty memo leaves the name as it is")
    void testNoMemo() {

        assertEquals("SELENE FINANCE PAYMENTS",
                FinancialInstitution.joinNameAndMemo("SELENE FINANCE PAYMENTS", null));
        assertEquals("SELENE FINANCE PAYMENTS",
                FinancialInstitution.joinNameAndMemo("SELENE FINANCE PAYMENTS", "   "));
        assertNull(FinancialInstitution.joinNameAndMemo(null, "RECURRING TRANSFER DAY CHECKING"));
    }
}
