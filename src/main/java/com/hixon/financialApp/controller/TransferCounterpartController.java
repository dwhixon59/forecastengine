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
import com.hixon.financialApp.model.merchant.MerchantUtilities;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.utility.BankReferenceNumber;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.base.ViewInt;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import static com.hixon.financialApp.model.entity.EntityInt.SaveMethod.INSERT;
import static com.hixon.financialApp.model.entity.EntityInt.SaveMethod.UPDATE;

/**
 * Records the expected other side of a transfer, so that one movement of money is dealt with once.
 *
 * <p>A transfer between two registers is a single movement of money, but each register's statement
 * reports it separately, and today each side is processed from scratch -- asking for a merchant and
 * a budget item both times.  When a transfer is processed in one register this controller creates a
 * <b>forecast transaction</b> in the counterparty register's forecast, at the same date and the
 * negated amount.  When the second statement is imported, Phase 2.5's existing
 * {@code ForecastTransactionMatcher} matches it, reports where it came from, and the questions are
 * skipped.
 *
 * <p>The counterpart is a forecast transaction rather than a compensating <em>transaction</em>
 * deliberately.  It needs no new matching code, does not touch {@code register.balance} (so there is
 * no double-count risk and no ordering problem with the balance update in Phase 2), does not
 * interact with the pending sweep (which only reads uncleared transactions), and is already a thing
 * the application creates, edits, regenerates and deletes.  It is also the honest model:  "this
 * transfer should turn up in Dave's account" is an expectation, and this application already
 * represents expectations as forecast transactions.
 *
 * <h2>The convention that resolves feedless registers</h2>
 *
 * <blockquote>
 * If the counterparty register has no forecast, do not create a forecast transaction for it.
 * </blockquote>
 *
 * <p>Note what this code does <b>not</b> contain:  any test of whether a register is active.  It
 * asks a data question -- does this register have a forecast? -- and the answer encodes the intent.
 * Whether a register has an import feed is a decision made once, by hand, when the forecast is or is
 * not created.  Turning on a feed for a register later needs no code change at all:  create a
 * forecast for it and transfers start landing there.
 *
 * @see TransferBudgetItemPair
 * @see BankReferenceNumber
 */
public class TransferCounterpartController {

    /**
     * The category and payee of the per-budget placeholder budget item that unpaired counterparts
     * hang off.  It exists only so that a forecast transaction can be created at all before the
     * pairing is known; {@link ForecastTransaction#isTransferPairingUnknown()} is what stops it from
     * ever being assigned to a transaction.
     */
    public static final String PLACEHOLDER_CATEGORY = "Transfers";
    public static final String PLACEHOLDER_PAYEE = "Unpaired transfer counterpart";

    /** forecast_transaction.memo is a varchar(64). */
    private static final int MEMO_MAX_LENGTH = 64;

    /**
     * How far either side of a transfer's date to look for the movement's other side in the
     * counterparty register.  Kept small:  the two sides of a transfer post within a day or two of
     * each other, and a wide window only risks mistaking an unrelated transaction for the other side.
     */
    private static final int OTHER_SIDE_DAY_WINDOW = 3;

    private final SessionController sessionController;
    private final ViewInt view;


    public TransferCounterpartController(SessionController sessionController) {
        this.sessionController = sessionController;
        this.view = sessionController.getView();
    }


    /*
     * Phase 5.5:  Record the other side of a transfer.
     */

