package com.hixon.financialApp.view.base;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.entity.Entity;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.ForecastItem;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.model.forecast.ForecastTransactionIterator;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.text.EnvelopeReport;
import com.hixon.financialApp.view.text.OverdueItemsReport;
import com.hixon.financialApp.view.text.TrackingItemsOfInterestReport;
import com.hixon.financialApp.view.text.UpcomingItemsOfInterestReport;
import com.hixon.financialApp.view.text.UpcomingItemsReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Forecast Summary Output Integration Test")
class ForecastSummaryOutputIntegrationTest {

    @Test
    @DisplayName("Render long-term forecast emits all enhanced summary sections")
    void renderLongTermForecastEmitsEnhancedSummarySections() throws Exception {
        ViewInt originalView = Utility.getView();
        ViewInt mockView = mock(ViewInt.class);
        Utility.setView(mockView);

        Forecast forecast = mock(Forecast.class);
        Budget budget = mock(Budget.class);
        Register register = mock(Register.class);

        when(forecast.getBudget()).thenReturn(budget);
        when(budget.getRegisters()).thenReturn(List.of(register));
        when(register.getReportType()).thenReturn("csv");
        when(register.getBalance()).thenReturn(500.0);
        when(register.getName()).thenReturn("Bill Pay Dave");

        Calendar firstOfMonth = Utility.getNextFirstOfMonth(Calendar.getInstance());
        Calendar secondMonth = (Calendar) firstOfMonth.clone();
        secondMonth.add(Calendar.MONTH, 1);
        Calendar thirdMonth = (Calendar) firstOfMonth.clone();
        thirdMonth.add(Calendar.MONTH, 2);

        List<ForecastTransaction> transactions = List.of(
                mockTransaction(firstOfMonth, 1000.0, "Income", "Salary"),
                mockTransaction(firstOfMonth, -1300.0, "Household", "Mortgage payment (PITI)"),
                mockTransaction(addDays(firstOfMonth, 10), -300.0, "Spending Money", "Dave's work expenses"),
                mockTransaction(secondMonth, 1000.0, "Income", "Salary"),
                mockTransaction(secondMonth, -1200.0, "Household", "AAdvantage Card - David"),
                mockTransaction(addDays(secondMonth, 12), -100.0, "Online Services", "Nixplay"),
                mockTransaction(thirdMonth, 1000.0, "Income", "Side Hustle"),
                mockTransaction(addDays(thirdMonth, 7), -900.0, "Household", "HOA Fees")
        );

        ForecastTransactionIterator mainIterator = new ListForecastTransactionIterator(transactions);
        ForecastTransactionIterator floatIterator = new ListForecastTransactionIterator(transactions);

        TestForecastView forecastView = new TestForecastView(forecast);

        try (MockedStatic<Forecast> forecastStatic = mockStatic(Forecast.class);
             MockedStatic<ForecastTransaction> transactionStatic = mockStatic(ForecastTransaction.class)) {

            forecastStatic.when(() -> Forecast.getFirstNonZeroTransactionDate(forecast)).thenReturn(firstOfMonth);
            transactionStatic.when(() -> ForecastTransaction.zeroRunningBalances(forecast)).thenAnswer(invocation -> null);
            transactionStatic.when(() -> ForecastTransaction.getForecastTransactionsStartingOn(eq(forecast), any(Calendar.class)))
                    .thenReturn(mainIterator, floatIterator);

            forecastView.renderLongTermForecast(forecast);
        } finally {
            Utility.setView(originalView);
        }

        ArgumentCaptor<String> outputCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockView, atLeastOnce()).say(outputCaptor.capture());

        String output = String.join("\n", outputCaptor.getAllValues());

        assertTrue(output.contains("Forecast Summary:"));
        assertTrue(output.contains("Monthly Cash Flow Breakdown:"));
        assertTrue(output.contains("Expense Breakdown by Category:"));
        assertTrue(output.contains("Income Breakdown by Source:"));
        assertTrue(output.contains("Risk Warnings:"));
        assertTrue(output.contains("Actionable Recommendations:"));
        assertTrue(output.contains("Financial Runway Analysis:"));
        assertTrue(output.contains("Forecast Timeline:"));
        assertTrue(output.contains("Immediate Actions Required:"));

