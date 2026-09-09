package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.budget.BudgetItemMerchant;
import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.view.base.ViewInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for recording a budget item the user has already chosen against a merchant.
 *
 * <p>The split prompt lets the user type a search string instead of a number, picks a budget item
 * from the results, and then offers to make the association permanent.  Answering "y" used to call
 * {@code assignBudgetItemsToMerchant}, which prompts for a budget item of its own and never looks at
 * the one already selected -- so the choice was discarded and the user was dropped into a fresh
 * search.
 *
 * <p>On 09-08-2026, recategorizing a 7-Eleven charge, the user searched "work", chose "Danni's Work
 * Expenses - Food for work", and confirmed the permanent association.  The redisplayed list came back
 * holding only "Gas for work" and "Other";  the item they had just chosen was not in it, and they had
 * to search "work" and pick it a second time before they could use it.
 */
@DisplayName("Associate an already-chosen budget item with a merchant")
class AssociateBudgetItemWithMerchantTest {

    private static final UUID GAS_FOR_WORK = UUID.randomUUID();
    private static final UUID FOOD_FOR_WORK = UUID.randomUUID();

    private static BudgetItem budgetItem(UUID id, String payee) {
        BudgetItem item = mock(BudgetItem.class);
        when(item.getId()).thenReturn(id);
        when(item.getPayee()).thenReturn(payee);
        return item;
    }

    private static BudgetItemMerchant association(BudgetItem item) {
        BudgetItemMerchant association = mock(BudgetItemMerchant.class);
        when(association.getBudgetItem()).thenReturn(item);
        return association;
    }

    /** A BudgetController wired to a mocked session, which is all this method touches. */
    private static BudgetController budgetController() {
        SessionController sessionController = mock(SessionController.class);
        when(sessionController.getView()).thenReturn(mock(ViewInt.class));
        return new BudgetController(sessionController);
    }

    @Test
    @DisplayName("The item the user chose is added to the merchant's list")
    void theChosenItemIsAddedToTheMerchantsList() throws Exception {

        Merchant sevenEleven = mock(Merchant.class);
        when(sevenEleven.getName()).thenReturn("7-Eleven");

        BudgetItem gasForWork = budgetItem(GAS_FOR_WORK, "Danni's Work Expenses");
        BudgetItem foodForWork = budgetItem(FOOD_FOR_WORK, "Danni's Work Expenses");

        // The merchant starts out with only the "Gas for work" association, exactly as 7-Eleven did.
        List<BudgetItemMerchant> budgetItemsForMerchant = new ArrayList<>(List.of(association(gasForWork)));

        try (MockedStatic<BudgetItemMerchant> associations = mockStatic(BudgetItemMerchant.class);
             MockedConstruction<BudgetItemMerchant> created = mockConstruction(BudgetItemMerchant.class)) {

            // isBudgetItemInList is a static on the same class, so it has to keep working while the
            // class is mocked.
            associations.when(() -> BudgetItemMerchant.isBudgetItemInList(
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenCallRealMethod();
            associations.when(() -> BudgetItemMerchant.getByItemAndMerchant(foodForWork, sevenEleven))
                    .thenReturn(null);

            boolean added = budgetController().associateBudgetItemWithMerchant(
                    sevenEleven, foodForWork, budgetItemsForMerchant);

            assertTrue(added, "a budget item the merchant did not have should be reported as added");

            // The association is persisted...
            assertEquals(1, created.constructed().size(),
                    "a new association should have been created for the chosen item");
            verify(created.constructed().get(0)).save();

            // ...and, the part that was broken, it is also put into the list the caller is about to
            // redisplay.  Without this the user is shown a list that does not contain their choice.
            assertEquals(2, budgetItemsForMerchant.size(),
                    "the chosen item should be in the list the caller redisplays");
            assertSame(created.constructed().get(0), budgetItemsForMerchant.get(1),
                    "the association added to the list should be the one that was saved");
        }
    }

    @Test
    @DisplayName("An association the database already holds is reused rather than duplicated")
    void anExistingDatabaseAssociationIsReused() throws Exception {

        Merchant sevenEleven = mock(Merchant.class);
        when(sevenEleven.getName()).thenReturn("7-Eleven");

        BudgetItem gasForWork = budgetItem(GAS_FOR_WORK, "Danni's Work Expenses");
        BudgetItem foodForWork = budgetItem(FOOD_FOR_WORK, "Danni's Work Expenses");

        List<BudgetItemMerchant> budgetItemsForMerchant = new ArrayList<>(List.of(association(gasForWork)));
        BudgetItemMerchant existing = mock(BudgetItemMerchant.class);

        try (MockedStatic<BudgetItemMerchant> associations = mockStatic(BudgetItemMerchant.class);
             MockedConstruction<BudgetItemMerchant> created = mockConstruction(BudgetItemMerchant.class)) {

            associations.when(() -> BudgetItemMerchant.isBudgetItemInList(
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenCallRealMethod();
            associations.when(() -> BudgetItemMerchant.getByItemAndMerchant(foodForWork, sevenEleven))
                    .thenReturn(existing);

            boolean added = budgetController().associateBudgetItemWithMerchant(
                    sevenEleven, foodForWork, budgetItemsForMerchant);

            assertTrue(added, "the item still reaches the list, so it counts as added");
            assertEquals(0, created.constructed().size(),
                    "an association that already exists must not be saved a second time");
            assertSame(existing, budgetItemsForMerchant.get(1),
                    "the association already on file should be the one used");
            verify(existing).setBudgetItem(foodForWork);
        }
    }

    @Test
    @DisplayName("An item the merchant already has is left alone")
    void anItemTheMerchantAlreadyHasIsLeftAlone() throws Exception {

        Merchant sevenEleven = mock(Merchant.class);
        when(sevenEleven.getName()).thenReturn("7-Eleven");

        BudgetItem gasForWork = budgetItem(GAS_FOR_WORK, "Danni's Work Expenses");
        List<BudgetItemMerchant> budgetItemsForMerchant = new ArrayList<>(List.of(association(gasForWork)));

        try (MockedStatic<BudgetItemMerchant> associations = mockStatic(BudgetItemMerchant.class);
             MockedConstruction<BudgetItemMerchant> created = mockConstruction(BudgetItemMerchant.class)) {

            associations.when(() -> BudgetItemMerchant.isBudgetItemInList(
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenCallRealMethod();

            boolean added = budgetController().associateBudgetItemWithMerchant(
                    sevenEleven, gasForWork, budgetItemsForMerchant);

            assertFalse(added, "the merchant already has this budget item");
            assertEquals(1, budgetItemsForMerchant.size(), "the list should not gain a duplicate");
            assertEquals(0, created.constructed().size(), "nothing should be saved");
        }
    }

    @Test
    @DisplayName("No selection is not an association")
    void noSelectionIsNotAnAssociation() throws Exception {

        Merchant sevenEleven = mock(Merchant.class);
        List<BudgetItemMerchant> budgetItemsForMerchant = new ArrayList<>();

        // A cancelled search returns null, which must not be treated as a choice.
        assertFalse(budgetController().associateBudgetItemWithMerchant(sevenEleven, null, budgetItemsForMerchant));
        assertTrue(budgetItemsForMerchant.isEmpty());
    }
}
