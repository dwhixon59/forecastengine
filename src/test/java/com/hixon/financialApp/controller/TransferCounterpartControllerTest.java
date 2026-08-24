package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.budget.Item;
import com.hixon.financialApp.model.budget.TransactionSplit;
import com.hixon.financialApp.model.budget.TransferBudgetItemPair;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.ForecastItem;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.view.base.ViewInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TransferCounterpartController} -- the claim that a transfer between two
 * registers is one movement of money and should be dealt with once.
 *
 * <p>The controller's writes go through protected seams, so a test subclass can record what would
 * have been written without a live database.  Everything the controller reads is a static lookup,
 * which Mockito's {@code mockStatic} stands in for.
 */
@DisplayName("Transfer Counterpart Tests")
public class TransferCounterpartControllerTest {

    /**
     * A controller that records rather than writes, so the decisions above the persistence layer can
     * be exercised on their own.
     */
    private static class RecordingController extends TransferCounterpartController {

        final List<ForecastTransaction> inserted = new ArrayList<>();
        final List<ForecastTransaction> updated = new ArrayList<>();
        final List<ForecastTransaction> deleted = new ArrayList<>();
        final List<ForecastItem> insertedForecastItems = new ArrayList<>();
        final List<BudgetItem> insertedBudgetItems = new ArrayList<>();
        final List<String> pairingsRecorded = new ArrayList<>();

        /** Whether the counterparty register is pretending to already hold the other side. */
        boolean otherSideAlreadyThere = false;

        /** What the controller told the user, so tests can assert on which register it named. */
        List<String> messages = new ArrayList<>();

        RecordingController(SessionController sessionController) {
            super(sessionController);
        }

        void captureFrom(List<String> viewMessages) {
            this.messages = viewMessages;
        }

        @Override
        protected boolean otherSideAlreadyExists(Transaction transaction, Register counterpartyRegister,
                                                 String reference) {
            return otherSideAlreadyThere;
        }

        @Override
        protected void insert(ForecastTransaction forecastTransaction) {
            inserted.add(forecastTransaction);
        }

        @Override
        protected void update(ForecastTransaction forecastTransaction) {
            updated.add(forecastTransaction);
        }

        @Override
        protected void delete(ForecastTransaction forecastTransaction) {
            deleted.add(forecastTransaction);
        }

        @Override
        protected void insert(ForecastItem forecastItem) {
            insertedForecastItems.add(forecastItem);
        }

        @Override
        protected void insert(BudgetItem budgetItem) {
            insertedBudgetItems.add(budgetItem);
        }

        @Override
        protected void recordPairing(BudgetItem sourceBudgetItem, Budget targetBudget, BudgetItem targetBudgetItem) {
            pairingsRecorded.add(sourceBudgetItem.getPayee() + " -> " + targetBudgetItem.getPayee() +
                    " in " + targetBudget.getName());
        }
    }

    private static final UUID SOURCE_REGISTER_ID = UUID.randomUUID();
    private static final UUID COUNTERPARTY_REGISTER_ID = UUID.randomUUID();

    private SessionController sessionController;
    private ViewInt view;
    private List<String> capturedMessages;
    private Register sourceRegister;
    private Register counterpartyRegister;
    private Budget sourceBudget;
    private Budget targetBudget;
    private Forecast counterpartyForecast;

    @BeforeEach
    void setUp() {
        view = mock(ViewInt.class);
        capturedMessages = new ArrayList<>();
        Mockito.doAnswer(invocation -> {
            capturedMessages.add(invocation.getArgument(0, String.class));
            return null;
        }).when(view).say(anyString());

        sourceRegister = mock(Register.class);
        when(sourceRegister.getId()).thenReturn(SOURCE_REGISTER_ID);
        when(sourceRegister.getName()).thenReturn("Bill Pay Danni");

        counterpartyRegister = mock(Register.class);
        when(counterpartyRegister.getId()).thenReturn(COUNTERPARTY_REGISTER_ID);
        when(counterpartyRegister.getName()).thenReturn("Bill Pay Dave");

        sourceBudget = mock(Budget.class);
        when(sourceBudget.getName()).thenReturn("Bill Pay Danni");
        when(sourceBudget.getId()).thenReturn(UUID.randomUUID());

        targetBudget = mock(Budget.class);
        when(targetBudget.getName()).thenReturn("Bill Pay Dave");
        when(targetBudget.getId()).thenReturn(UUID.randomUUID());

        counterpartyForecast = mock(Forecast.class);
        when(counterpartyForecast.getId()).thenReturn(UUID.randomUUID());
        when(counterpartyForecast.getDescription()).thenReturn("Bill Pay Account - Dave Forecast");

        sessionController = mock(SessionController.class);
        when(sessionController.getView()).thenReturn(view);
        when(sessionController.getRegister()).thenReturn(sourceRegister);
        when(sessionController.getBudget()).thenReturn(sourceBudget);
    }


