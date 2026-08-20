package com.hixon.financialApp.utility;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link StoreNumberStripper}.
 *
 * <p>Every input below is a real merchant payee taken from the application's {@code merchant_payee}
 * table, so the tests document what the change actually does to the existing data.</p>
 */
class StoreNumberStripperTest {

    /**
     * The merchants in the real database whose names contain a digit.  These are exactly the names
     * that must protect a number from being stripped.
     */
    private static final List<String> MERCHANT_NAMES_WITH_DIGITS = List.of(
            "124 Breakfast Lunch & More", "1700 South Cafe", "1password", "365 Retail Markets",
            "5th Avenue Empire Cafe Inc.", "7 Brew Coffee", "7-Eleven", "76", "Clearwater 7 Inc",
            "Home2 Suites", "Law District 1844731", "N1 Hvolsvollur", "One9 Travel Center",
            "Pier 1 Imports", "Pier 60", "Pub 32 Irish Gastropub", "ROW8", "Seasons 52");

    /** Lookup backed by the real merchant names above. */
    private static final StoreNumberStripper.MerchantNameLookup LOOKUP = candidate -> {
        String needle = candidate.toLowerCase(Locale.ROOT);
        return MERCHANT_NAMES_WITH_DIGITS.stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .anyMatch(name -> name.equals(needle) || name.startsWith(needle + " "));
    };

    private static String strip(String payee) {
        return StoreNumberStripper.strip(payee, LOOKUP);
    }

    /*
     * The core case: a trailing store number.
     */

    @Test
    void stripsTrailingStoreNumber() {
        assertEquals("STARBUCKS", strip("STARBUCKS 15"));
        assertEquals("TACO BELL", strip("TACO BELL 53"));
        assertEquals("RACETRAC", strip("RACETRAC 126"));
        assertEquals("CHUCK E CHEESE", strip("CHUCK E CHEESE 420"));
        assertEquals("MICROSOFT", strip("MICROSOFT 3"));
    }

    @Test
    void stripsStoreLabelAlongWithTheNumber() {
        assertEquals("STARBUCKS", strip("STARBUCKS STORE 02"));
        assertEquals("LE CREUSET", strip("LE CREUSET STORE 1"));
    }

    @Test
    void stripsHashPrefixedStoreNumber() {
        assertEquals("CHICK-FIL-A", strip("CHICK-FIL-A #03238"));
        assertEquals("PUBLIX", strip("PUBLIX #1553"));
        assertEquals("THE HOME DEPOT", strip("THE HOME DEPOT #1863 BRADEN"));
        assertEquals("CRACKER BARREL", strip("CRACKER BARREL #73 BRA"));
    }

    @Test
    void stripsLocationNoiseFollowingTheStoreNumber() {
        assertEquals("STARBUCKS", strip("STARBUCKS STORE 08 PALM"));
        assertEquals("CIRCLE K", strip("CIRCLE K 075 PLANT CITY USA"));
        assertEquals("THE FRESH MARKET", strip("THE FRESH MARKET 256 LAKEWOOD RANC FL"));
        assertEquals("SURF STYLE", strip("SURF STYLE 840 MIAMI"));
    }

    @Test
    void collapsesEveryVariantOfABrandToOnePayee() {
        // The nine "STARBUCKS ..." payees in the database all normalize to the same merchant payee.
        for (String variant : List.of("STARBUCKS 15", "STARBUCKS 66", "STARBUCKS 80",
                "STARBUCKS STORE 03 TARPON", "STARBUCKS STORE 26", "STARBUCKS STORE 51 BONITA",
                "STARBUCKS STORE 58 THE", "STARBUCKS STORE 66")) {
            assertEquals("STARBUCKS", strip(variant), "failed for: " + variant);
        }
    }

    /*
     * Numbers glued onto the merchant name.
     */

    @Test
    void splitsStoreNumberGluedOntoTheName() {
        assertEquals("RACETRAC", strip("RACETRAC2381"));
        assertEquals("HOTELSCOM", strip("HOTELSCOM919614895"));
        assertEquals("MAVIS", strip("MAVIS01232"));
        assertEquals("BESTBUYCOM", strip("BESTBUYCOM80619786"));
        assertEquals("MURPHY", strip("MURPHY7497ATWAL"));
        assertEquals("LINKEDIN", strip("LINKEDIN-600"));
    }

    @Test
    void keepsTheWordsAfterANumberInTheLeadingToken() {
        // Nothing precedes the number, so it is a processor prefix rather than a store number:
        // the words after it are still part of the merchant name and must survive.
        assertEquals("MEN MENCHIES UN", strip("MEN629 MENCHIES UN"));
        assertEquals("PPC EE Dir Dep", strip("PPC8013 EE Dir Dep"));
    }

    @Test
    void splitsHashSeparatedStoreNumberGluedOntoTheName() {
        assertEquals("BP", strip("BP#6984843SUNSH"));
        assertEquals("AMOCO", strip("AMOCO#1153900OS"));
    }

    @Test
    void keepsAGluedFragmentThatStartsWithARealWord() {
        // "ONE9" is the merchant ("One9 Travel Center"); "00089" is the store number.
        assertEquals("ONE9", strip("ONE9_00089"));
    }