    /**
     * Create the expected other side of a transfer, if this transaction is one.
     *
     * <p>Runs after the transaction, its merchant and its splits are settled, and is silent for
     * every transaction that is not a transfer into a register with a forecast.  Nothing here ever
     * asks the user a question:  the counterparty register was already resolved during import (see
     * {@link #resolveCounterpartyRegister}), and when the budget item pairing is not yet known the
     * counterpart is created anyway and flagged, so the far import asks in the place the application
     * already asks.
     *
     * @param transaction the transfer that was just processed in this register
     * @param splits      the splits assigned to it
     */
    public void recordOtherSideOfTransfer(Transaction transaction, List<TransactionSplit> splits) throws Exception {

        if (transaction == null || splits == null || splits.isEmpty()) {
            return;
        }

        // If the transaction is not a transfer, or we cannot say which register it went to:
        Register counterpartyRegister = resolveCounterpartyRegister(transaction);
        if (counterpartyRegister == null) {
            return;
        }

        // If the counterparty register has no forecast, it has no import feed, so an expectation
        // written there would never be matched and would sit forever as a stale expectation:
        Forecast counterpartyForecast = Forecast.getMostRecent(counterpartyRegister);
        if (counterpartyForecast == null) {
            return;
        }
        Budget targetBudget = counterpartyForecast.getBudget();

        // The bank's own reference for this transfer, if it issued one.  43% of transfers carry one,
        // so this is null more often than not and nothing below may depend on having it.
        String reference = BankReferenceNumber.extract(transaction.getPayee());

        // If a counterpart already exists, this register is being re-imported.  The source
        // transaction id is the primary check; the bank reference also identifies the pair when a
        // source row was deleted and recreated and its id changed.
        if (!ForecastTransaction.getCounterpartsOfSourceTransaction(transaction.getId()).isEmpty()) {
            return;
        }
        if (ForecastTransaction.getCounterpartByReference(counterpartyForecast, reference) != null) {
            return;
        }

        // If the counterparty register already holds the other side, there is nothing left to
        // expect.  This is what stops the expectation bouncing back:  when the second register is
        // imported, its transactions are transfers too, and without this check each one would record
        // an expectation in the register it came from -- for a transfer that has already happened
        // there.  It also makes re-imports of either side, in either order, come out the same.
        if (otherSideAlreadyExists(transaction, counterpartyRegister, reference)) {
            return;
        }

        // Work per split rather than per transaction:  a transfer covering two things on this side
        // should arrive as two expectations, not one lump, and a transfer that is part planned and
        // part ad-hoc should produce an expectation only for the ad-hoc part.
        for (TransactionSplit split : splits) {
            createCounterpartForSplit(transaction, split, counterpartyRegister, counterpartyForecast,
                    targetBudget, reference);
        }
    }

    private void createCounterpartForSplit(Transaction transaction, TransactionSplit split,
                                           Register counterpartyRegister, Forecast counterpartyForecast,
                                           Budget targetBudget, String reference) throws Exception {

        BudgetItem sourceBudgetItem = split.getBudgetItem();
        if (sourceBudgetItem == null) {
            return;
        }

        if (!isAdHoc(sourceBudgetItem.getPeriod())) {
            return;
        }

        // Which budget item does the far side belong to?  Only the user knows, so it is asked once,
        // by the far import, and remembered.
        TransferBudgetItemPair pairing =
                TransferBudgetItemPair.getBySourceAndTargetBudget(sourceBudgetItem, targetBudget);
        boolean pairingKnown = (pairing != null);

        BudgetItem targetBudgetItem = pairingKnown
                ? pairing.getTargetBudgetItem()
                : getOrCreatePlaceholderBudgetItem(targetBudget);
        if (targetBudgetItem == null) {
            return;
        }

        // A forecast transaction needs a forecast item, so make one for the target budget item if
        // the counterparty's forecast does not have one yet.
        double counterpartAmount = -split.getAmount();
        ForecastItem forecastItem =
                ForecastItem.getByBudgetItemId(counterpartyForecast, targetBudgetItem.getId());
        if (forecastItem == null) {
            forecastItem = new ForecastItem(counterpartyForecast, targetBudgetItem);
            forecastItem.setAmount(counterpartAmount);
            insert(forecastItem);
        }

        ForecastTransaction counterpart = new ForecastTransaction(forecastItem, transaction.getDate(), true);
        counterpart.setRemainingAmount(counterpartAmount);
        counterpart.setMemo(counterpartMemo(transaction));

        // Created overridden so that regenerating the counterparty's forecast before the far import
        // does not silently delete it:  updateForecast preserves transactions that are overridden or
        // already have splits, and a counterpart has neither until it is matched.  This is easy to
        // miss and would quietly undo the whole feature.
        counterpart.setOverridden(true);

        counterpart.setIdSourceTransaction(transaction.getId());
        counterpart.setIdSourceBudgetItem(sourceBudgetItem.getId());
        counterpart.setSourceReference(reference);

        // When the pairing is not known, the budget item above is a placeholder, not an answer.
        // Flagging it is what stops Phase 2.5 from auto-assigning the placeholder and thereby
        // suppressing the very questions that should be asked.
        counterpart.setTransferPairingUnknown(!pairingKnown);

        insert(counterpart);

        if (pairingKnown) {
            view.say("Recorded the other side of this transfer in " + counterpartyRegister.getName() + ":  " +
                    Utility.formatDollarAmount(counterpartAmount) + " against '" + targetBudgetItem.getPayee() + "'.");
        } else {
            view.say("Recorded the other side of this transfer in " + counterpartyRegister.getName() + ":  " +
                    Utility.formatDollarAmount(counterpartAmount) + ". Its budget item is not known yet and will " +
                    "be asked for when " + counterpartyRegister.getName() + " is imported.");
        }
    }


