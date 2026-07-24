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
    void handlesNullAndBlank() {
        assertNull(CitiBank.normalizeCitiPayee(null));
        assertEquals("   ", CitiBank.normalizeCitiPayee("   "));
    }
}



