package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.budget.TransactionSplit;
import com.hixon.financialApp.model.budget.TransferBudgetItemPair;
import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.view.base.ViewInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TransferCounterpartController#findPayingSide}:  identifying a card payment by the
 * paying register's own copy of it, through a budget item pairing, rather than by the card's wording.
 *
 * <p>Citi writes "ONLINE PAYMENT, THANK YOU" for payments from both Bill Pay Dave and Bill Pay Danni.  On
 * 07-28-2026 a $150.00 payment from Bill Pay Dave was filed under Danni because of it.
 */
@DisplayName("Find the paying side of a card payment")
class FindPayingSideTest {

    /** A controller whose database reads are the maps below. */
    private static class StubbedController extends TransferCounterpartController {

        final List<Register> registers = new ArrayList<>();
        final Map<Register, List<Transaction>> oppositeSides = new HashMap<>();
        final Map<Transaction, List<TransactionSplit>> splits = new HashMap<>();
        final Map<BudgetItem, BudgetItem> pairings = new HashMap<>();
        final Map<String, Merchant> merchants = new HashMap<>();

        StubbedController(SessionController sessionController) {
            super(sessionController);
        }

        @Override
        protected List<Register> otherRegisters(Register here) {
            return registers;
        }

        @Override
        protected List<Transaction> oppositeSideIn(Register register, Transaction transaction) {
            return oppositeSides.getOrDefault(register, List.of());
        }

        @Override
        protected List<TransactionSplit> splitsOf(Transaction transaction) {
            return splits.get(transaction);
        }

        @Override
        protected BudgetItem budgetItemOf(TransactionSplit split) {
            return sourceItems.get(split);
        }

        final Map<TransactionSplit, BudgetItem> sourceItems = new HashMap<>();

        @Override
        protected TransferBudgetItemPair pairingFor(BudgetItem sourceBudgetItem, Budget targetBudget)
                throws Exception {
            BudgetItem target = pairings.get(sourceBudgetItem);
            if (target == null) {
                return null;
            }
            TransferBudgetItemPair pairing = mock(TransferBudgetItemPair.class);
            when(pairing.getTargetBudgetItem()).thenReturn(target);
            return pairing;
        }

        @Override
        protected Merchant merchantNamed(String name) {
            return merchants.get(name);
        }
    }

    private StubbedController controller;
    private Register billPayDave;
    private Register billPayDanni;
    private BudgetItem aadvantageDavid;
    private BudgetItem paymentDave;

    private static Register register(String name) {
        Register register = mock(Register.class);
        when(register.getId()).thenReturn(UUID.randomUUID());
        when(register.getName()).thenReturn(name);
        return register;
    }

    private static BudgetItem budgetItem(String payee) {
        BudgetItem item = mock(BudgetItem.class);
        when(item.getId()).thenReturn(UUID.randomUUID());
        when(item.getPayee()).thenReturn(payee);
        return item;
    }

    private static Merchant merchant(String name) {
        Merchant merchant = mock(Merchant.class);
        when(merchant.getName()).thenReturn(name);
        return merchant;
    }

    private static Transaction transaction(double amount, String payee, int day) {
        Transaction transaction = mock(Transaction.class);
        when(transaction.getAmount()).thenReturn(amount);
        when(transaction.getPayee()).thenReturn(payee);
        when(transaction.getDate()).thenReturn(new GregorianCalendar(2026, 8, day));
        return transaction;
    }

    /** Puts a paying transaction in a register, split once to the given budget item. */
    private Transaction paidFrom(Register register, double amount, String payee, int day, BudgetItem item) {
        Transaction paying = transaction(amount, payee, day);
        TransactionSplit split = mock(TransactionSplit.class);
        controller.oppositeSides.computeIfAbsent(register, r -> new ArrayList<>()).add(paying);
        controller.splits.put(paying, List.of(split));
        controller.sourceItems.put(split, item);
        return paying;
    }

    @BeforeEach
    void setUp() {
        SessionController session = mock(SessionController.class);
        when(session.getView()).thenReturn(mock(ViewInt.class));
        Register citi = register("Citi AAdvantage Mastercard");
        Budget citiBudget = mock(Budget.class);
        when(session.getRegister()).thenReturn(citi);
        when(session.getBudget()).thenReturn(citiBudget);
        controller = new StubbedController(session);

        billPayDave = register("Bill Pay Dave");
        billPayDanni = register("Bill Pay Danni");
        controller.registers.addAll(List.of(billPayDave, billPayDanni));
        controller.merchants.put("Bill Pay Dave", merchant("Bill Pay Dave"));
        controller.merchants.put("Bill Pay Danni", merchant("Bill Pay Danni"));

        aadvantageDavid = budgetItem("AAdvantage Card - David");
        paymentDave = budgetItem("Payment - Dave");
        controller.pairings.put(aadvantageDavid, paymentDave);
    }