    /**
     * Whether a split against a budget item with this period is ad-hoc, and so needs a counterpart.
     *
     * <p><b>Only ad-hoc splits produce a counterpart.</b>  A split against a planned item already has
     * a forecast transaction waiting on the far side, which Phase 2.5 will match on its own; creating
     * a second one on the same date would give the matcher two candidates for the same money and risk
     * it taking the new one, leaving the planned transaction stranded and the forecast overstated.
     *
     * <p>On-demand is exactly the test the forecast generator itself uses:  it skips on-demand budget
     * items outright, so an on-demand item never produces a forecast transaction and there is nothing
     * on the far side for Phase 2.5 to match.
     *
     * <p>This reads the <em>source</em> split's budget item, which assumes that when the source side
     * is planned the target side is too.  A transfer that is budgeted on one side only will keep
     * asking on the far side; if that shows up in practice the fix is to gate on whether a target
     * forecast transaction actually exists rather than on the source item's type.
     */
    public static boolean isAdHoc(Item.PeriodType period) {
        return period == Item.PeriodType.ON_DEMAND;
    }


    /*
     * Learning the pairing from what the far import chose (section 2a).
     */

    /**
     * Record the budget item pairing the far import just established, and drop the placeholder.
     *
     * <p>Called once the far side's splits have been assigned by the ordinary import questions.  The
     * counterpart has done its job by then:  it told the far import that this transaction is the
     * other side of a transfer already dealt with, and it carries the source budget item, which is
     * what makes learning the pairing possible at all.  From the second occurrence of a pairing
     * onward the far import is silent.
     *
     * @param counterpart the unpaired counterpart that was matched
     * @param splits      the splits the far import assigned to the far transaction
     */
    public void learnPairingAndDropPlaceholder(ForecastTransaction counterpart, List<TransactionSplit> splits)
            throws Exception {

        if (counterpart == null) {
            return;
        }

        try {
            learnPairing(counterpart, splits);
        } finally {
            // The placeholder has to go whether or not the pairing could be recorded, otherwise it
            // stays in the forecast as an expectation that has already arrived.
            delete(counterpart);
        }
    }

