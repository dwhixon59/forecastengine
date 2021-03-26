package com.hixon.financialApp.view.spreadsheetXml;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.register.TransactionSplit;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.ViewException;
import com.hixon.financialApp.view.base.AbstractBudgetView;
import org.apache.commons.text.StringEscapeUtils;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.Calendar;

public class SpreadsheetXmlBudgetView extends AbstractBudgetView {
    //private static final Logger logger = LogManager.getLogger(SpreadsheetXmlBudgetView.class);

    /*
     * Fields:
     */
    private PrintWriter writer;
    private String category;
    private String lastCategory;
    private String spendingReportFilename;
    private String encoding;
    private String budgetSummaryReportFilename;


    /*
     * Getters and setters:
     */
    public String getSpendingReportFilename() {
        return spendingReportFilename;
    }

    public void setSpendingReportFilename(String spendingReportFilename) {
        this.spendingReportFilename = spendingReportFilename;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }


    public SpreadsheetXmlBudgetView(Budget budget) {
        super(budget);
        this.spendingReportFilename = "C:\\Users\\dwhix\\Dropbox\\Hixon Family Personal Business\\Finances\\Expenses\\" +
                "SpendingReport.xml";
        this.budgetSummaryReportFilename = "C:\\Users\\dwhix\\Dropbox\\Hixon Family Personal Business\\Finances\\Expenses\\" +
                "BudgetSummaryReport.xml";
        this.encoding = "UTF-8";
    }


    /*
     * Main methods:
     */
    public void openSpendingReportOutput() throws FileNotFoundException, UnsupportedEncodingException, ViewException {
        com.hixon.financialApp.utility.Utility.getResolver().say("MTD Spending Report will be rendered to the file: "
                + spendingReportFilename);
        Boolean done = false;
        while (!done) {
            done = true;
            try {
                writer = new PrintWriter(spendingReportFilename, encoding);
                category = " ";
                lastCategory = " ";
            } catch (Exception e) {
                Utility.getResolver().say(e.getMessage());
                done = !Utility.getResolver().getYesOrNo("Do you want to try again?");
                if (done) {
                    Utility.getResolver().say("Aborting spending report generation process.");
                    ViewException ve = new ViewException("Unable to open the spending report export file.");
                    ve.initCause(e);
                    throw ve;
                }
            }
        }
    }

