package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.budget.BudgetItemMerchant;
import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.model.merchant.MerchantPayee;
import com.hixon.financialApp.model.merchant.MerchantUtilities;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.view.base.ViewInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MerchantController#assignMerchantForBudgetItem}.
 *
 * <p>On 09-18-2026 the user confirmed the $1,600.00 Citi payment "ELECTRONIC PAYMENT-THANK YO" was
 * 'Payment - Dave', and was then asked whether its merchant was Samsung Electronics -- the one merchant
 * whose name shares the word "electronic".  The user answered yes, and the payee, the transaction and the
 * budget item all ended up tied to Samsung.  The budget item's own merchants are now offered first.
 */
@DisplayName("Assign a merchant to a transaction confirmed to be a budget item")
class AssignMerchantForBudgetItemTest {

    private static final String PAYEE = "ELECTRONIC PAYMENT-THANK YO";

    private ViewInt view;
    private MerchantController merchantController;
    private BudgetItem paymentDave;
    private Merchant billPayDave;
    private Merchant checkfree;

    private static Merchant merchant(String name) throws Exception {
        Merchant merchant = mock(Merchant.class);
        when(merchant.getId()).thenReturn(UUID.randomUUID());
        when(merchant.getName()).thenReturn(name);
        when(merchant.getPayees()).thenReturn(List.of());
        return merchant;
    }

    private static BudgetItemMerchant association(Merchant merchant) {
        BudgetItemMerchant association = mock(BudgetItemMerchant.class);
        UUID id = merchant.getId();
        when(association.getIdMerchant()).thenReturn(id);
        return association;
    }

    @BeforeEach
    void setUp() throws Exception {
        view = mock(ViewInt.class);
        SessionController sessionController = mock(SessionController.class);
        when(sessionController.getView()).thenReturn(view);
        merchantController = new MerchantController(sessionController);

        paymentDave = mock(BudgetItem.class);
        when(paymentDave.getPayee()).thenReturn("Payment - Dave");
        billPayDave = merchant("Bill Pay Dave");
        checkfree = merchant("Checkfree");
    }

    /** Stubs the budget item's merchants and the lookups the method makes. */
    private void stubLookups(MockedStatic<Merchant> merchants, MockedStatic<BudgetItemMerchant> associations,
                             Merchant knownForPayee) {
        merchants.when(() -> Merchant.getByPayee(PAYEE)).thenReturn(knownForPayee);
        merchants.when(() -> Merchant.getById(billPayDave.getId())).thenReturn(billPayDave);
        merchants.when(() -> Merchant.getById(checkfree.getId())).thenReturn(checkfree);
        List<BudgetItemMerchant> assigned = List.of(association(billPayDave), association(checkfree));
        associations.when(() -> BudgetItemMerchant.getAssignedMerchantsForBudgetItem(paymentDave))
                .thenReturn(assigned);
    }

    @Test
    @DisplayName("The budget item's merchants are offered, and the one chosen is remembered for the payee")
    void offersTheBudgetItemsMerchants() throws Exception {
        try (MockedStatic<Merchant> merchants = mockStatic(Merchant.class);
             MockedStatic<BudgetItemMerchant> associations = mockStatic(BudgetItemMerchant.class);
             MockedStatic<MerchantUtilities> utilities = mockStatic(MerchantUtilities.class);
             MockedStatic<MerchantPayee> payees = mockStatic(MerchantPayee.class);
             MockedConstruction<MerchantPayee> created = mockConstruction(MerchantPayee.class)) {

            stubLookups(merchants, associations, null);
            when(view.selectByPositionFromList(anyString(), anyList(), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean())).thenReturn(0);

            Merchant chosen = merchantController.assignMerchantForBudgetItem(PAYEE, PAYEE, 1600.00, paymentDave);

            assertSame(billPayDave, chosen);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<String>> choices = ArgumentCaptor.forClass(List.class);
            verify(view).selectByPositionFromList(anyString(), choices.capture(), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean());
            assertEquals(List.of("Bill Pay Dave", "Checkfree", MerchantController.NONE_OF_THESE_MERCHANTS),
                    choices.getValue());

            // The payee now belongs to the merchant chosen, so the next payment is recognised.
            payees.verify(() -> MerchantPayee.deleteByName(PAYEE));
            assertEquals(1, created.constructed().size());
            verify(created.constructed().get(0)).save();
        }
    }

    @Test
    @DisplayName("A payee that is already known is not asked about")
    void knownPayeeIsNotAskedAbout() throws Exception {
        try (MockedStatic<Merchant> merchants = mockStatic(Merchant.class);
             MockedStatic<BudgetItemMerchant> associations = mockStatic(BudgetItemMerchant.class);
             MockedStatic<MerchantUtilities> utilities = mockStatic(MerchantUtilities.class);
             MockedStatic<Register> registers = mockStatic(Register.class)) {

            stubLookups(merchants, associations, checkfree);

            Merchant chosen = merchantController.assignMerchantForBudgetItem(PAYEE, PAYEE, 1600.00, paymentDave);

            assertSame(checkfree, chosen);
            verify(view, never()).selectByPositionFromList(anyString(), anyList(), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean());
        }
    }

    @Test
    @DisplayName("'None of these' goes on to the ordinary merchant search")
    void noneOfTheseSearches() throws Exception {
        Merchant searched = merchant("Citibank");

        try (MockedStatic<Merchant> merchants = mockStatic(Merchant.class);
             MockedStatic<BudgetItemMerchant> associations = mockStatic(BudgetItemMerchant.class);
             MockedStatic<MerchantUtilities> utilities = mockStatic(MerchantUtilities.class);
             MockedStatic<MerchantPayee> payees = mockStatic(MerchantPayee.class);
             MockedConstruction<MerchantPayee> created = mockConstruction(MerchantPayee.class);
             MockedConstruction<SelectionController> search = mockConstruction(SelectionController.class,
                     withSettings().defaultAnswer(invocation -> searched))) {

            stubLookups(merchants, associations, null);
            when(view.selectByPositionFromList(anyString(), anyList(), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean())).thenReturn(2);

            Merchant chosen = merchantController.assignMerchantForBudgetItem(PAYEE, PAYEE, 1600.00, paymentDave);

            assertSame(searched, chosen);
            assertEquals(1, search.constructed().size());
        }
    }

    @Test
    @DisplayName("Without a budget item it is the ordinary merchant assignment")
    void noBudgetItemSearches() throws Exception {
        Merchant searched = merchant("Citibank");

        try (MockedStatic<Merchant> merchants = mockStatic(Merchant.class);
             MockedStatic<BudgetItemMerchant> associations = mockStatic(BudgetItemMerchant.class);
             MockedStatic<MerchantUtilities> utilities = mockStatic(MerchantUtilities.class);
             MockedStatic<MerchantPayee> payees = mockStatic(MerchantPayee.class);
             MockedConstruction<MerchantPayee> created = mockConstruction(MerchantPayee.class);
             MockedConstruction<SelectionController> search = mockConstruction(SelectionController.class,
                     withSettings().defaultAnswer(invocation -> searched))) {

            stubLookups(merchants, associations, null);

            Merchant chosen = merchantController.assignMerchantForBudgetItem(PAYEE, PAYEE, 1600.00, null);

            assertSame(searched, chosen);
            verify(view, never()).selectByPositionFromList(anyString(), anyList(), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean());
        }
    }
}