    private void learnPairing(ForecastTransaction counterpart, List<TransactionSplit> splits) throws Exception {

        UUID idSourceBudgetItem = counterpart.getIdSourceBudgetItem();
        if (idSourceBudgetItem == null || splits == null || splits.isEmpty()) {
            return;
        }

        Budget targetBudget = sessionController.getBudget();
        if (targetBudget == null) {
            return;
        }

        // A pairing maps one source budget item to one target budget item.  When the far side was
        // split several ways there is no single answer to record, so say so rather than picking one
        // arbitrarily and being silently wrong from then on.
        if (splits.size() > 1) {
            view.say("This transfer was split " + splits.size() + " ways on this side, so no single budget item " +
                    "pairing was recorded. The next transfer of this kind will ask again.");
            return;
        }

        BudgetItem sourceBudgetItem = BudgetItem.getById(idSourceBudgetItem);
        BudgetItem targetBudgetItem = splits.get(0).getBudgetItem();
        if (sourceBudgetItem == null || targetBudgetItem == null) {
            return;
        }

        recordPairing(sourceBudgetItem, targetBudget, targetBudgetItem);
        view.say("Learned that transfers against '" + sourceBudgetItem.getPayee() + "' belong to '" +
                targetBudgetItem.getPayee() + "' in " + targetBudget.getName() +
                ". This will not be asked again.");
    }


    /*
     * Lifecycle (section 4).
     */

    /**
     * Delete the counterparts of a transaction that is being deleted.
     *
     * <p>The expectation only exists because the source transaction did.  If the source goes away
     * before the far import, so must the expectation, or it ages in the counterparty's forecast as a
     * transfer that is never going to arrive.
     *
     * @param transaction the source transaction being deleted
     * @return the number of counterparts removed
     */
    public int deleteCounterpartsFor(Transaction transaction) throws Exception {

        if (transaction == null || transaction.getId() == null) {
            return 0;
        }

        List<ForecastTransaction> counterparts =
                ForecastTransaction.getCounterpartsOfSourceTransaction(transaction.getId());
        for (ForecastTransaction counterpart : counterparts) {
            delete(counterpart);
        }
        if (!counterparts.isEmpty()) {
            view.say("Removed " + counterparts.size() + " expected transfer counterpart(s) that this transaction " +
                    "had created in another register's forecast.");
        }
        return counterparts.size();
    }

    /**
     * Bring the counterparts of a transaction back into line after its amount or date was edited.
     *
     * <p>A counterpart carries the negated amount and the same date as its source, so an edit to
     * either has to reach it -- otherwise the far import scores against a stale expectation.
     *
     * <p>When the source has a single counterpart its amount follows the whole transaction.  When it
     * has several (one per ad-hoc split), only the date is corrected:  how a changed total should be
     * redistributed across splits is a question for the split editor, not for this.
     *
     * @param transaction the source transaction that was edited
     * @return the number of counterparts updated
     */
    public int updateCounterpartsFor(Transaction transaction) throws Exception {

        if (transaction == null || transaction.getId() == null) {
            return 0;
        }

        List<ForecastTransaction> counterparts =
                ForecastTransaction.getCounterpartsOfSourceTransaction(transaction.getId());
        if (counterparts.isEmpty()) {
            return 0;
        }

        int updated = 0;
        for (ForecastTransaction counterpart : counterparts) {
            boolean changed = false;

            if (transaction.getDate() != null &&
                    Utility.daysBetween(counterpart.getPlannedDate(), transaction.getDate()) != 0) {
                counterpart.setPlannedDate((Calendar) transaction.getDate().clone());
                changed = true;
            }

            if (counterparts.size() == 1) {
                double expectedAmount = -transaction.getAmount();
                if (!Utility.isEqualCurrency(counterpart.getRemainingAmount(), expectedAmount)) {
                    counterpart.setRemainingAmount(expectedAmount);
                    changed = true;
                }
            }

            if (changed) {
                update(counterpart);
                updated++;
            }
        }

        if (updated > 0) {
            view.say("Updated " + updated + " expected transfer counterpart(s) in another register's forecast to " +
                    "match this transaction.");
        }
        return updated;
    }


