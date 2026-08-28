package com.hixon.financialApp.model.financialinstitution;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

/**
 * Unit tests for {@link WellsFargoBank#extractUserDescription(String)}.
 *
 * <p>Wells Fargo has no memo field.  It appends the memo the user typed to the transfer
 * description, after the reference code and the account-type name, so extracting it means
 * stripping everything up to and including that account type.  The account type is a phrase
 * anchored at the front of the post-reference tail, and the bank truncates it to a fixed width --
 * both facts these tests pin down, since the previous word-by-word filter got them wrong in two
 * opposite directions: it left longer account types behind, and it ate memo words such as JOINT
 * and SAVINGS that are real budget item names.</p>
 *
 * <p>Every input below is a real payee string taken from the register.  No database is needed --
 * the method uses no instance state, so the institution is mocked with real method bodies rather
 * than constructed.</p>
 */
@DisplayName("Wells Fargo memo extraction")
class WellsFargoBankMemoExtractionTest {

    /** A WellsFargoBank whose real methods run, without the constructor's session/database setup. */
    private final WellsFargoBank bank =
            mock(WellsFargoBank.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));

    /** Asserts the extracted memo for one real payee string. */
    private void assertMemo(String expected, String payee) {
        assertEquals(expected, bank.extractUserDescription(payee),
                "extracting the memo from: " + payee);
    }

    /** Asserts that a payee string yields no memo at all. */
    private void assertNoMemo(String payee) {
        assertNull(bank.extractUserDescription(payee), "expected no memo from: " + payee);
    }

    @Test
    @DisplayName("strips a two-word account type")
    void stripsTwoWordAccountType() {
        assertMemo("RENT", "ONLINE TRANSFER FROM RYBICKI C REF #IB0Z8W2598 EVERYDAY CHECKING RENT");
        assertMemo("DOCTOR", "ONLINE TRANSFER FROM HIXON D REF #IB0Y89FF7K WAY2SAVE SAVINGS DOCTOR");
    }

    @Test
    @DisplayName("strips a long account type, truncated or not")
    void stripsLongAccountType() {
        // Untruncated: this used to come out as "WELLS FARGO CLEAR ACCESS JUSTIN SPENDING MONEY".
        assertMemo("JUSTIN SPENDING MONEY", "ONLINE TRANSFER TO HIXON J REF #IB0YZ8KPC9 "
                + "WELLS FARGO CLEAR ACCESS BANKING JUSTIN SPENDING MONEY");

        // Truncated mid-word by the bank's fixed-width account-type field -- the memo still follows.
        assertMemo("PATIO", "ONLINE TRANSFER TO HIXON J REF #IB0YZ8KPC9 "
                + "WELLS FARGO CLEAR ACCESS BA PATIO");
        assertMemo("DANNI", "ONLINE TRANSFER TO HIXON J REF #IB0YZ8KPC9 "
                + "WELLS FARGO AT WORK CHECKIN DANNI");
    }

    @Test
    @DisplayName("strips the older bare CHECKING account type")
    void stripsBareCheckingAccountType() {
        assertMemo("VACATION", "ONLINE TRANSFER FROM HIXON D REF #IB0792SXMC CHECKING VACATION");
    }

    @Test
    @DisplayName("keeps memo words that are real budget item names")
    void keepsBudgetItemWords() {
        // JOINT and SAVINGS were stopwords, so these arrived as "SPENDING MONEY JSA" and "JSV".
        assertMemo("JOINT SPENDING MONEY JSA", "ONLINE TRANSFER FROM HIXON D REF #IB0ZGXJP6L "
                + "EVERYDAY CHECKING JOINT SPENDING MONEY JSA");
        assertMemo("SAVINGS JSV", "ONLINE TRANSFER FROM HIXON D REF #IB0SS2B87Y "
                + "WAY2SAVE SAVINGS SAVINGS JSV");
    }

    @Test
    @DisplayName("keeps memo words that used to be filtered as bank terminology")
    void keepsMemoWordsThatLookLikeBankTerms() {
        // TRANSFER and FOR were stopwords, which cut this memo down to "COUCH".
        assertMemo("TRANSFER FOR COUCH",
                "ONLINE TRANSFER FROM HIXON D REF #IB07ZJNQYB CHECKING TRANSFER FOR COUCH");
    }

    @Test
    @DisplayName("returns null when the description carries no memo")
    void returnsNullWithoutAMemo() {
        // Without a memo the description ends in the account number and the posting date.
        assertNoMemo("ONLINE TRANSFER TO HIXON D EVERYDAY CHECKING XXXXXX7018 REF #IB0YLYCRJ2 ON 06/21/26");
        assertNoMemo("RECURRING TRANSFER TO HIXON D WAY2SAVE SAVINGS REF #OP06ZG9D34 XXXXXX4442");
    }

    @Test
    @DisplayName("returns null for truncated provisional descriptions")
    void returnsNullForProvisionalTruncation() {
        // The pending feed truncates the description, and the cut always lands before the memo.
        assertNoMemo("ONLINE TRANSFER FROM HIXON D REF");
        assertNoMemo("ONLINE TRANSFER FROM HIXON D");
        assertNoMemo("ONLINE TRANSFER FROM HIXON D REF #IB0ZHVQXST WA");
        assertNoMemo("ONLINE TRANSFER TO HIXON J REF #IB0TPQZL4M WELLS FARGO CLEAR");
    }

    @Test
    @DisplayName("strips a trailing posting date from a real memo")
    void stripsTrailingPostingDate() {
        // The date is the bank's, not the user's, so it must not reach user_description.
        assertMemo("GROCERY",
                "ONLINE TRANSFER FROM HIXON D REF #IB0Z8W2598 EVERYDAY CHECKING GROCERY ON 06/21/26");
    }

    @Test
    @DisplayName("handles null and blank input")
    void handlesNullAndBlank() {
        assertNoMemo(null);
        assertNoMemo("");
        assertNoMemo("   ");
    }
}
