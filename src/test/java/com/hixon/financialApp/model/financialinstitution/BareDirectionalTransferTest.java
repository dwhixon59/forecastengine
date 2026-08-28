package com.hixon.financialApp.model.financialinstitution;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link WellsFargoBank#isBareDirectionalTransfer(String[])}.
 *
 * <p>Wells Fargo writes SAVE AS YOU GO transfers as a bare {@code TO XXXXXXXXXXX4442}: the NAME
 * field carries only the direction and the counterparty account, and the transfer type sits in the
 * MEMO. Without recognising that shape the payee matches no case in {@code parseMerchantPayee},
 * falls through to the default, and collapses to the single word {@code "TO"} — a key that
 * identifies nothing and that every such transfer then shares.
 *
 * <p>The negative tests carry the weight: every transfer shape that already parses correctly must
 * keep going down its existing path, because this predicate redirects whatever it claims.
 */
@DisplayName("Bare Directional Transfer Tests")
public class BareDirectionalTransferTest {

    private static boolean isBare(String payee) {
        return WellsFargoBank.isBareDirectionalTransfer(payee.split(" "));
    }

    @Test
    @DisplayName("A bare TO followed by a masked account is a transfer")
    void testBareToIsATransfer() {
        // The real payee from the SAVE AS YOU GO transfers into Joint Savings Account.
        assertTrue(isBare("TO XXXXXXXXXXX4442"));
    }

    @Test
    @DisplayName("A bare FROM followed by a masked account is a transfer")
    void testBareFromIsATransfer() {
        assertTrue(isBare("FROM XXXXXX7394"));
    }

    @Test
    @DisplayName("Detection is case-insensitive on both the direction and the mask")
    void testCaseInsensitive() {
        assertTrue(isBare("to XXXXXXXXXXX4442"));
        assertTrue(isBare("To xxxxxxxxxxx4442"));
    }

    @Test
    @DisplayName("Trailing tokens after the account number do not stop it being recognised")
    void testTrailingTokens() {
        assertTrue(isBare("TO XXXXXXXXXXX4442 SAVE AS YOU GO TRANSFER DEBIT"));
    }

    @Test
    @DisplayName("A keyword transfer is NOT redirected - it already parses correctly")
    void testKeywordTransfersAreLeftAlone() {
        // These reach the transfer branch through their own switch cases and read the direction
        // from token 2 or token 5. Claiming them here would change how their direction is read.
        assertFalse(isBare("ONLINE TRANSFER TO HIXON D EVERYDAY CHECKING XXXXXX7394 REF #IB0ZFB4JC5"));
        assertFalse(isBare("RECURRING TRANSFER TO HIXON D EVERYDAY CHECKING XXXXXX8355"));
        assertFalse(isBare("ATM TRANSFER AUTHORIZED ON 08/18 TO XXXXXX4442"));
    }

    @Test
    @DisplayName("A direction word followed by something that is not an account number is not a transfer")
    void testDirectionWithoutAnAccountNumber() {
        // This is the ambiguous "HIXON D" family - a person, not an account. Routing it to the
        // transfer branch would invent a counterparty the payee never named.
        assertFalse(isBare("TO HIXON D REF #OP0ZD29XLK EVERYDAY CHECKING"));
        assertFalse(isBare("TO HIXON J WELLS FARGO CLEAR ACCESS"));
    }

    @Test
    @DisplayName("An ordinary purchase is untouched")
    void testOrdinaryPurchase() {
        assertFalse(isBare("PURCHASE AUTHORIZED ON 08/13 JERSEY MIKES 13092 LAKEWOOD RANC FL"));
        assertFalse(isBare("ANTHROPIC* CLAUDE"));
    }

    @Test
    @DisplayName("A mask must have at least four X's and exactly four trailing digits")
    void testMaskShape() {
        assertFalse(isBare("TO XXX4442"));        // too few X's
        assertFalse(isBare("TO XXXXXXXX44"));     // too few digits
        assertFalse(isBare("TO 4442"));           // no mask at all
    }

    @Test
    @DisplayName("A short or absent payee is handled without blowing up")
    void testDegenerateInput() {
        // The whole point of this shape is that it has only two tokens, so index safety matters.
        assertFalse(WellsFargoBank.isBareDirectionalTransfer(null));
        assertFalse(WellsFargoBank.isBareDirectionalTransfer(new String[]{}));
        assertFalse(WellsFargoBank.isBareDirectionalTransfer(new String[]{"TO"}));
    }
}