    /*
     * Testability seams.  These exist so the decision logic above can be exercised without a live
     * database -- they can be overridden in test subclasses.  They are the only points at which this
     * controller writes.
     */
    protected void insert(BudgetItem budgetItem) throws Exception {
        budgetItem.save(INSERT);
    }

    protected void insert(ForecastItem forecastItem) throws Exception {
        forecastItem.save(INSERT);
    }

    protected void insert(ForecastTransaction forecastTransaction) throws Exception {
        forecastTransaction.save(INSERT);
    }

    protected void update(ForecastTransaction forecastTransaction) throws Exception {
        forecastTransaction.save(UPDATE);
    }

    protected void delete(ForecastTransaction forecastTransaction) throws Exception {
        forecastTransaction.delete();
    }

    protected void recordPairing(BudgetItem sourceBudgetItem, Budget targetBudget, BudgetItem targetBudgetItem)
            throws Exception {
        TransferBudgetItemPair.learn(sourceBudgetItem, targetBudget, targetBudgetItem);
    }


    /*
     * Helpers.
     */

    /**
     * Work out which register the other side of this transfer is in, without asking anything.
     *
     * <p>The counterparty register is <b>already resolved during import</b>:  the financial
     * institution reads the masked account number out of the payee, and falls back to
     * {@link RegisterController#resolveUnmatchedAccount} when it is absent -- which narrows by
     * account type and user, asks only when still ambiguous, and caches the answer for the session.
     * Its answer is recorded as the transaction's merchant, because transfer merchants are named
     * after the counterparty register throughout the application.  This consumes the decision the
     * import already made and adds no account-identification prompt of its own.
     *
     * @return the counterparty register, or null if this is not a transfer we can place
     */
    private Register resolveCounterpartyRegister(Transaction transaction) throws Exception {

        UUID idSourceRegister = transaction.getIdRegister();
        if (idSourceRegister == null && sessionController.getRegister() != null) {
            idSourceRegister = sessionController.getRegister().getId();
        }

        // A transfer's merchant is the counterparty register's name:
        Merchant merchant = transaction.getMerchant();
        if (merchant != null && merchant.getName() != null) {
            Register byMerchantName = Register.getByName(merchant.getName());
            if (isUsableCounterparty(byMerchantName, idSourceRegister)) {
                return byMerchantName;
            }
        }

        // Failing that, the raw payee may still carry the masked counterparty account number:
        String lastFour = MerchantUtilities.extractMaskedAccountLastFour(transaction.getPayee());
        if (lastFour != null) {
            Register byLastFour = Register.getByLastFourDigits(lastFour);
            if (isUsableCounterparty(byLastFour, idSourceRegister)) {
                return byLastFour;
            }
        }

        return null;
    }

