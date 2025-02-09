package com.hixon.financialApp.view.text;

import com.hixon.financialApp.model.entity.Entity;
import com.hixon.financialApp.model.forecast.Envelope;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.Goal;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.base.ViewReportInt;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class EnvelopeReport implements ViewReportInt {

    private final Forecast forecast;
    private final Calendar reportDate;
    private final StringBuilder report;

    public EnvelopeReport(Forecast forecast, Calendar reportDate, File reportFile) {
        this.forecast = forecast;
        this.reportDate = reportDate;
        this.report = new StringBuilder();
    }

    @Override
    public void openReportOutput() {
        // No file output needed for text message
    }

    @Override
    public void renderReportFrontMatter() {
        report.append("Envelopes (").append(Utility.calendarDateToStringDate(reportDate)).append("):\n\n");
    }

    @Override
    public void renderHeaderRow() {
        // No header row needed for this report
    }

    @Override
    public List<Entity> getItems() throws Exception{

        // Get a list of envelopes in the forecast:
        List<Envelope> envelopes = forecast.getEnvelopes();

        // Return a list of entities:
        return new ArrayList<>(envelopes);
    }

    @Override
    public void renderItemRow(Entity item) throws Exception {

        // Output the details of the current envelope:
        Envelope envelope = (Envelope) item;
        double currentAmount = envelope.getAmount();
        double buffer = envelope.getBufferAmount();
        double deficit = buffer - currentAmount;
        report.append(envelope.getName()).append(": $").append(currentAmount).append(" / $").append(buffer).append("\n");

        // Retrieve any goals from the database.  A goal is a forecast transaction that is in the future and has a
        // negative amount.
        List<Goal> envelopeGoals = envelope.getGoals(reportDate);

        // If there are goals, then output the details of each goal:
        if (!envelopeGoals.isEmpty()) {
            for (Goal goal : envelopeGoals) {
                double goalAmount = goal.getAmount();
                double goalDeficit = goalAmount - currentAmount;
                double monthlyDeficit = goalDeficit / goal.getMonthsRemaining(reportDate);
                report.append("  Goal: ").append(goal.getDescription())
                        .append(" by ")
                        .append(Utility.calendarDateToStringDate(goal.getGoalDate()))
                        .append(" | Forecast: $").append(goalAmount).append(" | Deficit: $").append(goalDeficit)
                        .append(" (Needs +$").append(Math.round(monthlyDeficit)).append("/mo)\n");
            }
        } else {
            if (currentAmount < buffer) {
                double monthlyAddition = (buffer - currentAmount) / forecast.getMonthsRemaining(reportDate);
                report
                    .append("  Buffer $")
                    .append(buffer).append(" (Below cushion - add $")
                    .append(Math.round(monthlyAddition)).append("/mo)\n")
                ;
            }
        }
        report.append("\n");
    }

    @Override
    public void renderSummaryRow() throws Exception {
        double totalAmount = forecast.getEnvelopes()
                .stream()
                .mapToDouble(Envelope::getAmount) // Fixed incorrect method call syntax
                .sum();

        double totalBuffer = forecast.getEnvelopes()
                .stream()
                .mapToDouble(Envelope::getBufferAmount)
                .sum();

        double totalDeficit = totalBuffer - totalAmount;

        report.append("-----------------------------\n");
        report.append("Total: $").append(totalAmount).append(" / $").append(totalBuffer).append(" (Below cushion and deficit - $").append(totalDeficit).append(")");
    }

    @Override
    public void renderReportBackMatter() {
        // No back matter needed for this report
    }

    @Override
    public void closeReportOutput() {
        // No file output to close
    }

    public String getReport() {
        return report.toString();
    }
}