    /*
     * Fixtures.
     */

    private Transaction transferTransaction(String payee, double amount) throws Exception {
        Transaction transaction = mock(Transaction.class);
        when(transaction.getId()).thenReturn(UUID.randomUUID());
        when(transaction.getIdRegister()).thenReturn(SOURCE_REGISTER_ID);
        when(transaction.getRegister()).thenReturn(sourceRegister);
        when(transaction.getPayee()).thenReturn(payee);
        when(transaction.getAmount()).thenReturn(amount);
        when(transaction.getDate()).thenReturn(dateOf(2026, Calendar.AUGUST, 3));

        // A transfer's merchant is the counterparty register's name - that is the convention the
        // whole application uses, and it is how the import's already-made decision reaches us.
        Merchant merchant = mock(Merchant.class);
        when(merchant.getName()).thenReturn("Bill Pay Dave");
        when(transaction.getMerchant()).thenReturn(merchant);

        return transaction;
    }

    private static Calendar dateOf(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month, day);
        return calendar;
    }

    private BudgetItem budgetItem(String payee, Item.PeriodType period) {
        BudgetItem item = mock(BudgetItem.class);
        when(item.getId()).thenReturn(UUID.randomUUID());
        when(item.getPayee()).thenReturn(payee);
        when(item.getCategory()).thenReturn("Transfers");
        when(item.getMemo()).thenReturn("");
        when(item.getPeriod()).thenReturn(period);
        when(item.getAmount()).thenReturn(0.0);
        when(item.getStartDate()).thenReturn(dateOf(2026, Calendar.JANUARY, 1));
        return item;
    }

    private TransactionSplit split(double amount, BudgetItem budgetItem) throws Exception {
        // Read the id out first: calling a mock inside a thenReturn(...) leaves the stubbing unfinished.
        UUID idBudgetItem = budgetItem.getId();
        TransactionSplit split = mock(TransactionSplit.class);
        when(split.getAmount()).thenReturn(amount);
        when(split.getBudgetItem()).thenReturn(budgetItem);
        when(split.getIdBudgetItem()).thenReturn(idBudgetItem);
        return split;
    }

    /** Stubs the lookups every "this really is a transfer into a register with a forecast" run needs. */
    private void stubTransferIntoForecastedRegister(MockedStatic<Register> registers,
                                                    MockedStatic<Forecast> forecasts,
                                                    MockedStatic<ForecastTransaction> forecastTransactions) {
        registers.when(() -> Register.getByName("Bill Pay Dave")).thenReturn(counterpartyRegister);
        forecasts.when(() -> Forecast.getMostRecent(any(Register.class))).thenReturn(counterpartyForecast);
        try {
            when(counterpartyForecast.getBudget()).thenReturn(targetBudget);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        forecastTransactions.when(() -> ForecastTransaction.getCounterpartsOfSourceTransaction(any()))
                .thenReturn(new ArrayList<ForecastTransaction>());
        forecastTransactions.when(() -> ForecastTransaction.getCounterpartByReference(any(), any()))
                .thenReturn(null);
    }


    /*
     * The ad-hoc gate.
     */

    @Test
    @DisplayName("An on-demand budget item is ad-hoc, so its split needs a counterpart")
    void testOnDemandIsAdHoc() {
        assertTrue(TransferCounterpartController.isAdHoc(Item.PeriodType.ON_DEMAND));
    }

    @Test
    @DisplayName("A scheduled budget item is not ad-hoc - the far side already has a planned transaction")
    void testScheduledIsNotAdHoc() {
        // Creating a second forecast transaction for a planned item would give the matcher two
        // candidates for the same money and risk stranding the planned one.
        assertFalse(TransferCounterpartController.isAdHoc(Item.PeriodType.MONTHLY));
        assertFalse(TransferCounterpartController.isAdHoc(Item.PeriodType.SEMIMONTHLY));
        assertFalse(TransferCounterpartController.isAdHoc(Item.PeriodType.WEEKLY));
    }


    /*
     * Phase 5.5:  creating the counterpart.
     */

    @Test
    @DisplayName("An ad-hoc transfer with a known pairing creates one counterpart against the paired item")
    void testKnownPairingCreatesCounterpartAgainstPairedItem() throws Exception {

        Transaction transaction = transferTransaction(
                "ONLINE TRANSFER TO HIXON D REF #IB0ZBFJRYR EVERYDAY CHECKING XXXXXX7018", -30.00);
        BudgetItem sourceItem = budgetItem("Reimbursement", Item.PeriodType.ON_DEMAND);
        BudgetItem pairedTargetItem = budgetItem("Room rental and utilities", Item.PeriodType.ON_DEMAND);

        TransferBudgetItemPair pairing = mock(TransferBudgetItemPair.class);
        when(pairing.getTargetBudgetItem()).thenReturn(pairedTargetItem);

        RecordingController controller;
        try (MockedStatic<Register> registers = Mockito.mockStatic(Register.class);
             MockedStatic<Forecast> forecasts = Mockito.mockStatic(Forecast.class);
             MockedStatic<ForecastTransaction> forecastTransactions = Mockito.mockStatic(ForecastTransaction.class);
             MockedStatic<ForecastItem> forecastItems = Mockito.mockStatic(ForecastItem.class);
             MockedStatic<TransferBudgetItemPair> pairings = Mockito.mockStatic(TransferBudgetItemPair.class)) {

            stubTransferIntoForecastedRegister(registers, forecasts, forecastTransactions);
            pairings.when(() -> TransferBudgetItemPair.getBySourceAndTargetBudget(any(), any())).thenReturn(pairing);
            forecastItems.when(() -> ForecastItem.getByBudgetItemId(any(Forecast.class), any(UUID.class)))
                    .thenReturn(null);

            controller = new RecordingController(sessionController);
            controller.recordOtherSideOfTransfer(transaction, List.of(split(-30.00, sourceItem)));
        }

        assertEquals(1, controller.inserted.size(), "One ad-hoc split means one counterpart");
        ForecastTransaction counterpart = controller.inserted.get(0);

        assertEquals(30.00, counterpart.getRemainingAmount(), 0.001,
                "The counterpart carries the negated split amount");
        assertEquals(transaction.getId(), counterpart.getIdSourceTransaction());
        assertEquals(sourceItem.getId(), counterpart.getIdSourceBudgetItem());
        assertEquals("IB0ZBFJRYR", counterpart.getSourceReference());
        assertFalse(counterpart.isTransferPairingUnknown(),
                "The pairing was known, so the budget item on this counterpart is an answer");
        assertTrue(counterpart.isOverridden(),
                "Counterparts must be overridden so regenerating the far forecast does not delete them");
        assertTrue(counterpart.isTransferCounterpart());
    }

    @Test
    @DisplayName("An unknown pairing still creates a counterpart, flagged so its budget item is not trusted")
    void testUnknownPairingCreatesFlaggedCounterpart() throws Exception {

        Transaction transaction = transferTransaction("ONLINE TRANSFER TO HIXON D XXXXXX7018", -52.00);
        BudgetItem sourceItem = budgetItem("Reimbursement", Item.PeriodType.ON_DEMAND);

        RecordingController controller;
        try (MockedStatic<Register> registers = Mockito.mockStatic(Register.class);
             MockedStatic<Forecast> forecasts = Mockito.mockStatic(Forecast.class);
             MockedStatic<ForecastTransaction> forecastTransactions = Mockito.mockStatic(ForecastTransaction.class);
             MockedStatic<ForecastItem> forecastItems = Mockito.mockStatic(ForecastItem.class);
             MockedStatic<BudgetItem> budgetItems = Mockito.mockStatic(BudgetItem.class);
             MockedStatic<TransferBudgetItemPair> pairings = Mockito.mockStatic(TransferBudgetItemPair.class)) {

            stubTransferIntoForecastedRegister(registers, forecasts, forecastTransactions);
            pairings.when(() -> TransferBudgetItemPair.getBySourceAndTargetBudget(any(), any())).thenReturn(null);
            budgetItems.when(() -> BudgetItem.getUnexpiredByPayee(any(), anyString()))
                    .thenReturn(new ArrayList<BudgetItem>());
            forecastItems.when(() -> ForecastItem.getByBudgetItemId(any(Forecast.class), any(UUID.class)))
                    .thenReturn(null);

            controller = new RecordingController(sessionController);
            controller.recordOtherSideOfTransfer(transaction, List.of(split(-52.00, sourceItem)));
        }

        assertEquals(1, controller.inserted.size());
        ForecastTransaction counterpart = controller.inserted.get(0);

        assertTrue(counterpart.isTransferPairingUnknown(),
                "Without the flag, Phase 2.5 would assign the placeholder budget item and suppress the " +
                        "very questions that should be asked");
        assertEquals(sourceItem.getId(), counterpart.getIdSourceBudgetItem(),
                "The source budget item is what makes learning the pairing possible later");
        assertNull(counterpart.getSourceReference(),
                "This payee carries no REF #, which is the majority case and entirely normal");

        assertEquals(1, controller.insertedBudgetItems.size(), "A placeholder budget item was created");
        assertEquals(TransferCounterpartController.PLACEHOLDER_PAYEE,
                controller.insertedBudgetItems.get(0).getPayee());
    }

    @Test
    @DisplayName("A split against a planned budget item produces no counterpart")
    void testPlannedSplitProducesNoCounterpart() throws Exception {

        Transaction transaction = transferTransaction("ONLINE TRANSFER TO HIXON D XXXXXX7018", -1625.00);
        BudgetItem plannedItem = budgetItem("Danni's contribution", Item.PeriodType.MONTHLY);

        RecordingController controller;
        try (MockedStatic<Register> registers = Mockito.mockStatic(Register.class);
             MockedStatic<Forecast> forecasts = Mockito.mockStatic(Forecast.class);
             MockedStatic<ForecastTransaction> forecastTransactions = Mockito.mockStatic(ForecastTransaction.class)) {

            stubTransferIntoForecastedRegister(registers, forecasts, forecastTransactions);

            controller = new RecordingController(sessionController);
            controller.recordOtherSideOfTransfer(transaction, List.of(split(-1625.00, plannedItem)));
        }

        assertTrue(controller.inserted.isEmpty(),
                "The far side already has a planned forecast transaction; a second would compete with it");
    }

    @Test
    @DisplayName("A multi-split transfer produces one counterpart per ad-hoc split, and none for the planned one")
    void testMultiSplitProducesOneCounterpartPerAdHocSplit() throws Exception {

        Transaction transaction = transferTransaction("ONLINE TRANSFER TO HIXON D XXXXXX7018", -95.00);
        BudgetItem groceries = budgetItem("Grocery reimbursement", Item.PeriodType.ON_DEMAND);
        BudgetItem haircuts = budgetItem("Boys hair cuts", Item.PeriodType.ON_DEMAND);
        BudgetItem rent = budgetItem("Danni's contribution", Item.PeriodType.MONTHLY);

        RecordingController controller;
        try (MockedStatic<Register> registers = Mockito.mockStatic(Register.class);
             MockedStatic<Forecast> forecasts = Mockito.mockStatic(Forecast.class);
             MockedStatic<ForecastTransaction> forecastTransactions = Mockito.mockStatic(ForecastTransaction.class);
             MockedStatic<ForecastItem> forecastItems = Mockito.mockStatic(ForecastItem.class);
             MockedStatic<BudgetItem> budgetItems = Mockito.mockStatic(BudgetItem.class);
             MockedStatic<TransferBudgetItemPair> pairings = Mockito.mockStatic(TransferBudgetItemPair.class)) {

            stubTransferIntoForecastedRegister(registers, forecasts, forecastTransactions);
            pairings.when(() -> TransferBudgetItemPair.getBySourceAndTargetBudget(any(), any())).thenReturn(null);
            budgetItems.when(() -> BudgetItem.getUnexpiredByPayee(any(), anyString()))
                    .thenReturn(new ArrayList<BudgetItem>());
            forecastItems.when(() -> ForecastItem.getByBudgetItemId(any(Forecast.class), any(UUID.class)))
                    .thenReturn(null);

            controller = new RecordingController(sessionController);
            controller.recordOtherSideOfTransfer(transaction, List.of(
                    split(-40.00, groceries), split(-15.00, haircuts), split(-40.00, rent)));
        }

        assertEquals(2, controller.inserted.size(),
                "A transfer covering two ad-hoc things should arrive as two expectations, not one lump");
        assertEquals(40.00, controller.inserted.get(0).getRemainingAmount(), 0.001);
        assertEquals(15.00, controller.inserted.get(1).getRemainingAmount(), 0.001);
        assertEquals(groceries.getId(), controller.inserted.get(0).getIdSourceBudgetItem());
        assertEquals(haircuts.getId(), controller.inserted.get(1).getIdSourceBudgetItem());
    }

    @Test
    @DisplayName("A counterparty register with no forecast gets nothing - the feedless case")
    void testNoForecastOnCounterpartyRegisterCreatesNothing() throws Exception {

        Transaction transaction = transferTransaction("ONLINE TRANSFER TO HIXON D XXXXXX7018", -30.00);
        BudgetItem sourceItem = budgetItem("Reimbursement", Item.PeriodType.ON_DEMAND);

        RecordingController controller;
        try (MockedStatic<Register> registers = Mockito.mockStatic(Register.class);
             MockedStatic<Forecast> forecasts = Mockito.mockStatic(Forecast.class)) {

            registers.when(() -> Register.getByName("Bill Pay Dave")).thenReturn(counterpartyRegister);

            // No forecast is how the application records that this register has no import feed.
            // Note there is no "is this register active" test anywhere - only this data question.
            forecasts.when(() -> Forecast.getMostRecent(any(Register.class))).thenReturn(null);

            controller = new RecordingController(sessionController);
            controller.recordOtherSideOfTransfer(transaction, List.of(split(-30.00, sourceItem)));
        }

        assertTrue(controller.inserted.isEmpty(),
                "Anything written to a feedless register is never matched and sits forever as a stale expectation");
    }

    @Test
    @DisplayName("A transaction that is not a transfer gets nothing")
    void testNonTransferCreatesNothing() throws Exception {

        Transaction purchase = mock(Transaction.class);
        when(purchase.getId()).thenReturn(UUID.randomUUID());
        when(purchase.getIdRegister()).thenReturn(SOURCE_REGISTER_ID);
        when(purchase.getPayee()).thenReturn("PURCHASE AUTHORIZED ON 08/03 TRADER JOE'S SAN DIEGO CA");
        Merchant merchant = mock(Merchant.class);
        when(merchant.getName()).thenReturn("Trader Joe's");
        when(purchase.getMerchant()).thenReturn(merchant);

        BudgetItem sourceItem = budgetItem("Groceries", Item.PeriodType.ON_DEMAND);

        RecordingController controller;
        try (MockedStatic<Register> registers = Mockito.mockStatic(Register.class)) {
            registers.when(() -> Register.getByName(anyString())).thenReturn(null);
            registers.when(() -> Register.getByLastFourDigits(anyString())).thenReturn(null);

            controller = new RecordingController(sessionController);
            controller.recordOtherSideOfTransfer(purchase, List.of(split(-42.00, sourceItem)));
        }

        assertTrue(controller.inserted.isEmpty());
    }

    @Test
    @DisplayName("A transfer whose merchant resolves to the register being imported gets nothing")
    void testSelfTransferCreatesNothing() throws Exception {

        Transaction transaction = transferTransaction("ONLINE TRANSFER TO HIXON D XXXXXX7018", -30.00);
        BudgetItem sourceItem = budgetItem("Reimbursement", Item.PeriodType.ON_DEMAND);

        RecordingController controller;
        try (MockedStatic<Register> registers = Mockito.mockStatic(Register.class)) {
            // A transfer's masked number and merchant identify the COUNTERPARTY, never the account
            // being imported, so a match to ourselves is not a counterparty at all.
            registers.when(() -> Register.getByName(anyString())).thenReturn(sourceRegister);
            registers.when(() -> Register.getByLastFourDigits(anyString())).thenReturn(sourceRegister);

            controller = new RecordingController(sessionController);
            controller.recordOtherSideOfTransfer(transaction, List.of(split(-30.00, sourceItem)));
        }

        assertTrue(controller.inserted.isEmpty());
    }

    @Test
    @DisplayName("Re-importing the source register does not create the counterpart a second time")
    void testCounterpartIsNotCreatedTwiceOnReimport() throws Exception {

        Transaction transaction = transferTransaction("ONLINE TRANSFER TO HIXON D XXXXXX7018", -30.00);
        BudgetItem sourceItem = budgetItem("Reimbursement", Item.PeriodType.ON_DEMAND);

        RecordingController controller;
        try (MockedStatic<Register> registers = Mockito.mockStatic(Register.class);
             MockedStatic<Forecast> forecasts = Mockito.mockStatic(Forecast.class);
             MockedStatic<ForecastTransaction> forecastTransactions = Mockito.mockStatic(ForecastTransaction.class)) {

            registers.when(() -> Register.getByName("Bill Pay Dave")).thenReturn(counterpartyRegister);
            forecasts.when(() -> Forecast.getMostRecent(any(Register.class))).thenReturn(counterpartyForecast);
            when(counterpartyForecast.getBudget()).thenReturn(targetBudget);

            // The source transaction id is the primary "already created" check.
            forecastTransactions.when(() -> ForecastTransaction.getCounterpartsOfSourceTransaction(any()))
                    .thenReturn(List.of(mock(ForecastTransaction.class)));

            controller = new RecordingController(sessionController);
            controller.recordOtherSideOfTransfer(transaction, List.of(split(-30.00, sourceItem)));
        }

        assertTrue(controller.inserted.isEmpty());
    }

    @Test
    @DisplayName("A source row deleted and re-imported under a new id is still recognized by its bank reference")
    void testBankReferenceCatchesAReimportedSourceRow() throws Exception {

        // The source transaction's UUID has changed, so the primary check misses. The bank reference
        // is issued by the bank and is stable across re-imports, so it still identifies the pair.
        Transaction transaction = transferTransaction(
                "ONLINE TRANSFER TO HIXON D REF #IB0ZBFJRYR EVERYDAY CHECKING XXXXXX7018", -30.00);
        BudgetItem sourceItem = budgetItem("Reimbursement", Item.PeriodType.ON_DEMAND);

        RecordingController controller;
        try (MockedStatic<Register> registers = Mockito.mockStatic(Register.class);
             MockedStatic<Forecast> forecasts = Mockito.mockStatic(Forecast.class);
             MockedStatic<ForecastTransaction> forecastTransactions = Mockito.mockStatic(ForecastTransaction.class)) {

            registers.when(() -> Register.getByName("Bill Pay Dave")).thenReturn(counterpartyRegister);
            forecasts.when(() -> Forecast.getMostRecent(any(Register.class))).thenReturn(counterpartyForecast);
            when(counterpartyForecast.getBudget()).thenReturn(targetBudget);
            forecastTransactions.when(() -> ForecastTransaction.getCounterpartsOfSourceTransaction(any()))
                    .thenReturn(new ArrayList<ForecastTransaction>());
            forecastTransactions.when(() -> ForecastTransaction.getCounterpartByReference(any(), any()))
                    .thenReturn(mock(ForecastTransaction.class));

            controller = new RecordingController(sessionController);
            controller.recordOtherSideOfTransfer(transaction, List.of(split(-30.00, sourceItem)));
        }

        assertTrue(controller.inserted.isEmpty());
    }


    @Test
    @DisplayName("Nothing is recorded when the counterparty register already holds the other side")
    void testOtherSideAlreadyPresentCreatesNothing() throws Exception {

        // This is what stops the expectation bouncing back. Importing the second register processes
        // transfers too, and each one would otherwise record an expectation in the register it came
        // from -- for a transfer that has already happened there.
        Transaction transaction = transferTransaction("ONLINE TRANSFER TO HIXON D XXXXXX7018", -30.00);
        BudgetItem sourceItem = budgetItem("Reimbursement", Item.PeriodType.ON_DEMAND);

        RecordingController controller;
        try (MockedStatic<Register> registers = Mockito.mockStatic(Register.class);
             MockedStatic<Forecast> forecasts = Mockito.mockStatic(Forecast.class);
             MockedStatic<ForecastTransaction> forecastTransactions = Mockito.mockStatic(ForecastTransaction.class)) {

            stubTransferIntoForecastedRegister(registers, forecasts, forecastTransactions);

            controller = new RecordingController(sessionController);
            controller.otherSideAlreadyThere = true;
            controller.recordOtherSideOfTransfer(transaction, List.of(split(-30.00, sourceItem)));
        }

        assertTrue(controller.inserted.isEmpty(),
                "Both registers hold their own copy, so there is nothing left to expect");
    }


    @Test
    @DisplayName("The masked account number in the payee beats a merchant_payee row that disagrees")
    void testAccountNumberBeatsAStaleMerchantMapping() throws Exception {

        // Taken from a real import. The payee plainly carries XXXXXX7394 -- Danni's Spending Account
        // -- but merchant_payee maps "Transfer to Danni's Spending Account from Bill Pay Dave" to a
        // merchant named "Dave", and MerchantController skips confirmation for transfer payees, so
        // that wrong answer reaches us silently. The account number is a fact the bank stated; the
        // mapping is user-maintained. Where they disagree, the bank wins.
        Transaction transaction = transferTransaction(
                "ONLINE TRANSFER TO HIXON D EVERYDAY CHECKING XXXXXX7394 REF #IB0ZFB4JC5 ON 08/18/26", -14.00);
        Merchant staleMerchant = mock(Merchant.class);
        when(staleMerchant.getName()).thenReturn("Dave's Spending Account");
        when(transaction.getMerchant()).thenReturn(staleMerchant);

        Register danniSpending = mock(Register.class);
        when(danniSpending.getId()).thenReturn(UUID.randomUUID());
        when(danniSpending.getName()).thenReturn("Danni's Spending Account");

        Register davesSpending = mock(Register.class);
        when(davesSpending.getId()).thenReturn(UUID.randomUUID());
        when(davesSpending.getName()).thenReturn("Dave's Spending Account");

        BudgetItem sourceItem = budgetItem("Other", Item.PeriodType.ON_DEMAND);

        RecordingController controller;
        try (MockedStatic<Register> registers = Mockito.mockStatic(Register.class);
             MockedStatic<Forecast> forecasts = Mockito.mockStatic(Forecast.class);
             MockedStatic<ForecastTransaction> forecastTransactions = Mockito.mockStatic(ForecastTransaction.class);
             MockedStatic<ForecastItem> forecastItems = Mockito.mockStatic(ForecastItem.class);
             MockedStatic<BudgetItem> budgetItems = Mockito.mockStatic(BudgetItem.class);
             MockedStatic<TransferBudgetItemPair> pairings = Mockito.mockStatic(TransferBudgetItemPair.class)) {

            registers.when(() -> Register.getByLastFourDigits("7394")).thenReturn(danniSpending);
            registers.when(() -> Register.getByName("Dave's Spending Account")).thenReturn(davesSpending);

            // Both registers have a forecast here, so only the resolution order decides where the
            // counterpart lands -- the feedless convention cannot mask a wrong answer.
            forecasts.when(() -> Forecast.getMostRecent(any(Register.class))).thenReturn(counterpartyForecast);
            when(counterpartyForecast.getBudget()).thenReturn(targetBudget);
            forecastTransactions.when(() -> ForecastTransaction.getCounterpartsOfSourceTransaction(any()))
                    .thenReturn(new ArrayList<ForecastTransaction>());
            forecastTransactions.when(() -> ForecastTransaction.getCounterpartByReference(any(), any()))
                    .thenReturn(null);
            pairings.when(() -> TransferBudgetItemPair.getBySourceAndTargetBudget(any(), any())).thenReturn(null);
            budgetItems.when(() -> BudgetItem.getUnexpiredByPayee(any(), anyString()))
                    .thenReturn(new ArrayList<BudgetItem>());
            forecastItems.when(() -> ForecastItem.getByBudgetItemId(any(Forecast.class), any(UUID.class)))
                    .thenReturn(null);

            controller = new RecordingController(sessionController);
            controller.captureFrom(capturedMessages);
            controller.recordOtherSideOfTransfer(transaction, List.of(split(-14.00, sourceItem)));
        }

        assertEquals(1, controller.inserted.size());
        assertTrue(controller.messages.stream().anyMatch(m -> m.contains("Danni's Spending Account")),
                "The counterpart belongs to the register the bank named, not the one the stale " +
                        "merchant mapping named. Messages were: " + controller.messages);
        assertFalse(controller.messages.stream().anyMatch(m -> m.contains("Dave's Spending Account")),
                "Messages were: " + controller.messages);
    }


    /*
     * Learning the pairing from what the far import chose.
     */

    @Test
    @DisplayName("The far import's single answer is recorded as the pairing, and the placeholder is dropped")
    void testPairingIsLearnedAndPlaceholderDropped() throws Exception {

        BudgetItem sourceItem = budgetItem("Reimbursement", Item.PeriodType.ON_DEMAND);
        BudgetItem chosenTargetItem = budgetItem("Room rental and utilities", Item.PeriodType.ON_DEMAND);
        UUID idSourceItem = sourceItem.getId();

        ForecastTransaction counterpart = mock(ForecastTransaction.class);
        when(counterpart.getIdSourceBudgetItem()).thenReturn(idSourceItem);

        RecordingController controller;
        try (MockedStatic<BudgetItem> budgetItems = Mockito.mockStatic(BudgetItem.class)) {
            budgetItems.when(() -> BudgetItem.getById(idSourceItem)).thenReturn(sourceItem);

            controller = new RecordingController(sessionController);
            controller.learnPairingAndDropPlaceholder(counterpart, List.of(split(30.00, chosenTargetItem)));
        }

        assertEquals(List.of("Reimbursement -> Room rental and utilities in Bill Pay Danni"),
                controller.pairingsRecorded,
                "The question has now been asked once, in the place the application already asks it");
        assertEquals(List.of(counterpart), controller.deleted,
                "The placeholder expectation has served its purpose now that the real thing has arrived");
    }

    @Test
    @DisplayName("A far side split several ways records no pairing, but still drops the placeholder")
    void testMultiSplitFarSideRecordsNoPairing() throws Exception {

        BudgetItem sourceItem = budgetItem("Reimbursement", Item.PeriodType.ON_DEMAND);
        UUID idSourceItem = sourceItem.getId();

        ForecastTransaction counterpart = mock(ForecastTransaction.class);
        when(counterpart.getIdSourceBudgetItem()).thenReturn(idSourceItem);

        RecordingController controller;
        try (MockedStatic<BudgetItem> budgetItems = Mockito.mockStatic(BudgetItem.class)) {
            budgetItems.when(() -> BudgetItem.getById(idSourceItem)).thenReturn(sourceItem);

            controller = new RecordingController(sessionController);
            controller.learnPairingAndDropPlaceholder(counterpart, List.of(
                    split(20.00, budgetItem("Groceries", Item.PeriodType.ON_DEMAND)),
                    split(10.00, budgetItem("Haircuts", Item.PeriodType.ON_DEMAND))));
        }

        assertTrue(controller.pairingsRecorded.isEmpty(),
                "There is no single answer to record, and guessing would be silently wrong from then on");
        assertEquals(List.of(counterpart), controller.deleted,
                "The placeholder goes either way - the transfer has arrived");
    }


    /*
     * Lifecycle.
     */

    @Test
    @DisplayName("Deleting the source transaction removes its counterparts")
    void testDeletingSourceRemovesCounterparts() throws Exception {

        Transaction transaction = transferTransaction("ONLINE TRANSFER TO HIXON D XXXXXX7018", -30.00);
        ForecastTransaction counterpart = mock(ForecastTransaction.class);

        RecordingController controller;
        try (MockedStatic<ForecastTransaction> forecastTransactions = Mockito.mockStatic(ForecastTransaction.class)) {
            forecastTransactions.when(() -> ForecastTransaction.getCounterpartsOfSourceTransaction(transaction.getId()))
                    .thenReturn(List.of(counterpart));

            controller = new RecordingController(sessionController);
            assertEquals(1, controller.deleteCounterpartsFor(transaction));
        }

        assertEquals(List.of(counterpart), controller.deleted,
                "The expectation only exists because the source transaction did");
    }

    @Test
    @DisplayName("Editing the source amount and date brings its single counterpart back into line")
    void testEditingSourceUpdatesCounterpart() throws Exception {

        Transaction transaction = transferTransaction("ONLINE TRANSFER TO HIXON D XXXXXX7018", -45.00);
        when(transaction.getDate()).thenReturn(dateOf(2026, Calendar.AUGUST, 11));

        ForecastTransaction counterpart = new ForecastTransaction();
        counterpart.setPlannedDate(dateOf(2026, Calendar.AUGUST, 3));
        counterpart.setRemainingAmount(30.00);

        RecordingController controller;
        try (MockedStatic<ForecastTransaction> forecastTransactions = Mockito.mockStatic(ForecastTransaction.class)) {
            forecastTransactions.when(() -> ForecastTransaction.getCounterpartsOfSourceTransaction(transaction.getId()))
                    .thenReturn(List.of(counterpart));

            controller = new RecordingController(sessionController);
            assertEquals(1, controller.updateCounterpartsFor(transaction));
        }

        assertEquals(45.00, counterpart.getRemainingAmount(), 0.001,
                "The counterpart carries the negated amount of its source");
        assertEquals(11, counterpart.getPlannedDate().get(Calendar.DAY_OF_MONTH),
                "and the same date, or the far import scores against a stale expectation");
    }

    @Test
    @DisplayName("An unchanged source transaction leaves its counterpart untouched")
    void testUnchangedSourceLeavesCounterpartAlone() throws Exception {

        Transaction transaction = transferTransaction("ONLINE TRANSFER TO HIXON D XXXXXX7018", -30.00);

        ForecastTransaction counterpart = new ForecastTransaction();
        counterpart.setPlannedDate(dateOf(2026, Calendar.AUGUST, 3));
        counterpart.setRemainingAmount(30.00);

        RecordingController controller;
        try (MockedStatic<ForecastTransaction> forecastTransactions = Mockito.mockStatic(ForecastTransaction.class)) {
            forecastTransactions.when(() -> ForecastTransaction.getCounterpartsOfSourceTransaction(transaction.getId()))
                    .thenReturn(List.of(counterpart));

            controller = new RecordingController(sessionController);
            assertEquals(0, controller.updateCounterpartsFor(transaction));
        }

        assertTrue(controller.updated.isEmpty());
    }


    /*
     * Reporting.
     */

    @Test
    @DisplayName("A counterpart with a bank reference names it, so the pair can be reconciled by hand")
    void testDescribeCounterpartNamesTheBankReference() {
        ForecastTransaction counterpart = new ForecastTransaction();
        counterpart.setSourceReference("IB0ZBFJRYR");

        String description = TransferCounterpartController.describeCounterpart(counterpart);

        assertTrue(description.startsWith("Taken from the corresponding transfer"), description);
        assertTrue(description.contains("IB0ZBFJRYR"), description);
    }

    @Test
    @DisplayName("A counterpart whose source transaction is gone still describes itself")
    void testDescribeCounterpartSurvivesAMissingSource() {
        // A lookup failure must never break an import.
        String description = TransferCounterpartController.describeCounterpart(new ForecastTransaction());

        assertEquals("Taken from the corresponding transfer", description);
    }
}