    /**
     * Whether the counterparty register already holds the other side of this movement of money.
     *
     * <p>A transfer is one movement reported twice, so once both registers have their own copy the
     * expectation has nothing to be about.  Candidates are matched on the opposite amount within a
     * few days; when both sides carry a bank reference, a differing reference rules a candidate out
     * outright, which is the one judgement amount-and-date proximity cannot make.  A missing
     * reference is not evidence either way -- it is absent from most transfers.
     */
    protected boolean otherSideAlreadyExists(Transaction transaction, Register counterpartyRegister,
                                             String reference) throws Exception {

        List<Transaction> candidates = Transaction.findOppositeSideInRegister(
                counterpartyRegister.getId(), transaction.getAmount(), transaction.getDate(),
                OTHER_SIDE_DAY_WINDOW);

        for (Transaction candidate : candidates) {
            String candidateReference = BankReferenceNumber.extract(candidate.getPayee());
            if (BankReferenceNumber.areDifferentMovements(reference, candidateReference)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private boolean isUsableCounterparty(Register candidate, UUID idSourceRegister) {
        return candidate != null &&
                (idSourceRegister == null || !candidate.getId().equals(idSourceRegister));
    }

    /**
     * The budget item an unpaired counterpart hangs off until the pairing is learned.
     *
     * <p>One per budget, reused, and never assigned to a transaction -- the
     * {@code transferPairingUnknown} flag on the counterpart is what prevents that.  It is unplanned
     * and on-demand, so the forecast generator skips it and it never produces forecast transactions
     * of its own.
     */
    private BudgetItem getOrCreatePlaceholderBudgetItem(Budget targetBudget) throws Exception {

        List<BudgetItem> existing = BudgetItem.getUnexpiredByPayee(targetBudget, PLACEHOLDER_PAYEE);
        for (BudgetItem candidate : existing) {
            if (PLACEHOLDER_CATEGORY.equals(candidate.getCategory())) {
                return candidate;
            }
        }

        BudgetItem placeholder = new BudgetItem(targetBudget, PLACEHOLDER_PAYEE);
        placeholder.setId(UUID.randomUUID());
        placeholder.setCategory(PLACEHOLDER_CATEGORY);
        placeholder.setMemo("Holds transfers whose budget item is not yet known");
        placeholder.setPeriod(Item.PeriodType.ON_DEMAND);
        placeholder.setHowOccurs(Item.HowOccurs.UNPLANNED);
        placeholder.setHowPaid(Item.HowPaid.TRANSFER);
        placeholder.setItemType(Item.ItemType.EXPENSE);
        placeholder.setHowImportant(Item.HowImportant.DISCRETIONARY_NONESSENTIAL);
        placeholder.setAmount(0.0);
        placeholder.setRunningBalance(0.0);
        placeholder.setMinimumBalance(0.0);
        placeholder.setStartDate(Calendar.getInstance());
        placeholder.setNumberOfPayments(1);
        placeholder.setEndDate(null);
        insert(placeholder);
        return placeholder;
    }

    private String counterpartMemo(Transaction transaction) throws Exception {
        Register sourceRegister = transaction.getRegister();
        String sourceName = (sourceRegister != null) ? sourceRegister.getName() : "another register";
        String memo = "Transfer from " + sourceName;
        return (memo.length() > MEMO_MAX_LENGTH) ? memo.substring(0, MEMO_MAX_LENGTH) : memo;
    }

    /**
     * How Phase 2.5 should describe a match to a transfer counterpart, instead of its usual
     * auto-match message -- so it is obvious why no questions were asked.
     *
     * <p>Reads {@code "Taken from the corresponding transfer in Bill Pay Danni on 08-03-2026"}, and
     * names the bank's reference when the transfer carried one.
     */
    public static String describeCounterpart(ForecastTransaction counterpart) {

        StringBuilder description = new StringBuilder("Taken from the corresponding transfer");

        try {
            Transaction source = (counterpart.getIdSourceTransaction() != null)
                    ? Transaction.getById(counterpart.getIdSourceTransaction())
                    : null;
            if (source != null) {
                Register sourceRegister = source.getRegister();
                if (sourceRegister != null) {
                    description.append(" in ").append(sourceRegister.getName());
                }
                if (source.getDate() != null) {
                    description.append(" on ").append(Utility.calendarDateToStringDate(source.getDate()));
                }
            }
        } catch (Exception e) {
            // The source transaction may have been deleted since; the counterpart still describes
            // itself well enough without it, and a lookup failure must not break an import.
        }

        if (counterpart.getSourceReference() != null) {
            description.append(" (bank reference ").append(counterpart.getSourceReference()).append(")");
        }

        return description.toString();
    }

    /**
     * The counterparts of a transaction, for callers that want to report on them before acting.
     */
    public static List<ForecastTransaction> counterpartsOf(Transaction transaction) throws Exception {
        if (transaction == null || transaction.getId() == null) {
            return new ArrayList<>();
        }
        return ForecastTransaction.getCounterpartsOfSourceTransaction(transaction.getId());
    }
}
