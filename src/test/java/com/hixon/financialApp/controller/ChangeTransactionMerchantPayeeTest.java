package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.model.merchant.MerchantPayee;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.view.base.ViewInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for moving the payee mapping when a transaction's merchant is changed.
 *
 * <p>On 09-18-2026 a Citi import mapped the payee "ELECTRONIC PAYMENT-THANK YO" to Samsung Electronics.
 * Correcting the one transaction would have left the mapping in place, and next month's payment would have
 * arrived as Samsung Electronics again.
 */
@DisplayName("Move the payee mapping when a transaction's merchant is changed")
class ChangeTransactionMerchantPayeeTest {

    private static MerchantPayee payee(String payee) {
        MerchantPayee merchantPayee = mock(MerchantPayee.class);
        when(merchantPayee.getPayee()).thenReturn(payee);
        return merchantPayee;
    }

    private static Merchant merchant(String name) {
        Merchant merchant = mock(Merchant.class);
        when(merchant.getId()).thenReturn(UUID.randomUUID());
        when(merchant.getName()).thenReturn(name);
        return merchant;
    }

    private ViewInt view;
    private TransactionController transactionController;

    @BeforeEach
    void setUp() {
        view = mock(ViewInt.class);
        SessionController sessionController = mock(SessionController.class);
        when(sessionController.getView()).thenReturn(view);
        transactionController = new TransactionController(sessionController);
    }

    @Test
    @DisplayName("Only the payee strings the transaction's payee carries are the ones behind the merchant")
    void payeesBehindTheTransaction() {
        List<MerchantPayee> samsung = List.of(payee("Samsung Elec"), payee("Samsung USA"),
                payee("ELECTRONIC PAYMENT-THANK YO"));

        assertEquals(List.of("ELECTRONIC PAYMENT-THANK YO"),
                TransactionController.payeesBehind("ELECTRONIC PAYMENT-THANK YO", samsung));
    }

    @Test
    @DisplayName("Payee strings are matched without regard to case")
    void payeesBehindIgnoresCase() {
        assertEquals(List.of("Samsung Elec"),
                TransactionController.payeesBehind("PURCHASE SAMSUNG ELEC 650-9345824 NJ", List.of(payee("Samsung Elec"))));
    }

    @Test
    @DisplayName("Nothing is found for a missing payee")
    void payeesBehindMissingPayee() {
        assertEquals(List.of(), TransactionController.payeesBehind(null, List.of(payee("Samsung Elec"))));
    }

    @Test
    @DisplayName("Saying yes moves the payee to the new merchant")
    void yesMovesThePayee() throws Exception {
        Transaction transaction = mock(Transaction.class);
        when(transaction.getPayee()).thenReturn("ELECTRONIC PAYMENT-THANK YO");
        Merchant samsung = merchant("Samsung Electronics");
        Merchant billPayDave = merchant("Bill Pay Dave");
        when(view.getYesOrNo(anyString())).thenReturn(true);

        try (MockedStatic<MerchantPayee> payees = mockStatic(MerchantPayee.class);
             MockedConstruction<MerchantPayee> created = mockConstruction(MerchantPayee.class)) {
            List<MerchantPayee> samsungPayees = List.of(payee("Samsung Elec"), payee("ELECTRONIC PAYMENT-THANK YO"));
            payees.when(() -> MerchantPayee.getPayeesForMerchant(samsung)).thenReturn(samsungPayees);

            transactionController.offerToMovePayees(transaction, samsung, billPayDave);

            payees.verify(() -> MerchantPayee.deleteByName("ELECTRONIC PAYMENT-THANK YO"));
            payees.verify(() -> MerchantPayee.deleteByName("Samsung Elec"), never());
            assertEquals(1, created.constructed().size());
            verify(created.constructed().get(0)).save();
        }
    }

    @Test
    @DisplayName("Saying no leaves the payee where it was")
    void noLeavesThePayee() throws Exception {
        Transaction transaction = mock(Transaction.class);
        when(transaction.getPayee()).thenReturn("ELECTRONIC PAYMENT-THANK YO");
        Merchant samsung = merchant("Samsung Electronics");
        when(view.getYesOrNo(anyString())).thenReturn(false);

        try (MockedStatic<MerchantPayee> payees = mockStatic(MerchantPayee.class);
             MockedConstruction<MerchantPayee> created = mockConstruction(MerchantPayee.class)) {
            List<MerchantPayee> samsungPayees = List.of(payee("ELECTRONIC PAYMENT-THANK YO"));
            payees.when(() -> MerchantPayee.getPayeesForMerchant(samsung)).thenReturn(samsungPayees);
            Merchant billPayDave = merchant("Bill Pay Dave");

            transactionController.offerToMovePayees(transaction, samsung, billPayDave);

            payees.verify(() -> MerchantPayee.deleteByName(anyString()), never());
            assertEquals(0, created.constructed().size());
        }
    }
}
