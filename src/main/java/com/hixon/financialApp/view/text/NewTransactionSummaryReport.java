package com.hixon.financialApp.view.text;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.Item;
import com.hixon.financialApp.model.entity.Entity;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.model.register.TransactionSplit;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.base.RegisterReport;

import java.io.*;
import java.sql.SQLException;
import java.util.List;

public class NewTransactionSummaryReport extends RegisterReport {

    /*
     * Constructors:
     */
    public NewTransactionSummaryReport(Register register, List<Entity> items, File reportFile) throws FileNotFoundException {
        super(register, items, reportFile);
    }

    /*
     * Output the report:
     */
    @Override
    public void renderReportFrontMatter() {
        pw.println("New transaction summary:");
    }

    @Override
    public void renderItemRow(Entity itemEntity) throws EntityException, ForecastException, SQLException, BudgetException,
            RegisterException {

        // Cast the entity passed in to what it really is. This is required because we are using generics:
        Transaction transaction = (Transaction) itemEntity;

        // Use a short version of the date to take less space:
        String date = Utility.calendarDateToMonthDayStringDate(
                (transaction.getAuthorizationDate() != null) ? transaction.getAuthorizationDate() : transaction.getPostDate()
        );

        // Round off the amount to save space by not displaying the cents:
        String amount = Utility.formatRoundedDollarAmount(Math.abs(transaction.getAmount()));

        // Seems like we have about another 25 characters before text wrap on the iPhone 11, so get as much of the payee
        // as possible based on the length of the amount:
        String merchant = transaction.getMerchant().getName();
        int truncatedMerchantLength = 25 - amount.length();
        if (merchant.length() > truncatedMerchantLength) {
            merchant = merchant.substring(0, truncatedMerchantLength);
        }

        // Output the transaction line:
        pw.println(date + SPACE + merchant + SPACE + amount);

        // Output the splits under the payee indented one tab:
        List<TransactionSplit> splits = TransactionSplit.getSplitsForTransaction(transaction);
        if (splits != null) {
            for (TransactionSplit split : splits
            ) {

                // Warn the user if the amount is under or over what is expected, or is not in the forecast.  If the
                // amount is less that expected, prefix the split with a minus sign.  If the amount is more than
                // expected, then prefix the split with a plus sign.  If the amount was not expected at all, then
                // prefix the amount with an asterisk:
                String expectation = SPACE;
                switch (split.getBudgetItem().getHowOccurs()) {
                    case ENVELOPE:
                        // In the case of envelopes, no single split is over or under.  Only the total over a period
                        // could be more or less than expected, so there is no expectation for an envelope item entity.
                        break;

                    case PERIODIC:
                        if (!split.getBudgetItem().isWithinNormalAmountVariance(split.getAmount())) {
                            double difference = Utility.currencyDifference(split.getBudgetItem().getAmount(), split.getAmount());
                            if (difference < 0) {
                                expectation = AMOUNT_LESS_THAN_EXPECTED;
                            } else if (difference > 0) {
                                expectation = AMOUNT_MORE_THAN_EXPECTED;
                            }
                        }
                        break;

                    case UNPLANNED:
                        expectation = TRANSACTION_UNEXPECTED;
                        // In the case of unplanned items, if there is an expected amount, then add the over/under flag:
                        if (!split.getBudgetItem().isWithinNormalAmountVariance(split.getAmount())) {
                            double difference = Utility.currencyDifference(split.getBudgetItem().getAmount(), split.getAmount());
                            if (difference <= -1) {
                                expectation += AMOUNT_LESS_THAN_EXPECTED;
                            } else if (difference >= 1){
                                expectation += AMOUNT_MORE_THAN_EXPECTED;
                            }
                        }
                        break;

                    case COLLECTION:
                        // In the case of envelopes, no single split is ever "under" and is only considered "over" if the
                        // MTD amount exceeds the entire amount budgeted for that category in the current month:
                        if (Utility.currencyDifference(split.getBudgetItem().getAmountSpentMTD(),
                                split.getBudgetItem().getBudgetedAmountForCurrentMonth()) < 0) {
                            expectation = AMOUNT_MORE_THAN_EXPECTED;
                        }
                        break;

                    case VARIABLE_PERIODIC:
                        // In the case of variable periodic expenses, the split is expected, so it can't be unexpected.
                        // Furthermore, there is no expected amount for the split so it can't be over or under:
                        break;

                    default:
                        throw new BudgetException("Unknown howOccurs type in switch statement.");
                }
                pw.print(INDENT + expectation);

                // Only print the amount if it is different than the transaction amount, which only happens when there is
                // more that one split:
                String splitAmount = Utility.formatRoundedDollarAmount(Math.abs(split.getAmount()));
                splitAmount = (splits.size() > 1) ? SPACE + splitAmount : "";

                // Print as much of the split payee that will fit with the amount on an iPhone 11:
                String splitPayee = split.getBudgetItem().getPayee();
                int truncatedPayeeLength = MAX_PAYEE_LENGTH - splitAmount.length();
                if (splitPayee.length() > truncatedPayeeLength) {
                    splitPayee = splitPayee.substring(0, truncatedPayeeLength);
                }

                // Output the split line without the memo:
                String line = splitPayee + splitAmount;
                int remainingSpace = 27 - line.length();
                pw.print(line);

                // Print the planned vs. actual amounts for the month:
                double amountBudgeted = (split.getBudgetItem().getHowOccurs() == Item.HowOccurs.UNPLANNED) ?
                        split.getBudgetItem().getAmount() : split.getBudgetItem().getBudgetedAmountForCurrentMonth();
                String plannedVsActual = " (" + Utility.formatRoundedDollarAmount(Math.abs(amountBudgeted)) + "/" +
                        Utility.formatRoundedDollarAmount(Math.abs(split.getBudgetItem().getAmountSpentMTD())) + ")";
                pw.print(plannedVsActual);
                remainingSpace -= plannedVsActual.length();

                // Add the memo if there is one.
                String memo = "";
                if (split.getMemo() != null) {

                    int memoLength = split.getMemo().length();

                    // Put it on the same line if it fits:
                    if (memoLength <= remainingSpace) {
                        memo = SPACE + split.getMemo();
                    } else if (remainingSpace > 5) {
                        // truncate the memo if there is room for at least the first 6 characters:
                        memo = SPACE + split.getMemo().substring(0, remainingSpace);
                    } else {
                        //  otherwise put it on the next line:
                        int len = Math.min(split.getMemo().length(), 21);
                        memo = "\n\tMemo: " + split.getMemo().substring(0, len);
                    }
                }

                // Go to the next line:
                pw.println(memo);
            }
        }
    }

    @Override
    public void renderSummaryRow() {
        pw.println("\nCurrent Balance:  " + Utility.formatRoundedDollarAmount(register.getBalance()));
    }
}