        assertTrue(output.contains("Critical: The account first goes negative"));
        assertTrue(output.contains("Option A: Reduce monthly spending"));
        assertTrue(output.contains("Positive balance phase:"));
        assertTrue(output.contains("Salary"));
        assertTrue(output.contains("Household"));
        assertTrue(output.contains("[ ] By "));
    }

    private static ForecastTransaction mockTransaction(Calendar plannedDate, double remainingAmount, String category,
                                                       String payee) throws Exception {
        ForecastTransaction transaction = mock(ForecastTransaction.class);
        ForecastItem item = mock(ForecastItem.class);

        when(transaction.getPlannedDate()).thenReturn((Calendar) plannedDate.clone());
        when(transaction.getRemainingAmount()).thenReturn(remainingAmount);
        when(transaction.getForecastItem()).thenReturn(item);
        when(item.getCategory()).thenReturn(category);
        when(item.getPayee()).thenReturn(payee);
        when(item.getId()).thenReturn(UUID.randomUUID());

        doNothing().when(transaction).setRunningBalance(any(Double.class));
        doNothing().when(transaction).save(any(EntityInt.SaveMethod.class));

        return transaction;
    }

    private static Calendar addDays(Calendar source, int days) {
        Calendar copy = (Calendar) source.clone();
        copy.add(Calendar.DAY_OF_MONTH, days);
        return copy;
    }

    private static final class ListForecastTransactionIterator implements ForecastTransactionIterator {
        private final List<ForecastTransaction> transactions;
        private int index = 0;

        private ListForecastTransactionIterator(List<ForecastTransaction> transactions) {
            this.transactions = new ArrayList<>(transactions);
        }

        @Override
        public ForecastTransaction getNext() {
            if (index >= transactions.size()) {
                return null;
            }
            return transactions.get(index++);
        }
    }

    private static final class TestForecastView extends AbstractForecastView {
        private int rowNumber = 0;

        private TestForecastView(Forecast forecast) {
            super(forecast);
        }

        @Override
        protected void openLongTermForecastOutput(String reportType) {
        }

        @Override
        protected void renderLongTermForecastFrontMatter(String reportType) {
        }

        @Override
        protected void renderLongTermForecastMonthHeader(String reportType, Calendar plannedDate, double runningBalance) {
        }

        @Override
        protected int renderLongTermForecastTransaction(String reportType, ForecastTransaction forecastTransaction,
                                                        double credit, double debit) {
            return ++rowNumber;
        }

        @Override
        protected void renderLongTermForecastBackMatter(String reportType) {
        }

        @Override
        protected void closeLongTermForecastOutput(String reportType) {
        }

        @Override
        protected String getLongTermForecastFilename() {
            return null;
        }

        @Override
        public void editLongTermForecast() {
        }

        @Override
        public void closeForecastTransactionSource(String sourceName) {
        }

        @Override
        public List<ForecastTransaction> openForecastTransactionSource(String sourceName) {
            return List.of();
        }

        @Override
        protected TrackingItemsOfInterestReport getTrackingItemsOfInterestReport(User user, List<Entity> items,
                                                                                 File reportFile) {
            return null;
        }

        @Override
        protected UpcomingItemsOfInterestReport getUpcomingItemsOfInterestReport(User user, List<Entity> items,
                                                                                 File reportFile) {
            return null;
        }

        @Override
        protected OverdueItemsReport getOverdueItemsReport(Forecast forecast, List<Entity> items, File reportFile) {
            return null;
        }

        @Override
        protected UpcomingItemsReport getUpcomingItemsReport(Forecast forecast, List<Entity> items, File reportFile) {
            return null;
        }

        @Override
        public EnvelopeReport getEnvelopeReport(Forecast forecast, List<Entity> items, File reportFile) {
            return null;
        }
    }
}




