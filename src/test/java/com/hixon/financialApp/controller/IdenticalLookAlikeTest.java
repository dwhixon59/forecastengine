package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.Transaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ImportController#findIdenticalLookAlike}:  a re-downloaded charge described exactly as
 * the register already has it is recognised without asking.
 */
@DisplayName("Identical look-alike Tests")
class IdenticalLookAlikeTest {

    private static Transaction held(String payee, String importRecordId) {
        Register register = mock(Register.class);
        when(register.getId()).thenReturn(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        Calendar date = Calendar.getInstance();
        date.set(2026, Calendar.SEPTEMBER, 11, 0, 0, 0);
        return new Transaction(register, date, payee, -24.16, true, 0, importRecordId);
    }

    @Test
    @DisplayName("The 09-17-2026 Spotify charge matches the one already held")
    void identicalDescription() {
        Transaction spotify = held("Spotify USA New York NY", "20260911090004");

        assertSame(spotify, ImportController.findIdenticalLookAlike(List.of(spotify), "Spotify USA New York NY"));
    }

    @Test
    @DisplayName("Case and spacing do not matter")
    void caseAndSpacing() {
        Transaction spotify = held("Spotify USA New York NY", "20260911090004");

        assertSame(spotify, ImportController.findIdenticalLookAlike(List.of(spotify), "  SPOTIFY USA  NEW YORK NY "));
    }

    @Test
    @DisplayName("A renamed charge is not identical, so the user is still asked")
    void renamedIsNotIdentical() {
        Transaction payment = held("PAYMENT THANK YOU", "20260912090005");

        assertNull(ImportController.findIdenticalLookAlike(List.of(payment), "ONLINE PAYMENT, THANK YOU"));
    }

    @Test
    @DisplayName("The identical one is found among several held for the date and amount")
    void amongSeveral() {
        Transaction other = held("VXNBILL.COM CAMDEN DE", "A");
        Transaction spotify = held("Spotify USA New York NY", "B");

        assertSame(spotify, ImportController.findIdenticalLookAlike(List.of(other, spotify),
                "Spotify USA New York NY"));
    }

    @Test
    @DisplayName("Nothing held, or no description, matches nothing")
    void nothing() {
        assertNull(ImportController.findIdenticalLookAlike(List.of(), "Spotify USA New York NY"));
        assertNull(ImportController.findIdenticalLookAlike(List.of(held("", "A")), "  "));
        assertNull(ImportController.findIdenticalLookAlike(List.of(held("Spotify", "A")), null));
    }
}
