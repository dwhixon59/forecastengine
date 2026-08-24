package com.hixon.financialApp.model.merchant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MerchantUtilities#isUnidentifiedTransferPayee(String)}.
 *
 * <p>A transfer payee whose counterparty could not be named is not an identity -- every future
 * unidentifiable transfer out of the same register produces the identical string. Nothing may be
 * cached against it. The tests that matter most are the negative ones: a payee that <em>does</em>
 * name its counterparty must keep being remembered, or every transfer starts asking again.
 */
@DisplayName("Unidentified Transfer Payee Tests")
public class UnidentifiedTransferPayeeTest {

    @Test
    @DisplayName("An empty counterparty name means the transfer could not be identified")
    void testEmptyCounterpartyIsUnidentified() {
        // Produced when the payee carried no account number and resolveUnmatchedAccount could not
        // settle it either. Note the double space where the register name should be.
        assertTrue(MerchantUtilities.isUnidentifiedTransferPayee("Transfer to  from Bill Pay Dave"));
        assertTrue(MerchantUtilities.isUnidentifiedTransferPayee("Transfer from  to Bill Pay Account"));
    }

    @Test
    @DisplayName("Detection does not depend on the exact amount of whitespace")
    void testWhitespaceTolerance() {
        assertTrue(MerchantUtilities.isUnidentifiedTransferPayee("Transfer to from Bill Pay Danni"));
        assertTrue(MerchantUtilities.isUnidentifiedTransferPayee("Transfer to    from Bill Pay Danni"));
    }

    @Test
    @DisplayName("A payee that names its counterparty register is identified, and stays cacheable")
    void testNamedRegisterIsIdentified() {
        // This is the normal case and by far the most common. If it were ever misread as
        // unidentified, every transfer in the application would start asking again.
        assertFalse(MerchantUtilities.isUnidentifiedTransferPayee(
                "Transfer to Danni's Spending Account from Bill Pay Dave"));
        assertFalse(MerchantUtilities.isUnidentifiedTransferPayee(
                "Transfer from Bill Pay Danni to Bill Pay Dave"));
    }

    @Test
    @DisplayName("A masked account number is a perfectly good identity, even with no register behind it")
    void testMaskedAccountIsIdentified() {
        // No register matches these digits, but the account number always means the same external
        // account, so remembering a merchant against it is correct.
        assertFalse(MerchantUtilities.isUnidentifiedTransferPayee(
                "Transfer to XXXXXX8249 from Bill Pay Danni"));
    }

    @Test
    @DisplayName("A register whose name begins with 'from' or 'to' is not mistaken for an empty one")
    void testRegisterNameStartingWithKeyword() {
        assertFalse(MerchantUtilities.isUnidentifiedTransferPayee(
                "Transfer to Fromage Fund from Bill Pay Dave"));
        assertFalse(MerchantUtilities.isUnidentifiedTransferPayee(
                "Transfer from Toledo Savings to Bill Pay Dave"));
    }

    @Test
    @DisplayName("Ordinary non-transfer payees are unaffected")
    void testNonTransferPayees() {
        assertFalse(MerchantUtilities.isUnidentifiedTransferPayee("Jersey Mike's Subs"));
        assertFalse(MerchantUtilities.isUnidentifiedTransferPayee("ANTHROPIC* CLAUDE"));
        assertFalse(MerchantUtilities.isUnidentifiedTransferPayee(null));
        assertFalse(MerchantUtilities.isUnidentifiedTransferPayee(""));
    }

    @Test
    @DisplayName("A transfer with no source register either is still unidentified, not a crash")
    void testTruncatedPayee() {
        assertFalse(MerchantUtilities.isUnidentifiedTransferPayee("Transfer to  from "));
        assertFalse(MerchantUtilities.isUnidentifiedTransferPayee("Transfer to"));
    }
}
