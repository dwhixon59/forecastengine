package com.hixon.financial.view.excel;

import com.hixon.financial.Utility;
import com.hixon.financial.model.forecast.*;
import com.hixon.financial.view.ForecastView;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Calendar;
import java.util.Locale;

public class excelView implements ForecastView {

    private LongTermForecast forecast = null;

    public excelView() {
    }

    @Override
    public void setForecast(LongTermForecast forecastToRender) {
        forecast = forecastToRender;
    }

    @Override
    public boolean render(String filename, String encoding) throws ForecastException, FileNotFoundException, UnsupportedEncodingException {

        // To clue the user into what things to look for in the spreadsheet, run the forecast summarization routine
        // requesting below minimum balance events:
        LongTermForecast.SignificantEvents[] events = {LongTermForecast.SignificantEvents.daysBelowMinimumBalance};
        forecast.summarize(events);

        // Print out the starting and ending balances:
        System.out.println("The starting balance is: " + Utility.formatDollarAmount(forecast.getStartingBalance()));
        System.out.println("The ending balance is:   " + Utility.formatDollarAmount(forecast.getEndingBalance()));
        System.out.println("The savings rate is:   " + Utility.formatDollarAmount(forecast.getEndingBalance() /
                forecast.getNumberOfMonths()) + " per month.");

        // and print out the significant events list:
        ForecastTransaction forecastTransaction = forecast.getFirstSignificantEvent();
        while (forecastTransaction != null) {
            System.out.println("The balance on " + Utility.calendarDateToStringDate(forecastTransaction.getPlannedDate()) +
                    " is $" + forecastTransaction.getRunningBalance());
            if (forecastTransaction.getRunningBalance() < forecast.getMinimumBalance()) {
                System.out.println("Balance below minimum balance!");
            }
            forecastTransaction = forecastTransaction.getNextSignificantEvent();
        }

        // Create the tab delimited file with the forecast data to import into Excel:
        PrintWriter writer = new PrintWriter(filename, encoding);
        ForecastTransactionIterator forecastTransactions = new forecastTransactionMemoryIterator();
        forecastTransactions.setForecast(forecast);
        forecastTransaction = forecastTransactions.getNext();
        int currentMonth = 0;
        while (forecastTransaction != null) {
            int amount;
            if (forecastTransaction.getAmount() == 0) {
                amount = Utility.doubleToInt(forecastTransaction.getForecastItem().getAmount());
            }
            else {
                amount = Utility.doubleToInt(forecastTransaction.getAmount());
            }
            int credit;
            int debit;
            if (amount > 0) {
                credit = amount;
                debit = 0;
            } else {
                credit = 0;
                debit = -amount;
            }
            // The month changed, so write out a header line with the name of the month:
            if (forecastTransaction.getPlannedDate().get(Calendar.MONTH) != currentMonth) {
                writer.println("\n" + forecastTransaction.getPlannedDate().getDisplayName(Calendar.MONTH, Calendar.LONG,
                        Locale.US));
                currentMonth = forecastTransaction.getPlannedDate().get(Calendar.MONTH);
            }

            // Write out the forecast line:
            writer.println(
                    Utility.calendarDateToStringDate(forecastTransaction.getPlannedDate()) + "\t" +
                    forecastTransaction.getForecastItem().getPayee() + "\t" +
                    credit + "\t" +
                    debit + "\t" +
                    Utility.doubleToInt(forecastTransaction.getRunningBalance()) + "\t" +
                    forecastTransaction.getForecastItem().getCategory() + "\t" +
                    "\t" +
                    forecastTransaction.getId().toString()
            );
            forecastTransaction = forecastTransactions.getNext();
        }
        writer.close();
        return true;
    }
}