    @Test
    void dropsAGluedFragmentThatIsPartOfAReferenceCode() {
        // The "P" in "P3E6A1283E" and the "F" in "F19080" are reference codes, not merchant names.
        assertEquals("Spotify", strip("Spotify P3E6A1283E"));
        assertEquals("MCDONALD'S", strip("MCDONALD'S F19080 SARASOTA FL"));
        assertEquals("ATM AMSCOT", strip("ATM AMSCOT 2-K205051"));
        assertEquals("AA WIFI", strip("AA WIFI 1-888-649-6711"));
    }

    @Test
    void keepsAMultiLetterFragmentOfAReferenceToken() {
        assertEquals("ADT SECURITY", strip("ADT SECURITY*320925392"));
        assertEquals("AMAZON MKTPL", strip("AMAZON MKTPL*6241O71L3"));
    }

    /*
     * Numbers that are part of the merchant's real name must survive.
     */

    @Test
    void keepsANumberThatIsPartOfTheMerchantName() {
        assertEquals("SEASONS 52", strip("SEASONS 52"));
        assertEquals("PUB 32", strip("PUB 32"));
        assertEquals("CLEARWATER 7 INC", strip("CLEARWATER 7 INC"));
        assertEquals("HOME2 SUITES", strip("HOME2 SUITES"));
        assertEquals("7-ELEVEN", strip("7-ELEVEN"));
    }

    @Test
    void stripsTheStoreNumberButKeepsTheNumberInTheName() {
        // "1" belongs to "Pier 1 Imports"; "494" is the store number, and "FORT WORTH TX" is location.
        assertEquals("PIER 1 IMPORTS", strip("PIER 1 IMPORTS 494 FORT WORTH TX"));
        // "52" belongs to "Seasons 52"; the trailing "0" is the store number.
        assertEquals("SEASONS 52", strip("SEASONS 52 0"));
    }

    @Test
    void stripsANumberWhenTheMerchantNameDoesNotContainIt() {
        // The merchant is "Lazy Flamingo", so the trailing "2" is a location number.
        assertEquals("LAZY FLAMINGO", strip("LAZY FLAMINGO 2"));
        assertEquals("WONDERTREE", strip("WONDERTREE 1974"));
    }

    @Test
    void keepsAPayeeThatIsEntirelyAKnownNumberedMerchantName() {
        assertEquals("Law District 1844731", strip("Law District 1844731"));
    }

    /*
     * Transfers and other payees that must not be touched.
     */

    @Test
    void leavesTransferPayeesAlone() {
        assertEquals("Transfer to XXXXXX8249 from Bill Pay Account",
                strip("Transfer to XXXXXX8249 from Bill Pay Account"));
        assertEquals("Transfer from Joint Savings Account to Bill Pay Account",
                strip("Transfer from Joint Savings Account to Bill Pay Account"));
        assertEquals("ONLINE TRANSFER FROM HIXON D REF #IB0VM5S5WL EV",
                strip("ONLINE TRANSFER FROM HIXON D REF #IB0VM5S5WL EV"));
    }

    @Test
    void leavesAPayeeThatStartsWithItsNumberAlone() {
        // Stripping would empty the payee, so it is kept as-is.
        assertEquals("365 Retail Markets", strip("365 Retail Markets"));
        assertEquals("1011PEPSIVEN914767", strip("1011PEPSIVEN914767"));
        assertEquals("800420166", strip("800420166"));
    }

    @Test
    void leavesAPayeeWithoutAStoreNumberAlone() {
        assertEquals("BURLINGTON STORES", strip("BURLINGTON STORES"));
        assertEquals("Payment Received WELLS FARG", strip("Payment Received WELLS FARG"));
        assertEquals("AA 25% INFLIGHT CREDIT", strip("AA 25% INFLIGHT CREDIT"));
        assertEquals("AMZN Mktp US*M62VV", strip("AMZN Mktp US*M62VV"));
        assertEquals("N1 Hvolsvollur", strip("N1 Hvolsvollur"));
        assertEquals("CHEVRON/41 EXPRESS", strip("CHEVRON/41 EXPRESS"));
    }

    /*
     * Housekeeping.
     */

    @Test
    void collapsesExtraWhitespaceOnlyWhenSomethingIsStripped() {
        assertEquals("TACO BELL", strip("  TACO   BELL   53 "));
        // A payee with no store number is returned byte-for-byte so its existing merchant_payee
        // row keeps matching - even when it contains doubled or trailing whitespace.
        assertEquals("AFFIRM  PAY", strip("AFFIRM  PAY"));
        assertEquals("STATE FARM  INSURANCE", strip("STATE FARM  INSURANCE"));
        assertEquals("TARGET.COM ", strip("TARGET.COM "));
    }

    @Test
    void handlesNullAndBlank() {
        assertNull(strip(null));
        assertEquals("   ", strip("   "));
    }

    @Test
    void worksWithoutAMerchantLookup() {
        // With no lookup (e.g. no database connection) numbers are stripped unconditionally.
        assertEquals("STARBUCKS", StoreNumberStripper.strip("STARBUCKS 15", null));
        assertEquals("PIER", StoreNumberStripper.strip("PIER 1 IMPORTS 494", null));
    }
}
