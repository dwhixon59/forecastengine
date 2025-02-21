package com.hixon.financialApp.view.base;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.Entity;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.view.ViewException;

import java.util.List;

public class ReportRenderer<T extends ReportRendererInt> {

    /*
     * Fields:
     */
    private final T reportObject;


    /*
     * Constructors:
     */
    public ReportRenderer(T reportObject) {
        this.reportObject = reportObject;
    }


    /*
     * Helper methods:
     */


    /*
     * Main methods:
     */
    // Render a report:
    public boolean renderReport() throws Exception, ViewException,
            EntityException, BudgetException, RegisterException {

        // Open the output and output the front matter:
        reportObject.openReportOutput();
        reportObject.renderReportFrontMatter();

        // Render the header row(s):
        reportObject.renderHeaderRow();

        // For each item in the report:
        List<Entity> items = reportObject.getItems();
        if (items != null) {
            for (Entity item : items
            ) {
                reportObject.renderItemRow(item);
            }
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
