package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.budget.Item;
import com.hixon.financialApp.model.budget.TransactionSplit;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.ForecastItem;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.model.forecast.ForecastTransactionSplit.SplitDisposition;
import com.hixon.financialApp.utility.ForecastTransactionMatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.doubleThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for re-budgeting an item to the amount that actually arrived.
 *
 * <p>Two questions can end in "adjust", and until now only one of them offered it.  The overage
 * prompt in {@code deductSplitAmount} lives inside {@code case COLLECTION}, and the
 * "differs significantly" prompt is asked <em>only</em> when the item is not COLLECTION -- so the
 * two never met, and a PERIODIC item whose price had changed could not be re-budgeted from the
 * import at all.
 *
 * <p>The case that surfaced it:  Netflix went from $19.99 to $22.56.  Streaming TV is PERIODIC, so
 * the import offered only assign / do-not-assign / dispute, and every future occurrence stayed at
 * the old price until the budget item was edited by hand.
 */
@DisplayName("Adjust Budgeted Amount Tests")
public class AdjustBudgetedAmountTest {

    /** Netflix, as budgeted before the price rise:  a monthly, periodic expense of $19.99. */
    private static BudgetItem netflix() {
        BudgetItem item = mock(BudgetItem.class);
        when(item.getAmount()).thenReturn(-19.99);
        when(item.getPayee()).thenReturn("Streaming TV");
        when(item.getHowOccurs()).thenReturn(Item.HowOccurs.PERIODIC);
        return item;
    }

    private static TransactionSplit splitOf(BudgetItem item, double amount) throws Exception {
        TransactionSplit split = mock(TransactionSplit.class);
        when(split.getAmount()).thenReturn(amount);
        when(split.getBudgetItem()).thenReturn(item);
        return split;
    }

    private static ForecastTransaction occurrenceOf(Forecast forecast, double remainingAmount) throws Exception {
        ForecastItem forecastItem = mock(ForecastItem.class);
        when(forecastItem.getForecast()).thenReturn(forecast);

        ForecastTransaction occurrence = mock(ForecastTransaction.class);
        when(occurrence.getForecastItem()).thenReturn(forecastItem);
        when(occurrence.getRemainingAmount()).thenReturn(remainingAmount);
        return occurrence;
    }


    /*
     * The adjustment itself:
     */
    @Test
    @DisplayName("Adjusting re-budgets the item, desyncs the forecast and settles the occurrence")
    void testAdjustAppliesAllThreeEffects() throws Exception {

        BudgetItem item = netflix();
        TransactionSplit split = splitOf(item, -22.56);
        Forecast forecast = mock(Forecast.class);
        ForecastTransaction occurrence = occurrenceOf(forecast, -19.99);

        ForecastController controller = new ForecastController(mock(SessionController.class));
        controller.adjustBudgetItemAmount(split, occurrence, split.getAmount());

        // The item carries the new price from here on ...
        verify(item).setAmount(-22.56);
        verify(item).save(any());

        // ... the forecast knows it is stale, so a regeneration picks the change up ...
        verify(forecast).setInSync(false);
        verify(forecast).save(any());

        // ... and the occurrence being reconciled right now is set to the new amount, so that the
        // split about to be deducted from it settles to zero instead of leaving a $2.57 remainder.
        verify(occurrence).setRemainingAmount(-22.56);
    }

    @Test
    @DisplayName("The sign of the budgeted amount follows the transaction, not the user's typing")
    void testExpenseStaysNegative() throws Exception {

        // Deliberately not prompted for:  the amount comes from the split, so an expense cannot be
        // re-budgeted as a positive number by someone typing "22.56".
        BudgetItem item = netflix();
        TransactionSplit split = splitOf(item, -22.56);
        Forecast forecast = mock(Forecast.class);

        ForecastController controller = new ForecastController(mock(SessionController.class));
        controller.adjustBudgetItemAmount(split, occurrenceOf(forecast, -19.99), split.getAmount());

        verify(item).setAmount(doubleThat(amount -> amount < 0));
    }

    @Test
    @DisplayName("Income adjusts upward without changing sign either")
    void testIncomeStaysPositive() throws Exception {

        BudgetItem rent = mock(BudgetItem.class);
        when(rent.getPayee()).thenReturn("Room rental and utilities");
        TransactionSplit split = splitOf(rent, 1245.00);
        Forecast forecast = mock(Forecast.class);

        ForecastController controller = new ForecastController(mock(SessionController.class));
        controller.adjustBudgetItemAmount(split, occurrenceOf(forecast, 1242.00), split.getAmount());

        verify(rent).setAmount(1245.00);
    }


    /*
     * The gap that made this necessary:
     */
    @Test
    @DisplayName("A PERIODIC item is asked the differs-significantly question, not the overage one")
    void testPeriodicItemReachesOnlyTheAmountMatchQuestion() throws Exception {

        // The two questions are selected by exactly this test, in opposite directions:
        // getApplicableForecastTransaction asks the amount-match question only when the item is not
        // COLLECTION, and deductSplitAmount asks the overage question only when it is.  So before
        // this change a PERIODIC item could reach neither route to "adjust".
        assertNotEquals(Item.HowOccurs.COLLECTION, netflix().getHowOccurs(),
                "Netflix is PERIODIC, so the overage prompt that offers adjust is never reached");

        // And it is outside tolerance, so the amount-match question really is asked.
        assertFalse(ForecastTransactionMatcher.isAmountWithinAutoMatchTolerance(-22.56, -19.99),
                "$22.56 against a planned $19.99 is what raised the question in the first place");
    }

    @Test
    @DisplayName("A small price change stays silent - the question is only asked outside tolerance")
    void testWithinToleranceAsksNothing() {

        // The regression guard:  adding an option to a question must not cause the question to be
        // asked more often.  A cent or two of drift still auto-assigns with no prompt at all.
        assertTrue(ForecastTransactionMatcher.isAmountWithinAutoMatchTolerance(-20.01, -19.99));
    }

    @Test
    @DisplayName("ADJUST is a disposition the split can carry")
    void testAdjustDispositionExists() {
        assertNotNull(SplitDisposition.valueOf("ADJUST"));
    }
}