    @Override
    public void renderSpendingReportFrontMatter() {
        //logger.debug("Enter renderSpendingReportFrontMatter()");

        /*
         * Write the header information to the output file:
         */
        writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        writer.println("<?mso-application progid=\"Excel.Sheet\"?>");
        writer.println("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\"");
        writer.println("xmlns:o=\"urn:schemas-microsoft-com:office:office\"");
        writer.println("xmlns:x=\"urn:schemas-microsoft-com:office:excel\"");
        writer.println("xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\"");
        writer.println("xmlns:html=\"http://www.w3.org/TR/REC-html40\">");


        /*
         * Define the styles that will be used in the spreadsheet:
         */
        writer.println("<Styles>");

        // The time period and summary row default cell font style:
        writer.println("\t<Style ss:ID=\"TimePeriod\">");
        writer.println("\t\t<Font ss:FontName=\"Calibri\" x:Family=\"Swiss\" ss:Size=\"16\" ss:Color=\"#000000\"");
        writer.println("\t\tss:Bold=\"1\"/>");
        writer.println("\t</Style>");

        // The header row default cell font style:
        writer.println("\t<Style ss:ID=\"HeaderRow\">");
        writer.println("\t\t<Font ss:FontName=\"Calibri\" x:Family=\"Swiss\" ss:Size=\"12\" ss:Color=\"#000000\"");
        writer.println("\t\tss:Bold=\"1\"/>");
        writer.println("\t\t<Alignment ss:Horizontal=\"Center\" ss:Vertical=\"Bottom\" ss:WrapText=\"1\"/>");
        writer.println("\t</Style>");

        // The category row default cell font style:
        writer.println("\t<Style ss:ID=\"CategoryRow\">");
        writer.println("\t\t<Font ss:FontName=\"Calibri\" x:Family=\"Swiss\" ss:Size=\"12\" ss:Color=\"#000000\"");
        writer.println("\t\tss:Bold=\"1\"/>");
        writer.println("\t</Style>");

        // The budget item row default cell font style:
        writer.println("\t<Style ss:ID=\"BudgetItemRow\">");
        writer.println("\t\t<Font ss:FontName=\"Calibri\" x:Family=\"Swiss\" ss:Size=\"12\" ss:Color=\"#000000\"");
        writer.println("\t\tss:Bold=\"0\"/>");
        writer.println("\t</Style>");

        // The split row default cell font style:
        writer.println("\t<Style ss:ID=\"SplitRow\">");
        writer.println("\t\t<Font ss:FontName=\"Calibri\" x:Family=\"Swiss\" ss:Size=\"12\" ss:Color=\"#000000\"");
        writer.println("\t\tss:Bold=\"0\"/>");
        writer.println("\t</Style>");

        // The split row date column style:
        writer.println("\t<Style ss:ID=\"Date\">");
        writer.println("\t\t<Alignment ss:Horizontal=\"Center\" ss:Vertical=\"Bottom\"/>");
        writer.println("\t\t<NumberFormat ss:Format=\"Short Date\"/>");
        writer.println("\t</Style>");

        // The split row amount column style:
        writer.println("\t<Style ss:ID=\"Amount\">");
        writer.println("\t\t<NumberFormat ss:Format=\"&quot;$&quot;#,##0\"/>");
        writer.println("\t</Style>");

        writer.println("</Styles>");


        /*
         * Define the sheet and the table:
         */
        writer.println("<Worksheet ss:Name=\"Spending Report\">");
        writer.println("\t<Table ss:DefaultRowHeight=\"15\">");


        /*
         * Define the columns that will appear in the spreadsheet:
         */
        // The category and budget item column:
        writer.println("\t\t<Column ss:Index=\"1\" ss:AutoFitWidth=\"0\" ss:Width=\"110\"/>");

        // The budgeted amount column:
        writer.println("\t\t<Column ss:Index=\"2\" ss:StyleID=\"Amount\" ss:AutoFitWidth=\"0\" ss:Width=\"55\"/>");

        // The actual amount column:
        writer.println("\t\t<Column ss:Index=\"3\" ss:StyleID=\"Amount\" ss:AutoFitWidth=\"0\" ss:Width=\"55\"/>");

        // The date column:
        writer.println("\t\t<Column ss:Index=\"4\" ss:StyleID=\"Date\" ss:AutoFitWidth=\"0\" ss:Width=\"60\"/>");

        // The merchant column:
        writer.println("\t\t<Column ss:Index=\"5\" ss:AutoFitWidth=\"0\" ss:Width=\"110\"/>");

        // The memo column:
        writer.println("\t\t<Column ss:Index=\"6\" ss:AutoFitWidth=\"0\" ss:Width=\"110\"/>");

        //logger.debug("Exit renderSpendingReportFrontMatter()");
    }

    @Override
    protected void renderTimePeriodRow(Calendar startDate, Calendar endDate) {
        //logger.debug("Enter renderSummaryRow()");

        String TimePeriodRow = "Spending Report for the Time Period of " + Utility.calendarDateToStringDate(startDate) + " to " +
                Utility.calendarDateToStringDate(endDate);
        writer.println("\t\t<Row ss:Height=\"25\" ss:StyleID=\"TimePeriod\">");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + TimePeriodRow + "</Data></Cell>");
        writer.println("\t\t</Row>");

