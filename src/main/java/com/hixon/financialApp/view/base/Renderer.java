package com.hixon.financialApp.view.base;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.Entity;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.view.ViewException;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.List;

public class Renderer<T extends ViewReportInt> {

    /*
     * Fields:
     */
    private final T reportObject;


    /*
     * Constructors:
     */
    public Renderer(T reportObject) {
        this.reportObject = reportObject;
    }


    /*
     * Helper methods:
     */


    /*
     * Main methods:
     */
    // Render a report:
    public boolean renderReport() throws FileNotFoundException, UnsupportedEncodingException, ViewException,
            EntityException, ForecastException, SQLException, BudgetException, RegisterException {

        // Open the output and output the front matter:
        reportObject.openReportOutput();
        reportObject.renderReportFrontMatter();

        // Render the header row(s):
        reportObject.renderHeaderRow();

        // For each item in the report:
        List<Entity> items = reportObject.getItems();
        for (Entity item : items
        ) {
            reportObject.renderItemRow(item);
        }

        // Render the summary row:
        reportObject.renderSummaryRow();

        // Render any back matter:
        reportObject.renderReportBackMatter();

        // Close the output file:
        reportObject.closeReportOutput();

        return true;
    }
}
