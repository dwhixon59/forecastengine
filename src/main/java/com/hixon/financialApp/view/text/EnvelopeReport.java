package com.hixon.financialApp.view.text;

import com.hixon.financialApp.model.budget.TransactionSplit;
import com.hixon.financialApp.model.entity.Entity;
import com.hixon.financialApp.model.forecast.Envelope;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.Goal;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.utility.Utility;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class EnvelopeReport extends ForecastReport {

    private final Calendar reportDate;

    public EnvelopeReport(Forecast forecast, Calendar reportDate, File reportFile) throws Exception {
        super(forecast, new ArrayList<Entity>(forecast.getEnvelopes()), reportFile);
        this.reportDate = reportDate;
    }

    @Override
    public void renderReportFrontMatter() {
        pw.println("Envelopes (" + Utility.calendarDateToStringDate(reportDate) + "):\n");
    }

    @Override
    public void renderItemRow(Entity item) throws Exception {
        // Output the details of the current envelope:
        Envelope envelope = (Envelope) item;
        double contributionAmount = envelope.getContributionAmount();
        double envelopeBalance = envelope.getEnvelopeBalance();
        double bufferTarget = envelope.getBufferTarget();
        double deficit = bufferTarget - contributionAmount;
        pw.println(envelope.getName() + ": $" + envelopeBalance + " ($" + contributionAmount + "/ $" + bufferTarget +
                " | Deficit: $" + deficit + ")");

        // Retrieve any new transactions for this envelope from the database:
        List<Transaction> envelopeTransactions = envelope.getNewTransactions();

        // If there are transactions, then output the details of each transaction:
        if (!envelopeTransactions.isEmpty()) {

            // Output the transactions for the envelope:
            for (Transaction transaction : envelopeTransactions) {

                // Output the transaction details:
                pw.println("      " + transaction.toString());

                // Output the splits for the transaction:
                List<TransactionSplit> transactionSplits = TransactionSplit.getSplitsForTransaction(transaction);
                if (transactionSplits != null) {
                    for (TransactionSplit split : transactionSplits) {
                        pw.println("            " + split.toStringConcise());
                    }
                }
            }
        }

        // Retrieve any goals from the database. A goal is a forecast transaction that is in the future and has a negative amount.
        List<Goal> envelopeGoals = envelope.getGoals(reportDate);

        // If there are goals, then output the details of each goal:
        if (!envelopeGoals.isEmpty()) {
            for (Goal goal : envelopeGoals) {
                double goalAmount = goal.getAmount();
                double goalDeficit = goalAmount - contributionAmount;
                double monthlyDeficit = goalDeficit / goal.getMonthsRemaining(reportDate);
                pw.println("  Goal: " + goal.getDescription() + " on " + Utility.calendarDateToStringDate(goal.getGoalDate()) +
                        " | Forecast: $" + goalAmount + " | Deficit: $" + goalDeficit + " (Needs +$" + Math.round(monthlyDeficit) + "/mo)");
            }
        } else {
            if (contributionAmount < bufferTarget) {
                double monthlyAddition = (bufferTarget - envelopeBalance) / forecast.getMonthsRemaining(reportDate);
                pw.println("  Buffer $" + bufferTarget + " (Below cushion - add $" + Math.round(monthlyAddition) + "/mo)");
            }
        }
        pw.println();
    }

    @Override
    public void renderSummaryRow() throws Exception {
        double totalAmount = forecast.getEnvelopes()
                .stream()
                .mapToDouble(Envelope::getAmount)
                .sum();

        double totalBuffer = forecast.getEnvelopes()
                .stream()
                .mapToDouble(Envelope::getMinimumBalance)
                .sum();

        double totalDeficit = totalBuffer - totalAmount;

        pw.println("-----------------------------");
        pw.println("Total: $" + totalAmount + " / $" + totalBuffer + " (Below cushion and deficit - $" + totalDeficit + ")");
    }
}