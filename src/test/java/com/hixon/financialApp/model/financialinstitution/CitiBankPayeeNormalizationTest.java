package com.hixon.financialApp.model.financialinstitution;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link CitiBank#normalizeCitiPayee(String)}.
 *
 * <p>Citi credit-card descriptors append store numbers, reference/phone numbers, and a truncated
 * city/state to the merchant name.  These tests use real examples from an actual Citi QFX import to
 * verify that normalization produces a stable merchant name for matching.</p>
 */
class CitiBankPayeeNormalizationTest {

    @Test
    void stripsStoreNumberAndTrailingLocation() {
        assertEquals("THE HOME DEPOT", CitiBank.normalizeCitiPayee("THE HOME DEPOT #1863 BRADEN"));
    }

    @Test
    void stripsReferenceNumberAndTrailingLocation() {
        assertEquals("ATM AMSCOT", CitiBank.normalizeCitiPayee("ATM AMSCOT 2-K205051 BRADEN"));
    }

    @Test
    void stripsTrailingPhoneOrReferenceNumber() {
        assertEquals("MANATEECOUNTYUTIL", CitiBank.normalizeCitiPayee("MANATEECOUNTYUTIL 800420166"));
    }

    @Test
    void keepsLeadingLettersOfReferenceTokenAndDropsLocation() {
        assertEquals("ADT SECURITY", CitiBank.normalizeCitiPayee("ADT SECURITY*320925392 BOCA"));
    }

    @Test
    void dropsStrayShortLeadingLetterOfReferenceCode() {
        // The "P" in "P3E6A1283E" is part of the reference code, not the merchant name.
        assertEquals("Spotify", CitiBank.normalizeCitiPayee("Spotify P3E6A1283E New York"));
    }

    @Test
    void stripsTrailingCityAndState() {
        // The ".COM"/standalone "COM" domain fragments are stripped along with the city and state.
        assertEquals("VXNBILL", CitiBank.normalizeCitiPayee("VXNBILL.COM CAMDEN DE"));
        assertEquals("HEADSHOP", CitiBank.normalizeCitiPayee("HEADSHOP COM ENCINITAS CA"));
    }

    @Test
    void stripsDomainSuffixFromMerchantToken() {
        // Even without a city lookup, the ".COM" suffix is removed so "NETFLIX" can match the merchant.
        // With no multi-word city lookup, only a single trailing city token is stripped as a fallback.
        assertEquals("NETFLIX LOS", CitiBank.normalizeCitiPayee("NETFLIX.COM LOS GATOS CA",
                (city, state) -> false));
    }

    @Test
    void stripsMultiWordCityUsingCityLookup() {
        // "LOS GATOS" is a real city in CA; with the lookup it is fully stripped, leaving "NETFLIX".
        CitiBank.CityStateLookup lookup =
                (city, state) -> city.equalsIgnoreCase("LOS GATOS") && state.equalsIgnoreCase("CA");
        assertEquals("NETFLIX", CitiBank.normalizeCitiPayee("NETFLIX.COM LOS GATOS CA", lookup));
    }

    @Test
    void leavesCleanPayeeUnchanged() {
        assertEquals("Payment Received WELLS FARG",
                CitiBank.normalizeCitiPayee("Payment Received WELLS FARG"));
    }

    @Test
    void doesNotStripCityWhenItWouldRemoveTheOnlyMerchantToken() {
        // Only "APPLE" would remain after removing the state, so the city token must be kept.
        assertEquals("APPLE", CitiBank.normalizeCitiPayee("APPLE CA"));
    }

    @Test
    void collapsesExtraWhitespace() {
        assertEquals("THE HOME DEPOT", CitiBank.normalizeCitiPayee("  THE   HOME  DEPOT  #1863   BRADEN "));
    }

    @Test
    void fallsBackToOriginalWhenNormalizationEmptiesString() {
        // A descriptor that is nothing but a reference number falls back to the original.
        assertEquals("800420166", CitiBank.normalizeCitiPayee("800420166"));
    }

    @Test
    void stripsTrailingCountryAndCityFromAPendingRow() {
        // The portal writes a pending charge's location as "IRVINE USA" where the QFX row for the
        // same charge writes "IRVINE CA".  Both have to reduce to the same merchant.
        assertEquals("LA FITNESS", CitiBank.normalizeCitiPayee("LA FITNESS IRVINE USA"));
        assertEquals("LA FITNESS", CitiBank.normalizeCitiPayee("LA FITNESS IRVINE CA"));
    }

    @Test
    void stripsTrailingBareCountryCode() {
        assertEquals("LA FITNESS", CitiBank.normalizeCitiPayee("LA FITNESS IRVINE US"));
    }

    @Test
    void stripsAMultiWordCityBeforeATrailingCountry() {
        // A country names no state, so the multi-word city is looked up in any state.  Pending and
        // posted rows for the same charge must reduce to the same merchant.
        CitiBank.CityStateLookup lookup = (city, state) -> city.equalsIgnoreCase("LOS GATOS")
                && (state == null || state.equalsIgnoreCase("CA"));
        assertEquals("Netflix", CitiBank.normalizeCitiPayee("Netflix.com Los Gatos USA", lookup));
        assertEquals("Netflix", CitiBank.normalizeCitiPayee("Netflix.com Los Gatos CA", lookup));
    }

    @Test
    void pendingAndPostedRowsFromThe0923PasteReduceAlike() {
        // Pending and posted descriptors of the same charges, from the 09-23 portal paste.  The cities
        // table spells it "St. Louis", so "SAINT LOUIS" is not a known city and both forms lose only
        // "LOUIS" -- consistently, which is what the pending-to-posted match needs.
        CitiBank.CityStateLookup lookup = (city, state) -> false;
        assertEquals(CitiBank.normalizeCitiPayee("Spectrum SAINT LOUIS MO", lookup),
                CitiBank.normalizeCitiPayee("Spectrum SAINT LOUIS USA", lookup));
        assertEquals("PEACE RIVER ELECTRIC",
                CitiBank.normalizeCitiPayee("PEACE RIVER ELECTRIC WAUCHULA USA", lookup));
    }

    @Test
    void leavesACountryCodeThatIsNotTheLastTokenAlone() {
        // The country rule looks only at the last token, so the "USA" in the middle of this descriptor
        // survives and the state rule takes the trailing "New York NY" as it always has.  The result
        // is unchanged from before the country rule existed.
        CitiBank.CityStateLookup lookup =
                (city, state) -> city.equalsIgnoreCase("NEW YORK") && state.equalsIgnoreCase("NY");
        assertEquals("Spotify USA", CitiBank.normalizeCitiPayee("Spotify USA New York NY", lookup));
    }

    @Test
    void doesNotStripCountryWhenItWouldRemoveTheOnlyMerchantToken() {
        // Dropping "USA" and the token before it would leave nothing, so both are kept.
        assertEquals("NETFLIX USA", CitiBank.normalizeCitiPayee("NETFLIX USA"));
    }

    @Test
    void handlesNullAndBlank() {
        assertNull(CitiBank.normalizeCitiPayee(null));
        assertEquals("   ", CitiBank.normalizeCitiPayee("   "));
    }
}



