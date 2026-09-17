package com.hixon.financialApp.model.financialinstitution;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link WellsFargoBank#cleanProvisionalPayee}:  web page text copied along with a
 * pending transaction's description is removed.
 */
@DisplayName("Wells Fargo pending payee cleaning")
class WellsFargoProvisionalPayeeTest {

    @Test
    @DisplayName("The 09-17-2026 Amazon return loses the page text glued onto it")
    void removesGluedPageText() {
        assertEquals("PURCH RTN AMAZON MKTPL Amzn.com/bil WA CARD0148",
                WellsFargoBank.cleanProvisionalPayee(
                        "PURCH RTN AMAZON MKTPL Amzn.com/bil WA CARD0148Learn MoreOpens a dialog"));
    }

    @Test
    @DisplayName("Either label on its own, in any case, is removed")
    void removesEitherLabel() {
        assertEquals("PURCHASE SLIM CHICKEN CARD0148",
                WellsFargoBank.cleanProvisionalPayee("PURCHASE SLIM CHICKEN CARD0148 Learn More"));
        assertEquals("PURCHASE SLIM CHICKEN CARD0148",
                WellsFargoBank.cleanProvisionalPayee("PURCHASE SLIM CHICKEN CARD0148 OPENS A DIALOG"));
    }

    @Test
    @DisplayName("An ordinary description is only trimmed")
    void leavesOrdinaryDescriptions() {
        assertEquals("PURCHASE Platinum Hea 194-1927112 FL CARD0148",
                WellsFargoBank.cleanProvisionalPayee("  PURCHASE Platinum Hea  194-1927112 FL CARD0148 "));
        assertEquals("ONLINE TRANSFER TO HIXON D REF #IB0386HSK8 EVERYDAY CHECKING",
                WellsFargoBank.cleanProvisionalPayee("ONLINE TRANSFER TO HIXON D REF #IB0386HSK8 EVERYDAY CHECKING"));
    }

    @Test
    @DisplayName("The 09-17-2026 Amazon return:  PURCH RTN is skipped, leaving the merchant")
    void purchaseReturnPrefixSkipped() {
        assertEquals(2, WellsFargoBank.leadingWordsToSkip("PURCH RTN AMAZON MKTPL".split(" ")));
        assertEquals(2, WellsFargoBank.leadingWordsToSkip("PURCHASE RETURN AMAZON MKTPL".split(" ")));
    }

    @Test
    @DisplayName("The prefixes already skipped still are")
    void existingPrefixes() {
        assertEquals(2, WellsFargoBank.leadingWordsToSkip("BILL PAY Citi AAdvantage Card".split(" ")));
        assertEquals(1, WellsFargoBank.leadingWordsToSkip("PURCHASE SLIM CHICKEN".split(" ")));
        assertEquals(1, WellsFargoBank.leadingWordsToSkip("REVERSAL DELTA AIR".split(" ")));
        assertEquals(0, WellsFargoBank.leadingWordsToSkip("HELLOFRESH".split(" ")));
    }

    @Test
    @DisplayName("A description that is nothing but the prefix keeps its words")
    void prefixOnly() {
        assertEquals(0, WellsFargoBank.leadingWordsToSkip("BILL PAY".split(" ")));
        assertEquals(0, WellsFargoBank.leadingWordsToSkip("PURCHASE".split(" ")));
        assertEquals(0, WellsFargoBank.leadingWordsToSkip("BILL".split(" ")));
        assertEquals(0, WellsFargoBank.leadingWordsToSkip(new String[0]));
        assertEquals(0, WellsFargoBank.leadingWordsToSkip(null));
    }

    @Test
    @DisplayName("A null description stays null")
    void nullStaysNull() {
        assertNull(WellsFargoBank.cleanProvisionalPayee(null));
    }
}