        //logger.debug("Exit renderSummaryRow()");
    }

    @Override
    protected void renderHeaderRow() {
        //logger.debug("Enter renderHeaderRow()");

        writer.println("\t\t<Row ss:Height=\"36\" ss:StyleID=\"HeaderRow\">");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">Category/Item</Data></Cell>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">Budgeted Amount</Data></Cell>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">Actual Amount</Data></Cell>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">Date</Data></Cell>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">Merchant</Data></Cell>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">Memo</Data></Cell>");
        writer.println("\t\t</Row>");

        //logger.debug("Exit renderHeaderRow()");
    }


    @Override
    public void renderBudgetItem(BudgetItem budgetItem, Calendar startDate, Calendar endDate, double plannedAmount,
                                 double actualAmount) throws ForecastException, EntityException, BudgetException {

        // Output a blank line to visually separate the budget items:
        writer.println("\t\t<Row/>");

        // If the budget category has changed, then output a category row:
        category = budgetItem.getCategory();
        if (!category.equals(lastCategory)) {
            writer.println("\t\t<Row ss:StyleID=\"CategoryRow\">");
            writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + category + "</Data></Cell>");
            writer.println("\t\t</Row>");
            lastCategory = category;
        }

        // Output the budget item row:
        writer.println("\t\t<Row ss:StyleID=\"BudgetItemRow\">");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + budgetItem.getPayee() + "</Data></Cell>");
        writer.println("\t\t\t<Cell ss:StyleID=\"Amount\"><Data ss:Type=\"Number\">" + plannedAmount + "</Data></Cell>");
        writer.println("\t\t\t<Cell ss:StyleID=\"Amount\"><Data ss:Type=\"Number\">" + actualAmount + "</Data></Cell>");
        writer.println("\t\t</Row>");
    }

    @Override
    public void renderTransactionSplit(TransactionSplit split, boolean hide) throws EntityException, SQLException, RegisterException {
        if (hide) {
            writer.println("\t\t<Row ss:Hidden=\"1\" ss:StyleID=\"SplitRow\">");
        } else {
            writer.println("\t\t<Row ss:StyleID=\"SplitRow\">");
        }
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + " " + "</Data></Cell>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + " " + "</Data></Cell>");
        writer.println("\t\t\t<Cell ss:StyleID=\"Amount\"><Data ss:Type=\"Number\">" + split.getAmount() + "</Data></Cell>");
        writer.println("\t\t\t<Cell ss:StyleID=\"Date\"><Data ss:Type=\"DateTime\">" + Utility.calendarDateToStringTimeStamp(
                split.getTransaction().getDate()) + "</Data></Cell>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + StringEscapeUtils.escapeXml11(split.getTransaction().
                getMerchant().getName()) + "</Data></Cell>");
        if (split.getMemo() != null) {
            writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + split.getMemo() + "</Data></Cell>");
        }
        writer.println("\t\t</Row>");
    }

    @Override
    public void renderTotalRow(double budgetedIncome, double actualIncome, double budgetedSpending, double actualSpending) {
        String line;
        writer.println("\t<Row/>");
        writer.println("\t\t<Row ss:Height=\"25\" ss:StyleID=\"TimePeriod\">");
        writer.println("\t\t<Cell><Data ss:Type=\"String\">Summary Information:</Data></Cell>");
        writer.println("\t</Row>");
        writer.println("\t<Row ss:StyleID=\"CategoryRow\">");
        line = "Total budgeted spending:  " + Utility.formatDollarAmount(budgetedSpending) + ", Actual spending:  " +
                Utility.formatDollarAmount(actualSpending) + ", Over/Under:  " +
                Utility.formatDollarAmount(actualSpending - budgetedSpending);
        writer.println("\t\t<Cell><Data ss:Type=\"String\">" + line + "</Data></Cell>");
        writer.println("\t</Row>");
        writer.println("\t<Row ss:StyleID=\"CategoryRow\">");
        line = "Total budgeted income:  " + Utility.formatDollarAmount(budgetedIncome) + ", Actual income:  " +
                Utility.formatDollarAmount(actualIncome) + ", Over/Under:  " +
                Utility.formatDollarAmount(actualIncome - budgetedIncome);
        writer.println("\t\t<Cell><Data ss:Type=\"String\">" + line + "</Data></Cell>");
        writer.println("\t</Row>");
    }

    @Override
    public void renderSpendingReportBackMatter() {
        writer.println("\t</Table>");
        writer.println("</Worksheet>");
        writer.println("</Workbook>");
    }

    @Override
    public void closeSpendingReportOutput() {
        writer.close();
    }


    /*
     * Budget Summary Report methods:
     */

    /**
     * Open the output file for the Budget Summary report>
     *
     * @throws FileNotFoundException
     * @throws UnsupportedEncodingException
     * @throws ViewException
     */
    @Override
    public void openBudgetSummaryReportOutput() throws FileNotFoundException, UnsupportedEncodingException, ViewException {
        com.hixon.financialApp.utility.Utility.getResolver().say("Budget Summary Report will be rendered to the file: "
                + budgetSummaryReportFilename);
        Boolean done = false;
        while (!done) {
            done = true;
            try {
                writer = new PrintWriter(budgetSummaryReportFilename, encoding);
                category = " ";
                lastCategory = " ";
            } catch (Exception e) {
                Utility.getResolver().say(e.getMessage());
                done = !Utility.getResolver().getYesOrNo("Do you want to try again?");
                if (done) {
                    Utility.getResolver().say("Aborting budget summary report generation process.");
                    ViewException ve = new ViewException("Unable to open the budget summary report export file.");
                    ve.initCause(e);
                    throw ve;
                }
            }
        }
    }

    @Override
    protected void renderBudgetSummaryReportFrontMatter() {
        //logger.debug("Enter renderBudgetSummaryReportFrontMatter()");

        /*
         * Write the header information to the output file:
         */
        writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        writer.println("<?mso-application progid=\"Excel.Sheet\"?>");
        writer.println("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\"");
        writer.println("xmlns:o=\"urn:schemas-microsoft-com:office:office\"");
        writer.println("xmlns:x=\"urn:schemas-microsoft-com:office:excel\"");
        writer.println("xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\"");
        writer.println("xmlns:html=\"http://www.w3.org/TR/REC-html40\">");


        /*
         * Define the styles that will be used in the spreadsheet:
         */
        writer.println("<Styles>");

        // The title row default font style:
        writer.println("\t<Style ss:ID=\"TitleRow\">");
        writer.println("\t\t<Font ss:FontName=\"Calibri\" x:Family=\"Swiss\" ss:Size=\"16\" ss:Color=\"#000000\"");
        writer.println("\t\tss:Bold=\"1\"/>");
        writer.println("\t</Style>");

        // The summary row default cell font style:
        writer.println("\t<Style ss:ID=\"Summary\">");
        writer.println("\t\t<Font ss:FontName=\"Calibri\" x:Family=\"Swiss\" ss:Size=\"16\" ss:Color=\"#000000\"");
        writer.println("\t\tss:Bold=\"1\"/>");
        writer.println("\t</Style>");

        // The header row default cell font style:
        writer.println("\t<Style ss:ID=\"HeaderRow\">");
        writer.println("\t\t<Font ss:FontName=\"Calibri\" x:Family=\"Swiss\" ss:Size=\"12\" ss:Color=\"#000000\"");
        writer.println("\t\tss:Bold=\"1\"/>");
        writer.println("\t\t<Alignment ss:Horizontal=\"Center\" ss:Vertical=\"Bottom\" ss:WrapText=\"1\"/>");
        writer.println("\t</Style>");

        // The category row default cell font style:
        writer.println("\t<Style ss:ID=\"CategoryRow\">");
        writer.println("\t\t<Font ss:FontName=\"Calibri\" x:Family=\"Swiss\" ss:Size=\"12\" ss:Color=\"#000000\"");
        writer.println("\t\tss:Bold=\"1\"/>");
        writer.println("\t</Style>");

        // The budget item row default cell font style:
        writer.println("\t<Style ss:ID=\"BudgetItemRow\">");
        writer.println("\t\t<Font ss:FontName=\"Calibri\" x:Family=\"Swiss\" ss:Size=\"12\" ss:Color=\"#000000\"");
        writer.println("\t\tss:Bold=\"0\"/>");
        writer.println("\t</Style>");

        // The split row default cell font style:
        writer.println("\t<Style ss:ID=\"SplitRow\">");
        writer.println("\t\t<Font ss:FontName=\"Calibri\" x:Family=\"Swiss\" ss:Size=\"12\" ss:Color=\"#000000\"");
        writer.println("\t\tss:Bold=\"0\"/>");
        writer.println("\t</Style>");

        // The split row date column style:
        writer.println("\t<Style ss:ID=\"Date\">");
        writer.println("\t\t<Alignment ss:Horizontal=\"Center\" ss:Vertical=\"Bottom\"/>");
        writer.println("\t\t<NumberFormat ss:Format=\"Short Date\"/>");
        writer.println("\t</Style>");

        // The split row amount column style:
        writer.println("\t<Style ss:ID=\"Amount\">");
        writer.println("\t\t<NumberFormat ss:Format=\"&quot;$&quot;#,##0\"/>");
        writer.println("\t</Style>");

        writer.println("</Styles>");


        /*
         * Define the sheet and the table:
         */
        writer.println("<Worksheet ss:Name=\"Budget Summary Report\">");
        writer.println("\t<Table ss:DefaultRowHeight=\"15\">");


        /*
         * Define the columns that will appear in the spreadsheet:
         */
        // The category and budget item column:
        writer.println("\t\t<Column ss:Index=\"1\" ss:AutoFitWidth=\"0\" ss:Width=\"110\"/>");

        // The budgeted amount column:
        writer.println("\t\t<Column ss:Index=\"2\" ss:StyleID=\"Amount\" ss:AutoFitWidth=\"0\" ss:Width=\"55\"/>");

        // The actual amount column:
        writer.println("\t\t<Column ss:Index=\"3\" ss:StyleID=\"Amount\" ss:AutoFitWidth=\"0\" ss:Width=\"55\"/>");

        // The date column:
        writer.println("\t\t<Column ss:Index=\"4\" ss:StyleID=\"Date\" ss:AutoFitWidth=\"0\" ss:Width=\"60\"/>");

        // The merchant column:
        writer.println("\t\t<Column ss:Index=\"5\" ss:AutoFitWidth=\"0\" ss:Width=\"110\"/>");

        // The memo column:
        writer.println("\t\t<Column ss:Index=\"6\" ss:AutoFitWidth=\"0\" ss:Width=\"110\"/>");

        //logger.debug("Exit renderBudgetSummaryReportFrontMatter()");
    }

    @Override
    protected void renderBudgetSummaryReportTitleRow(Calendar startDate, Calendar endDate) {
        //logger.debug("Enter renderBudgetSummaryHeaderRow()");

        String titleRow = "Budget Summary Report for the Time Period of " + Utility.calendarDateToStringDate(startDate) + " to " +
                Utility.calendarDateToStringDate(endDate);
        writer.println("\t\t<Row ss:Height=\"25\" ss:StyleID=\"TitleRow\">");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + titleRow + "</Data></Cell>");
        writer.println("\t\t</Row>");

        //logger.debug("Exit renderBudgetSummaryHeaderRow()");
    }

    @Override
    protected void renderBudgetSummaryReportHeaderRow() {
        //logger.debug("Enter renderBudgetSummaryHeaderRow()");

        writer.println("\t\t<Row ss:Height=\"25\" ss:StyleID=\"TitleRow\">");
        writer.println("\t\t\t<Cell/>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">Planned</Data></Cell>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">%</Data></Cell>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">Actual</Data></Cell>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">%</Data></Cell>");
        writer.println("\t\t</Row>");

        //logger.debug("Exit renderBudgetSummaryHeaderRow()");
    }

    @Override
    protected void renderBudgetSummaryReportTotalsRow(double totalBudgetedIncome, double totalActualIncome, double
            totalBudgetedSpending, double totalActualSpending) {
        //logger.debug("Enter renderBudgetSummarySummaryRow()");
        String line;
        writer.println("\t<Row/>");
        writer.println("\t\t<Row ss:Height=\"25\" ss:StyleID=\"Summary\">");
        writer.println("\t\t<Cell><Data ss:Type=\"String\">Summary Information:</Data></Cell>");
        writer.println("\t</Row>");
        writer.println("\t<Row ss:StyleID=\"CategoryRow\">");
        line = "Total budgeted spending:  " + Utility.formatDollarAmount(totalBudgetedSpending) + ", Actual spending:  " +
                Utility.formatDollarAmount(totalActualSpending) + ", Over/Under:  " +
                Utility.formatDollarAmount(totalActualSpending - totalBudgetedSpending);
        writer.println("\t\t<Cell><Data ss:Type=\"String\">" + line + "</Data></Cell>");
        writer.println("\t</Row>");
        writer.println("\t<Row ss:StyleID=\"CategoryRow\">");
        line = "Total budgeted income:  " + Utility.formatDollarAmount(totalBudgetedIncome) + ", Actual income:  " +
                Utility.formatDollarAmount(totalActualIncome) + ", Over/Under:  " +
                Utility.formatDollarAmount(totalActualIncome - totalBudgetedIncome);
        writer.println("\t\t<Cell><Data ss:Type=\"String\">" + line + "</Data></Cell>");
        writer.println("\t</Row>");
        //logger.debug("Exit renderBudgetSummarySummaryRow()");
    }
}
