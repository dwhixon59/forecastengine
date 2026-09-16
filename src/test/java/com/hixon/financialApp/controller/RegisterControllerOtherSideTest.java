package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.view.base.ViewInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Calendar;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link RegisterController#registersHoldingOtherSide}, which lets a transfer with no account number be
 * matched to the register already holding its other side:  the same date, exactly the opposite amount.
 */
@DisplayName("Transfer other-side register matching Tests")
class RegisterControllerOtherSideTest {

    private Register billPayDanni;
    private Register christiansChecking;
    private Register joint;
    private RegisterController controller;
    private final Calendar date = Calendar.getInstance();

    private static Register register(String name) {
        Register register = mock(Register.class);
        when(register.getId()).thenReturn(UUID.randomUUID());
        when(register.getName()).thenReturn(name);
        return register;
    }

    @BeforeEach
    void setUp() {
        billPayDanni = register("Bill Pay Danni");
        christiansChecking = register("Christian's Checking Account");
        joint = register("Joint Savings Account");

        SessionController session = mock(SessionController.class);
        when(session.getRegister()).thenReturn(billPayDanni);
        when(session.getView()).thenReturn(mock(ViewInt.class));
        controller = new RegisterController(session);
    }

    /** Stubs the other-side lookup so that only the given registers hold a candidate. */
    private void holders(MockedStatic<Transaction> transactions, Register... holding) {
        transactions.when(() -> Transaction.findOppositeSideInRegister(any(), anyDouble(), any(), anyInt()))
                .thenReturn(List.of());
        for (Register register : holding) {
            transactions.when(() -> Transaction.findOppositeSideInRegister(eq(register.getId()), anyDouble(), any(), eq(0)))
                    .thenReturn(List.of(mock(Transaction.class)));
        }
    }

    @Test
    @DisplayName("The 09-15-2026 transfer:  only Christian's Checking holds the other side, so it is the answer")
    void singleRegisterHoldingOtherSide_isReturned() {
        try (MockedStatic<Register> registers = Mockito.mockStatic(Register.class);
             MockedStatic<Transaction> transactions = Mockito.mockStatic(Transaction.class)) {
            registers.when(Register::getListOf).thenReturn(List.of(billPayDanni, christiansChecking, joint));
            holders(transactions, christiansChecking);

            assertEquals(Set.of(christiansChecking), controller.registersHoldingOtherSide(date, 40.00));
        }
    }

    @Test
    @DisplayName("The search is for exactly the opposite amount on the same date")
    void searchIsExactOppositeOnSameDate() {
        try (MockedStatic<Register> registers = Mockito.mockStatic(Register.class);
             MockedStatic<Transaction> transactions = Mockito.mockStatic(Transaction.class)) {
            registers.when(Register::getListOf).thenReturn(List.of(billPayDanni, christiansChecking));
            holders(transactions);

            controller.registersHoldingOtherSide(date, 40.00);

            transactions.verify(() -> Transaction.findOppositeSideInRegister(christiansChecking.getId(), 40.00, date, 0));
        }
    }

    @Test
    @DisplayName("No register holding the other side gives no match")
    void noRegisterHoldingOtherSide_isEmpty() {
        try (MockedStatic<Register> registers = Mockito.mockStatic(Register.class);
             MockedStatic<Transaction> transactions = Mockito.mockStatic(Transaction.class)) {
            registers.when(Register::getListOf).thenReturn(List.of(billPayDanni, christiansChecking, joint));
            holders(transactions);

            assertTrue(controller.registersHoldingOtherSide(date, 40.00).isEmpty());
        }
    }

    @Test
    @DisplayName("Several registers holding a candidate are all returned, so the question is limited to them")
    void severalRegistersHoldingOtherSide_areAllReturned() {
        try (MockedStatic<Register> registers = Mockito.mockStatic(Register.class);
             MockedStatic<Transaction> transactions = Mockito.mockStatic(Transaction.class)) {
            registers.when(Register::getListOf).thenReturn(List.of(billPayDanni, christiansChecking, joint));
            holders(transactions, christiansChecking, joint);

            assertEquals(Set.of(christiansChecking, joint), controller.registersHoldingOtherSide(date, 40.00));
        }
    }

    @Test
    @DisplayName("The register being imported is never its own other side")
    void currentRegister_isExcluded() {
        try (MockedStatic<Register> registers = Mockito.mockStatic(Register.class);
             MockedStatic<Transaction> transactions = Mockito.mockStatic(Transaction.class)) {
            registers.when(Register::getListOf).thenReturn(List.of(billPayDanni, christiansChecking));
            holders(transactions, billPayDanni);

            assertTrue(controller.registersHoldingOtherSide(date, 40.00).isEmpty());
        }
    }

    @Test
    @DisplayName("A lookup failure is no match, so the usual questions are asked")
    void lookupFailure_isEmpty() {
        try (MockedStatic<Register> registers = Mockito.mockStatic(Register.class)) {
            registers.when(Register::getListOf).thenThrow(new RuntimeException("database unavailable"));

            assertTrue(controller.registersHoldingOtherSide(date, 40.00).isEmpty());
        }
    }
}
