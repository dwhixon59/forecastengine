package com.hixon.financialApp.model.register;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for telling a line of credit apart from an account that holds money.
 *
 * <p>The forecast summary reads a negative balance as trouble, which is right for a checking
 * account and meaningless for a credit card, where the balance owed is the point of the account.
 * Applied to the Citi register on 09-04-2026 the report argued with itself -- "You have sufficient
 * float to ensure no negative balances" printed directly beneath "Lowest projected balance is
 * $-14,321" -- and asked the user to hold contingency float against a "negative-balance risk" that
 * was just the card's balance.
 *
 * <p>{@code account_type} is free text in the database, so the test pins the spellings that are
 * actually in it as well as the boundaries.
 */
@DisplayName("Credit Line Register Tests")
class CreditLineRegisterTest {

    private static Register withAccountType(String accountType) {
        Register register = new Register();
        register.setAccountType(accountType);
        return register;
    }

    @Test
    @DisplayName("The account type in the database is recognised")
    void testCreditCardIsACreditLine() {

        // 'Credit Card' is the exact string the Citi register carries.
        assertTrue(withAccountType("Credit Card").isCreditLine());
        assertTrue(withAccountType(Register.CREDIT_CARD).isCreditLine());
    }

    @Test
    @DisplayName("Accounts that hold money are not credit lines")
    void testAssetAccountsAreNotCreditLines() {

        // The float, runway and negative-balance analysis must keep working for these -- the change
        // that suppresses it is only safe if it never fires on an account where it belongs.
        assertFalse(withAccountType(Register.CHECKING).isCreditLine());
        assertFalse(withAccountType(Register.SAVINGS).isCreditLine());
        assertFalse(withAccountType("Checking").isCreditLine());
        assertFalse(withAccountType("Savings").isCreditLine());
    }

    @Test
    @DisplayName("Capitalisation of the stored account type does not decide the answer")
    void testMatchIsCaseInsensitive() {

        // account_type is free text written by whatever created the register, and the budget items
        // in this same database already show how easily two spellings of one word appear.
        assertTrue(withAccountType("credit card").isCreditLine());
        assertTrue(withAccountType("CREDIT CARD").isCreditLine());
    }

    @Test
    @DisplayName("An unset or unknown account type is treated as an asset account")
    void testUnknownTypeFallsBackToAssetBehaviour() {

        // The safe direction:  an unrecognised type keeps the full analysis rather than silently
        // dropping sections of a report the user relies on.  Suppressing is the exception, and it
        // has to be asked for by name.
        assertFalse(withAccountType(null).isCreditLine(), "no account type must not throw");
        assertFalse(withAccountType("").isCreditLine());
        assertFalse(withAccountType("Line of Credit").isCreditLine(),
                "a spelling the code does not know keeps the existing behaviour");
    }
}