    @Test
    @DisplayName("The 09-17 $1,600 payment is Bill Pay Dave's, and Payment - Dave")
    void findsThePayingRegister() throws Exception {
        Transaction paying = paidFrom(billPayDave, -1600.00, "BILL PAY Citi AAdvantage Card", 16, aadvantageDavid);

        TransferCounterpartController.PayingSide side =
                controller.findPayingSide(transaction(1600.00, "ELECTRONIC PAYMENT-THANK YO", 17));

        assertNotNull(side);
        assertSame(billPayDave, side.payingRegister());
        assertSame(paying, side.payingTransaction());
        assertEquals("Bill Pay Dave", side.merchant().getName());
        assertSame(paymentDave, side.budgetItem());
    }

    @Test
    @DisplayName("A same-sized debit to a budget item with no pairing is not a payment to this card")
    void unpairedBudgetItemIsIgnored() throws Exception {
        // "CITI CARD ONLINE PAYMENT" has also paid the untracked Citibank Card.
        paidFrom(billPayDanni, -750.00, "CITI CARD ONLINE PAYMENT", 14, budgetItem("Citibank Card"));

        assertNull(controller.findPayingSide(transaction(750.00, "ONLINE PAYMENT, THANK YOU", 12)));
    }

    @Test
    @DisplayName("Two candidate payments are ambiguous, so the import asks as before")
    void twoCandidatesAreAmbiguous() throws Exception {
        BudgetItem aadvantageDanni = budgetItem("AAdvantage Card - Danni");
        controller.pairings.put(aadvantageDanni, budgetItem("Payment - Danni"));
        paidFrom(billPayDave, -150.00, "CITI CARD ONLINE PAYMENT", 29, aadvantageDavid);
        paidFrom(billPayDanni, -150.00, "CITI CARD ONLINE PAYMENT", 27, aadvantageDanni);

        assertNull(controller.findPayingSide(transaction(150.00, "ONLINE PAYMENT, THANK YOU", 28)));
    }

    @Test
    @DisplayName("A paying transaction split several ways is not taken as the payment")
    void multipleSplitsAreIgnored() throws Exception {
        Transaction paying = paidFrom(billPayDave, -1600.00, "BILL PAY Citi AAdvantage Card", 16, aadvantageDavid);
        controller.splits.put(paying, List.of(mock(TransactionSplit.class), mock(TransactionSplit.class)));

        assertNull(controller.findPayingSide(transaction(1600.00, "ELECTRONIC PAYMENT-THANK YO", 17)));
    }

    @Test
    @DisplayName("Different bank references are different movements of money")
    void differentReferencesAreIgnored() throws Exception {
        paidFrom(billPayDave, -1600.00, "ONLINE TRANSFER REF #IB0AAAAAAA", 16, aadvantageDavid);

        assertNull(controller.findPayingSide(transaction(1600.00, "PAYMENT REF #IB0BBBBBBB", 17)));
    }

    @Test
    @DisplayName("Without a merchant named after the paying register, the import asks as before")
    void missingRegisterMerchant() throws Exception {
        controller.merchants.clear();
        paidFrom(billPayDave, -1600.00, "BILL PAY Citi AAdvantage Card", 16, aadvantageDavid);

        assertNull(controller.findPayingSide(transaction(1600.00, "ELECTRONIC PAYMENT-THANK YO", 17)));
    }

    @Test
    @DisplayName("Nothing in any other register means there is no paying side yet")
    void nothingFound() throws Exception {
        assertNull(controller.findPayingSide(transaction(1600.00, "ELECTRONIC PAYMENT-THANK YO", 17)));
    }

    @Test
    @DisplayName("The payment is described by the paying register's own copy")
    void describesThePayment() throws Exception {
        paidFrom(billPayDave, -1600.00, "BILL PAY Citi AAdvantage Card", 16, aadvantageDavid);

        TransferCounterpartController.PayingSide side =
                controller.findPayingSide(transaction(1600.00, "ELECTRONIC PAYMENT-THANK YO", 17));

        assertEquals("Payment from Bill Pay Dave (09-16-2026, $-1,600.00 'BILL PAY Citi AAdvantage Card')" +
                " -> Payment - Dave", side.describe());
    }
}
