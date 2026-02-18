package com.hixon.financialApp.view.spreadsheetXml;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.budget.TransactionSplit;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.ViewException;
import com.hixon.financialApp.view.base.*;
import org.apache.commons.text.StringEscapeUtils;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.Calendar;

/**
 * XML spreadsheet implementation of budget view functionality for Microsoft Excel.
 * <p>
 * This class generates two types of budget reports in SpreadsheetML (XML) format that can be
 * opened directly in Microsoft Excel:
 * <ul>
 *   <li><b>Spending Report</b> - A detailed month-to-date spending report showing budget items,
 *       categories, actual vs. planned amounts, and individual transaction splits</li>
 *   <li><b>Budget Summary Report</b> - A comprehensive annual budget summary with planned vs. actual
 *       comparisons, percentage analyses, and financial health indicators</li>
 * </ul>
 *
 * <h2>Report Features</h2>
 * Both reports include:
 * <ul>
 *   <li>Professional formatting with custom styles (fonts, colors, number formats)</li>
 *   <li>Auto-sized columns optimized for readability</li>
 *   <li>Hierarchical organization by budget category and item</li>
 *   <li>Currency and percentage formatting</li>
 *   <li>Hidden detail rows for transaction splits</li>
 *   <li>Summary sections with financial analysis</li>
 * </ul>
 *
 * <h2>File Output</h2>
 * The class writes reports to XML files using the Office Open XML SpreadsheetML format,
 * which maintains compatibility with Microsoft Excel 2003 and later versions. Default
 * file locations are user-specific and include timestamps for version tracking.
 *
 * <h2>Architecture</h2>
 * This class extends {@link AbstractBudgetView} and implements the template methods defined
 * in the base class, providing XML-specific rendering logic. It follows the MVC pattern
 * where this class represents a view implementation for budget data.
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * Budget budget = // ... obtain budget instance
 * SpreadsheetXmlBudgetView view = new SpreadsheetXmlBudgetView(budget);
 * view.setSpendingReportFilename("C:\\Reports\\Spending_Nov2025.xml");
 *
 * // Generate spending report
 * view.openSpendingReportOutput();
 * view.renderSpendingReportFrontMatter();
 * // ... render budget items and transactions
 * view.renderSpendingReportBackMatter();
 * view.closeSpendingReportOutput();
 * }</pre>
 *
 * @author David Hixon
 * @version 1.0
 * @since 1.0
 * @see AbstractBudgetView
 * @see BudgetViewInt
 */
public class SpreadsheetXmlBudgetView extends AbstractBudgetView {
    //private static final Logger logger = LogManager.getLogger(SpreadsheetXmlBudgetView.class);

    /*
     * Fields:
     */

    /** The PrintWriter used to write XML content to the output file. */
    private PrintWriter writer;

    /** The current budget category being processed. */
    private String category;

    /** The previous budget category, used to detect category changes. */
    private String lastCategory;

    /** The file path for the spending report output. */
    private String spendingReportFilename;

    /** The character encoding used for output files (default: UTF-8). */
    private String encoding;

    /** The file path for the budget summary report output. */
    private String budgetSummaryReportFilename;

    /** Flag indicating whether this is the first category in the report (used for spacing). */
    private boolean firstCategory = true;


    /*
     * Getters and setters:
     */

    /**
     * Gets the file path for the spending report output.
     *
     * @return the spending report filename with full path
     */
    public String getSpendingReportFilename() {
        return spendingReportFilename;
    }

    /**
     * Sets the file path for the spending report output.
     *
     * @param spendingReportFilename the spending report filename with full path
     */
    public void setSpendingReportFilename(String spendingReportFilename) {
        this.spendingReportFilename = spendingReportFilename;
    }

    /**
     * Gets the character encoding used for output files.
     *
     * @return the character encoding (e.g., "UTF-8")
     */
    public String getEncoding() {
        return encoding;
    }

    /**
     * Sets the character encoding used for output files.
     *
     * @param encoding the character encoding (e.g., "UTF-8")
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    /**
     * Constructs a new SpreadsheetXmlBudgetView with default file paths and settings.
     * <p>
     * Default settings:
     * <ul>
     *   <li>Spending report filename includes current month and year</li>
     *   <li>Budget summary report uses a fixed filename</li>
     *   <li>Character encoding set to UTF-8</li>
     *   <li>Output directory points to user's OneDrive folder</li>
     * </ul>
     *
     * @param budget the Budget object containing budget data to be rendered
     */


    public SpreadsheetXmlBudgetView(Budget budget) {
        super(budget);
        this.spendingReportFilename = "C:\\Users\\dwhix\\OneDrive\\Shared Data\\Hixon Family Personal Business\\Finances\\Expenses\\" +
                "SpendingReport_" + Utility.calendarDateToMonthYearDate(Calendar.getInstance()) + ".xml";
        this.budgetSummaryReportFilename = "C:\\Users\\dwhix\\OneDrive\\Shared Data\\Hixon Family Personal Business\\Finances\\Expenses\\" +
                "BudgetSummaryReport.xml";
        this.encoding = "UTF-8";
    }


    /*
     * Main methods:
     */

    /**
     * Opens the output file for the spending report and initializes the writer.
     * <p>
     * This method performs the following actions:
     * <ul>
     *   <li>Notifies the user of the target file location</li>
     *   <li>Creates a versioned backup of any existing file with the same name</li>
     *   <li>Opens a PrintWriter to the target file</li>
     *   <li>Initializes category tracking variables</li>
     *   <li>Provides retry logic if file creation fails</li>
     * </ul>
     *
     * If an error occurs during file creation, the user is prompted to retry or abort.
     * If the user chooses to abort, a ViewException is thrown with the underlying cause.
     *
     * @throws FileNotFoundException if the file path is invalid or inaccessible
     * @throws UnsupportedEncodingException if the specified encoding is not supported
     * @throws ViewException if the user aborts after a file creation error
     * @see #closeSpendingReportOutput()
     */
    public void openSpendingReportOutput() throws FileNotFoundException, UnsupportedEncodingException, ViewException {
        com.hixon.financialApp.utility.Utility.getView().say("MTD Spending Report will be rendered to the file: "
                + spendingReportFilename);
        Boolean done = false;
        while (!done) {
            done = true;
            try {
                Utility.versionFile(spendingReportFilename);
                writer = new PrintWriter(spendingReportFilename, encoding);
                category = " ";
                lastCategory = " ";
            } catch (Exception e) {
                Utility.getView().say(e.getMessage());
                done = !Utility.getView().getYesOrNo("Do you want to try again?");
                if (done) {
                    Utility.getView().say("Aborting spending report generation process.");
                    ViewException ve = new ViewException("Unable to open the spending report export file.");
                    ve.initCause(e);
                    throw ve;
                }
            }
        }
    }

    /**
     * Renders the front matter (header and styles) for the spending report.
     * <p>
     * This method generates the XML declaration, workbook definition, and all style
     * definitions needed for the spending report. The following styles are defined:
     * <ul>
     *   <li><b>TimePeriod</b> - Large bold font for the report title</li>
     *   <li><b>HeaderRow</b> - Bold, centered text for column headers</li>
     *   <li><b>CategoryRow</b> - Bold text for budget categories</li>
     *   <li><b>BudgetItemRow</b> - Regular text for budget items</li>
     *   <li><b>SplitRow</b> - Regular text for transaction splits</li>
     *   <li><b>Date</b> - Centered, short date format</li>
     *   <li><b>Amount</b> - Currency format with dollar sign</li>
     * </ul>
     *
     * Column widths are also configured:
     * <ul>
     *   <li>Category/Item: 110pt</li>
     *   <li>Budgeted Amount: 55pt (currency format)</li>
     *   <li>Actual Amount: 55pt (currency format)</li>
     *   <li>Date: 60pt (date format)</li>
     *   <li>Merchant: 110pt</li>
     *   <li>Memo: 110pt</li>
     * </ul>
     *
     * @see #renderSpendingReportBackMatter()
     */
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

    /**
     * Renders the time period row showing the date range of the spending report.
     * <p>
     * This row appears at the top of the report with large, bold text indicating
     * the reporting period (e.g., "Spending Report for the Time Period of 11/01/2025 to 11/30/2025").
     * The row height is set to 25pt for prominence.
     *
     * @param startDate the beginning date of the reporting period
     * @param endDate the ending date of the reporting period
     */
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

    /**
     * Renders the column header row for the spending report.
     * <p>
     * Outputs a bold, centered header row containing:
     * Category/Item | Budgeted Amount | Actual Amount | Date | Merchant | Memo
     * The row height is set to 36pt with wrapped text enabled.
     */
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

    /**
     * Renders a single budget item row in the spending report.
     * <p>
     * If the budget item belongs to a new category, a category header row is
     * inserted first. The budget item row shows the payee name, budgeted amount,
     * and actual amount spent during the reporting period. A blank row is added
     * above each budget item for visual separation.
     *
     * @param budgetItem the budget item to render
     * @param startDate the start date of the reporting period (not currently used in rendering)
     * @param endDate the end date of the reporting period (not currently used in rendering)
     * @param plannedAmount the budgeted amount for this item in the period
     * @param actualAmount the actual amount spent on this item in the period
     * @throws ForecastException if an error occurs accessing forecast data
     * @throws EntityException if an error occurs accessing entity data
     * @throws BudgetException if an error occurs accessing budget data
     */
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

    /**
     * Renders a transaction split detail row in the spending report.
     * <p>
     * Transaction splits show the individual transactions that contribute to a budget
     * item's actual spending. The row displays the split amount, transaction date,
     * merchant name, and optional memo. The first two columns are left blank to
     * visually indent the split under its parent budget item.
     * <p>
     * If the hide parameter is true, the row is marked as hidden in Excel, allowing
     * users to collapse detailed transaction information.
     *
     * @param split the transaction split to render
     * @param hide if true, the row will be hidden by default in Excel
     * @throws EntityException if an error occurs accessing entity data
     * @throws SQLException if a database error occurs
     * @throws RegisterException if an error occurs accessing register/transaction data
     */
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

    /**
     * Renders the summary/total row at the bottom of the spending report.
     * <p>
     * This section displays two summary lines:
     * <ul>
     *   <li>Total budgeted spending vs. actual spending with over/under amount</li>
     *   <li>Total budgeted income vs. actual income with over/under amount</li>
     * </ul>
     * The summary section is preceded by a blank row and uses the large bold font style.
     *
     * @param budgetedIncome the total budgeted income for the period
     * @param actualIncome the total actual income for the period
     * @param budgetedSpending the total budgeted spending for the period
     * @param actualSpending the total actual spending for the period
     */
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

    /**
     * Renders the closing XML tags for the spending report.
     * <p>
     * Closes the Table, Worksheet, and Workbook XML elements to properly
     * terminate the SpreadsheetML document structure.
     *
     * @see #renderSpendingReportFrontMatter()
     */
    @Override
    public void renderSpendingReportBackMatter() {
        writer.println("\t</Table>");
        writer.println("</Worksheet>");
        writer.println("</Workbook>");
    }

    /**
     * Closes the spending report output file and releases resources.
     * <p>
     * This method flushes and closes the PrintWriter, ensuring all buffered
     * content is written to disk. This method must be called after
     * {@link #renderSpendingReportBackMatter()} to complete the report generation.
     *
     * @see #openSpendingReportOutput()
     */
    @Override
    public void closeSpendingReportOutput() {
        writer.close();
    }


    /*
     * ===============================================================================
     * Budget Summary Report Methods
     * ===============================================================================
     * The following methods generate a comprehensive annual budget summary report
     * showing planned vs. actual spending and income across all budget categories
     * and items, with percentage analyses and financial health indicators.
     */

    /**
     * Opens the output file for the Budget Summary report and initializes the writer.
     * <p>
     * Similar to {@link #openSpendingReportOutput()}, this method prepares the
     * output file for the budget summary report. Unlike the spending report,
     * this file is not versioned automatically.
     *
     * @throws FileNotFoundException if the file path is invalid or inaccessible
     * @throws UnsupportedEncodingException if the specified encoding is not supported
     * @throws ViewException if the user aborts after a file creation error
     */
    @Override
    public void openBudgetSummaryReportOutput() throws FileNotFoundException, UnsupportedEncodingException, ViewException {
        com.hixon.financialApp.utility.Utility.getView().say("Budget Summary Report will be rendered to the file: "
                + budgetSummaryReportFilename);
        Boolean done = false;
        while (!done) {
            done = true;
            try {
                writer = new PrintWriter(budgetSummaryReportFilename, encoding);
                category = " ";
                lastCategory = " ";
            } catch (Exception e) {
                Utility.getView().say(e.getMessage());
                done = !Utility.getView().getYesOrNo("Do you want to try again?");
                if (done) {
                    Utility.getView().say("Aborting budget summary report generation process.");
                    ViewException ve = new ViewException("Unable to open the budget summary report export file.");
                    ve.initCause(e);
                    throw ve;
                }
            }
        }
    }

    /**
     * Renders the front matter (header and styles) for the budget summary report.
     * <p>
     * This method generates comprehensive XML structure for a detailed budget analysis
     * report. The following styles are defined:
     * <ul>
     *   <li><b>TitleRow</b> - Large bold font for the report title</li>
     *   <li><b>Summary</b> - Large bold font for summary sections</li>
     *   <li><b>HeaderRow</b> - Bold, centered text for column headers</li>
     *   <li><b>CategoryRow</b> - Bold text for budget categories</li>
     *   <li><b>BudgetItemRow</b> - Regular text for budget items</li>
     *   <li><b>SplitRow</b> - Regular text for transaction splits</li>
     *   <li><b>Date</b> - Centered, short date format</li>
     *   <li><b>Amount</b> - Currency format with dollar sign</li>
     *   <li><b>BoldAmount</b> - Bold currency format</li>
     *   <li><b>Percent</b> - Percentage format</li>
     *   <li><b>BoldPercent</b> - Bold percentage format</li>
     *   <li><b>SummaryRow</b> - Bold text for summary rows</li>
     * </ul>
     *
     * Column configuration (10 columns):
     * <ol>
     *   <li>Category/Item: 110pt</li>
     *   <li>Annual Amount: 55pt (currency)</li>
     *   <li>Annual %: 55pt (percentage)</li>
     *   <li>Planned Amount: 55pt (currency)</li>
     *   <li>Planned %: 55pt (percentage)</li>
     *   <li>Actual Amount: 55pt (currency)</li>
     *   <li>Actual %: 55pt (percentage)</li>
     *   <li>Date: 60pt (date format)</li>
     *   <li>Merchant: 110pt</li>
     *   <li>Memo: 110pt</li>
     * </ol>
     */
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

        // The date column style:
        writer.println("\t<Style ss:ID=\"Date\">");
        writer.println("\t\t<Alignment ss:Horizontal=\"Center\" ss:Vertical=\"Bottom\"/>");
        writer.println("\t\t<NumberFormat ss:Format=\"Short Date\"/>");
        writer.println("\t</Style>");

        // The amount column style:
        writer.println("\t<Style ss:ID=\"Amount\">");
        writer.println("\t\t<NumberFormat ss:Format=\"&quot;$&quot;#,##0\"/>");
        writer.println("\t</Style>");

        // The bold amount column style:
        writer.println("\t<Style ss:ID=\"BoldAmount\">");
        writer.println("\t\t<NumberFormat ss:Format=\"&quot;$&quot;#,##0\"/>");
        writer.println("\t\t<Font ss:FontName=\"Calibri\" x:Family=\"Swiss\" ss:Size=\"12\" ss:Color=\"#000000\"");
        writer.println("\t\tss:Bold=\"0\"/>");
        writer.println("\t</Style>");

        // The percent column style:
        writer.println("\t<Style ss:ID=\"Percent\">");
        writer.println("\t\t<NumberFormat ss:Format=\"0%\"/>");
        writer.println("\t</Style>");

        // The bold percent column style:
        writer.println("\t<Style ss:ID=\"BoldPercent\">");
        writer.println("\t\t<NumberFormat ss:Format=\"0%\"/>");
        writer.println("\t\t<Font ss:FontName=\"Calibri\" x:Family=\"Swiss\" ss:Size=\"12\" ss:Color=\"#000000\"");
        writer.println("\t\tss:Bold=\"0\"/>");
        writer.println("\t</Style>");

        // The summary row default cell font style:
        writer.println("\t<Style ss:ID=\"SummaryRow\">");
        writer.println("\t\t<Font ss:FontName=\"Calibri\" x:Family=\"Swiss\" ss:Size=\"12\" ss:Color=\"#000000\"");
        writer.println("\t\tss:Bold=\"1\"/>");
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

        // The annual amount column:
        writer.println("\t\t<Column ss:Index=\"2\" ss:StyleID=\"Amount\" ss:AutoFitWidth=\"0\" ss:Width=\"55\"/>");

        // The percent annual amount column:
        writer.println("\t\t<Column ss:Index=\"3\" ss:StyleID=\"Percent\" ss:AutoFitWidth=\"0\" ss:Width=\"55\"/>");

        // The planned amount column:
        writer.println("\t\t<Column ss:Index=\"4\" ss:StyleID=\"Amount\" ss:AutoFitWidth=\"0\" ss:Width=\"55\"/>");

        // The percent planned amount column:
        writer.println("\t\t<Column ss:Index=\"5\" ss:StyleID=\"Percent\" ss:AutoFitWidth=\"0\" ss:Width=\"55\"/>");

        // The actual amount column:
        writer.println("\t\t<Column ss:Index=\"6\" ss:StyleID=\"Amount\" ss:AutoFitWidth=\"0\" ss:Width=\"55\"/>");

        // The percent actual amount column:
        writer.println("\t\t<Column ss:Index=\"7\" ss:StyleID=\"Percent\" ss:AutoFitWidth=\"0\" ss:Width=\"55\"/>");

        // The date column:
        writer.println("\t\t<Column ss:Index=\"8\" ss:StyleID=\"Date\" ss:AutoFitWidth=\"0\" ss:Width=\"60\"/>");

        // The merchant column:
        writer.println("\t\t<Column ss:Index=\"9\" ss:AutoFitWidth=\"0\" ss:Width=\"110\"/>");

        // The memo column:
        writer.println("\t\t<Column ss:Index=\"10\" ss:AutoFitWidth=\"0\" ss:Width=\"110\"/>");

        //logger.debug("Exit renderBudgetSummaryReportFrontMatter()");
    }

    /**
     * Renders the title row for the budget summary report showing the date range.
     * <p>
     * Displays text like "Budget Summary Report for the Time Period of MM/DD/YYYY to MM/DD/YYYY"
     * in large, bold text with a height of 25pt.
     *
     * @param startDate the beginning date of the reporting period
     * @param endDate the ending date of the reporting period
     */
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

    /**
     * Renders the column header row for the budget summary report.
     * <p>
     * Outputs headers for the seven main data columns:
     * <ul>
     *   <li>Column 1: (blank - for category/item names)</li>
     *   <li>Column 2-3: Annual amount and percentage</li>
     *   <li>Column 4-5: Planned amount and percentage</li>
     *   <li>Column 6-7: Actual amount and percentage</li>
     * </ul>
     * Headers use bold, centered text. The firstCategory flag is reset to true
     * to properly manage spacing in subsequent category rows.
     */
    @Override
    protected void renderBudgetSummaryReportHeaderRow() {
        //logger.debug("Enter renderBudgetSummaryHeaderRow()");

        writer.println("\t\t<Row ss:StyleID=\"HeaderRow\">");
        writer.println("\t\t\t<Cell/>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">Annual</Data></Cell>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">%</Data></Cell>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">Planned</Data></Cell>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">%</Data></Cell>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">Actual</Data></Cell>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">%</Data></Cell>");
        writer.println("\t\t</Row>");

        firstCategory = true;

        //logger.debug("Exit renderBudgetSummaryHeaderRow()");
    }

    /**
     * Renders a budget category summary row in the budget summary report.
     * <p>
     * Each category row displays:
     * <ul>
     *   <li>Category name</li>
     *   <li>Forecast annual amount and percentage of total</li>
     *   <li>Planned amount in period and percentage of total</li>
     *   <li>Actual amount in period and percentage of total</li>
     * </ul>
     * Categories are separated by blank rows (except for the first category after
     * the header). The row uses bold text styling.
     *
     * @param budgetCategoryReportRow the budget category data to render
     */
    @Override
    public void renderBudgetCategoryReportRow(BudgetCategoryReportRow budgetCategoryReportRow) {

        // Output a blank line to visually separate the budget categories for each category except the one after
        // the header row:
        if (firstCategory)  {
            firstCategory = false;
        } else {
            writer.println("\t\t<Row/>");
        }

        writer.println("\t\t<Row ss:StyleID=\"CategoryRow\">");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + budgetCategoryReportRow.getBudgetCategory().getName() +
                "</Data></Cell>");
        writer.println("\t\t\t<Cell ss:StyleID=\"Amount\"><Data ss:Type=\"Number\">" +
                budgetCategoryReportRow.getForecastAnnualAmount() + "</Data></Cell>");
        writer.println("\t\t\t<Cell ss:StyleID=\"Percent\"><Data ss:Type=\"Number\">" +
                budgetCategoryReportRow.getPercentAverageAnnualAmount()/100 + "</Data></Cell>");
        writer.println("\t\t\t<Cell ss:StyleID=\"Amount\"><Data ss:Type=\"Number\">" +
                budgetCategoryReportRow.getPlannedAmountInPeriod() + "</Data></Cell>");
        writer.println("\t\t\t<Cell ss:StyleID=\"Percent\"><Data ss:Type=\"Number\">" +
                budgetCategoryReportRow.getPercentPlannedAmount()/100 + "</Data></Cell>");
        writer.println("\t\t\t<Cell ss:StyleID=\"Amount\"><Data ss:Type=\"Number\">" +
                budgetCategoryReportRow.getActualAmountInPeriod() + "</Data></Cell>");
        writer.println("\t\t\t<Cell ss:StyleID=\"Percent\"><Data ss:Type=\"Number\">" +
                budgetCategoryReportRow.getPercentActualAmount()/100 + "</Data></Cell>");
        writer.println("\t\t</Row>");
    }

    /**
     * Renders a budget item detail row in the budget summary report.
     * <p>
     * Each budget item row displays:
     * <ul>
     *   <li>Payee/item name</li>
     *   <li>Forecast annual amount and percentage of category total</li>
     *   <li>Planned amount in period and percentage of category total</li>
     *   <li>Actual amount in period and percentage of category total</li>
     * </ul>
     * Budget items appear indented under their parent category and use regular
     * (non-bold) text styling.
     *
     * @param budgetItemReportRow the budget item data to render
     */
    @Override
    public void renderBudgetItemReportRow(BudgetItemReportRow budgetItemReportRow) {

        // Output the budget item row:
        writer.println("\t\t<Row ss:StyleID=\"BudgetItemRow\">");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + budgetItemReportRow.getBudgetItem().getPayee() +
                "</Data></Cell>");
        writer.println("\t\t\t<Cell ss:StyleID=\"Amount\"><Data ss:Type=\"Number\">" +
                budgetItemReportRow.getForecastAnnualAmount() + "</Data></Cell>");
        writer.println("\t\t\t<Cell ss:StyleID=\"Percent\"><Data ss:Type=\"Number\">" +
                budgetItemReportRow.getPercentForecastAnnualAmount()/100 + "</Data></Cell>");
        writer.println("\t\t\t<Cell ss:StyleID=\"Amount\"><Data ss:Type=\"Number\">" +
                budgetItemReportRow.getPlannedAmountInPeriod() + "</Data></Cell>");
        writer.println("\t\t\t<Cell ss:StyleID=\"Percent\"><Data ss:Type=\"Number\">" +
                budgetItemReportRow.getPercentPlannedAmount()/100 + "</Data></Cell>");
        writer.println("\t\t\t<Cell ss:StyleID=\"Amount\"><Data ss:Type=\"Number\">" +
                budgetItemReportRow.getActualAmountInPeriod() + "</Data></Cell>");
        writer.println("\t\t\t<Cell ss:StyleID=\"Percent\"><Data ss:Type=\"Number\">" +
                budgetItemReportRow.getPercentActualAmount()/100 + "</Data></Cell>");
        writer.println("\t\t</Row>");
    }

    /**
     * Renders a transaction split detail row in the budget summary report.
     * <p>
     * Transaction splits provide the detailed transaction-level data supporting
     * the actual amounts shown in budget items. The row displays:
     * <ul>
     *   <li>Transaction amount (in column 6)</li>
     *   <li>Transaction post date</li>
     *   <li>Merchant name</li>
     *   <li>Optional memo text</li>
     * </ul>
     * The first five columns are left blank for visual indentation. All transaction
     * split rows are hidden by default in Excel, allowing users to expand them when
     * detail is needed.
     *
     * @param transactionSplitReportRow the transaction split data to render
     */
    @Override
    public void renderTransactionSplitReportRow(TransactionSplitReportRow transactionSplitReportRow) {

        writer.println("\t\t<Row ss:Hidden=\"1\" ss:StyleID=\"SplitRow\">");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + " " + "</Data></Cell>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + " " + "</Data></Cell>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + " " + "</Data></Cell>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + " " + "</Data></Cell>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + " " + "</Data></Cell>");
        writer.println("\t\t\t<Cell ss:StyleID=\"Amount\"><Data ss:Type=\"Number\">" +
                transactionSplitReportRow.getTransactionSplit().getAmount() + "</Data></Cell>");
        writer.println("\t\t\t<Cell ss:StyleID=\"Date\"><Data ss:Type=\"DateTime\">" + Utility.calendarDateToStringTimeStamp(
                transactionSplitReportRow.getTransaction().getPostDate()) + "</Data></Cell>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" +
                StringEscapeUtils.escapeXml11(transactionSplitReportRow.getMerchant().getName())
                + "</Data></Cell>");
        if (transactionSplitReportRow.getTransactionSplit().getMemo() != null) {
            writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" +
                    transactionSplitReportRow.getTransactionSplit().getMemo() + "</Data></Cell>");
        }
        writer.println("\t\t</Row>");
    }

    /**
     * Renders the comprehensive summary section at the bottom of the budget summary report.
     * <p>
     * This method generates a detailed financial analysis including three major sections:
     *
     * <h3>1. Next 12 Months (Budget Balance Analysis)</h3>
     * Compares planned annual income vs. planned annual spending to show the projected
     * savings/deficit if current budget allocations continue:
     * <ul>
     *   <li>Planned Income</li>
     *   <li>Planned Spending (shown as negative)</li>
     *   <li>Planned Savings (difference)</li>
     * </ul>
     *
     * <h3>2. Last 12 Months (Actual Savings Analysis)</h3>
     * Compares actual income vs. actual spending to show real financial performance:
     * <ul>
     *   <li>Actual Income</li>
     *   <li>Actual Spending (shown as negative)</li>
     *   <li>Actual Savings (difference)</li>
     * </ul>
     *
     * <h3>3. Spending Variance Analysis</h3>
     * Compares actual spending vs. planned spending to identify over/under spending:
     * <ul>
     *   <li>Actual Spending (shown as negative)</li>
     *   <li>Planned Spending (shown as negative)</li>
     *   <li>Over/Under Spending (difference)</li>
     * </ul>
     *
     * <h3>4. Income Variance Analysis</h3>
     * Compares actual income vs. planned income to identify earnings performance:
     * <ul>
     *   <li>Actual Income</li>
     *   <li>Planned Income</li>
     *   <li>Over/Under Earnings (difference)</li>
     * </ul>
     *
     * Each section is separated by blank rows and uses large bold headers for
     * section titles. Amounts are displayed in bold currency format.
     *
     * @param budgetTotalsReportRow the aggregated totals data containing all necessary
     *                               values for the summary calculations
     */
    @Override
    protected void renderBudgetSummaryReportSummary(BudgetTotalsReportRow budgetTotalsReportRow) {
        //logger.debug("Enter renderBudgetSummarySummaryRow()");
        String line;

        // Summary header row:
        writer.println("\t<Row/>");
        writer.println("\t<Row ss:Height=\"25\" ss:StyleID=\"Summary\">");
        writer.println("\t\t<Cell><Data ss:Type=\"String\">Summary Information:</Data></Cell>");
        writer.println("\t</Row>");

        // Next 12 months header row:
        writer.println("\t<Row ss:StyleID=\"SummaryRow\">");
        writer.println("\t\t<Cell><Data ss:Type=\"String\">Next 12 Months:</Data></Cell>");
        writer.println("\t</Row>");

        /*
         * Total Annual Income vs. Total Annual Spending (budget balance analysis)
         */
        // Total planned income row:
        writer.println("\t<Row ss:StyleID=\"BudgetItemRow\">");
        writer.println("\t\t<Cell><Data ss:Type=\"String\">Planned income:</Data></Cell>");
        writer.println("\t\t<Cell><Data ss:Type=\"String\">" + " " + "</Data></Cell>");
        writer.println("\t\t<Cell ss:StyleID=\"BoldAmount\"><Data ss:Type=\"Number\">" +
                budgetTotalsReportRow.getTotalAverageAnnualIncome() + "</Data></Cell>");
        writer.println("\t</Row>");

        // Total planned spending row:
        writer.println("\t<Row ss:StyleID=\"BudgetItemRow\">");
        writer.println("\t\t<Cell><Data ss:Type=\"String\">Planned Spending:</Data></Cell>");
        writer.println("\t\t<Cell><Data ss:Type=\"String\">" + " " + "</Data></Cell>");
        writer.println("\t\t<Cell ss:StyleID=\"BoldAmount\"><Data ss:Type=\"Number\">" +
                -budgetTotalsReportRow.getTotalAverageAnnualSpending() + "</Data></Cell>");
        writer.println("\t</Row>");

        // Horizontal rule:
        writer.println("\t<Row ss:StyleID=\"BudgetItemRow\">");
        writer.println("\t\t<Cell><Data ss:Type=\"String\">" + " " + "</Data></Cell>");
        writer.println("\t\t<Cell><Data ss:Type=\"String\">" + " " + "</Data></Cell>");
        writer.println("\t\t<Cell><Data ss:Type=\"String\">--------------</Data></Cell>");
        writer.println("\t</Row>");

        // Difference row:
        writer.println("\t<Row ss:StyleID=\"BudgetItemRow\">");
        writer.println("\t\t<Cell><Data ss:Type=\"String\">Planned Savings:</Data></Cell>");
        writer.println("\t\t<Cell><Data ss:Type=\"String\">" + " " + "</Data></Cell>");
        writer.println("\t\t<Cell ss:StyleID=\"BoldAmount\"><Data ss:Type=\"Number\">" +
                (budgetTotalsReportRow.getTotalAverageAnnualIncome() +
                        budgetTotalsReportRow.getTotalAverageAnnualSpending()) + "</Data></Cell>");
        writer.println("\t</Row>");

        // Blank row separator:
        writer.println("\t<Row/>");

        // Last 12 months header row:
        writer.println("\t<Row ss:StyleID=\"SummaryRow\">");
        writer.println("\t\t<Cell><Data ss:Type=\"String\">Last 12 Months:</Data></Cell>");
        writer.println("\t</Row>");

        /*
         * Actual Income vs. Actual Spending (actual savings analysis)
         */
        // Total actual income row:
        writer.println("\t<Row ss:StyleID=\"BudgetItemRow\">");
        writer.println("\t\t<Cell><Data ss:Type=\"String\">Actual Income:</Data></Cell>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + " " + "</Data></Cell>");
        writer.println("\t\t\t<Cell ss:StyleID=\"BoldAmount\"><Data ss:Type=\"Number\">" +
                budgetTotalsReportRow.getTotalActualIncomeInPeriod() + "</Data></Cell>");
        writer.println("\t</Row>");

        // Total actual spending row:
        writer.println("\t<Row ss:StyleID=\"BudgetItemRow\">");
        writer.println("\t\t<Cell><Data ss:Type=\"String\">Actual Spending:</Data></Cell>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + " " + "</Data></Cell>");
        writer.println("\t\t\t<Cell ss:StyleID=\"BoldAmount\"><Data ss:Type=\"Number\">" +
                -budgetTotalsReportRow.getTotalActualSpendingInPeriod() + "</Data></Cell>");
        writer.println("\t</Row>");

        // Horizontal rule:
        writer.println("\t<Row ss:StyleID=\"BudgetItemRow\">");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + " " + "</Data></Cell>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + " " + "</Data></Cell>");
        writer.println("\t\t<Cell><Data ss:Type=\"String\">--------------</Data></Cell>");
        writer.println("\t</Row>");

        // Difference row (actual savings):
        writer.println("\t<Row ss:StyleID=\"BudgetItemRow\">");
        writer.println("\t\t<Cell><Data ss:Type=\"String\">Actual Savings:</Data></Cell>");
        writer.println("\t\t<Cell><Data ss:Type=\"String\">" + " " + "</Data></Cell>");
        writer.println("\t\t<Cell ss:StyleID=\"Amount\"><Data ss:Type=\"Number\">" +
                (budgetTotalsReportRow.getTotalActualIncomeInPeriod() + budgetTotalsReportRow.getTotalActualSpendingInPeriod()) +
                "</Data></Cell>");
        writer.println("\t</Row>");

        // Blank row separator:
        writer.println("\t<Row/>");

        /*
         * Planned spending vs. Actual Spending (overspending)
         */
        // Total actual spending row:
        writer.println("\t<Row ss:StyleID=\"BudgetItemRow\">");
        writer.println("\t\t<Cell><Data ss:Type=\"String\">Actual Spending:</Data></Cell>");
        writer.println("\t\t<Cell><Data ss:Type=\"String\">" + " " + "</Data></Cell>");
        writer.println("\t\t<Cell ss:StyleID=\"BoldAmount\"><Data ss:Type=\"Number\">" +
                -budgetTotalsReportRow.getTotalActualSpendingInPeriod() + "</Data></Cell>");
        writer.println("\t</Row>");

        // Total planned spending row:
        writer.println("\t<Row ss:StyleID=\"BudgetItemRow\">");
        writer.println("\t\t<Cell><Data ss:Type=\"String\">Planned Spending:</Data></Cell>");
        writer.println("\t\t<Cell><Data ss:Type=\"String\">" + " " + "</Data></Cell>");
        writer.println("\t\t<Cell ss:StyleID=\"BoldAmount\"><Data ss:Type=\"Number\">" +
                -budgetTotalsReportRow.getTotalPlannedSpendingInPeriod() + "</Data></Cell>");
        writer.println("\t</Row>");

        // Horizontal rule:
        writer.println("\t<Row ss:StyleID=\"BudgetItemRow\">");
        writer.println("\t\t<Cell><Data ss:Type=\"String\">" + " " + "</Data></Cell>");
        writer.println("\t\t<Cell><Data ss:Type=\"String\">" + " " + "</Data></Cell>");
        writer.println("\t\t<Cell><Data ss:Type=\"String\">--------------</Data></Cell>");
        writer.println("\t</Row>");

        // Difference row:
        writer.println("\t<Row ss:StyleID=\"BudgetItemRow\">");
        double amount = budgetTotalsReportRow.getTotalPlannedSpendingInPeriod() -
                budgetTotalsReportRow.getTotalActualSpendingInPeriod();
        if (amount > 0) {
            writer.println("\t\t<Cell><Data ss:Type=\"String\">Over Spending</Data></Cell>");
        } else {
            writer.println("\t\t<Cell><Data ss:Type=\"String\">Under Spending</Data></Cell>");
        }
        writer.println("\t\t<Cell><Data ss:Type=\"String\">" + " " + "</Data></Cell>");
        writer.println("\t\t<Cell ss:StyleID=\"BoldAmount\"><Data ss:Type=\"Number\">" + amount + "</Data></Cell>");
        writer.println("\t</Row>");

        // Blank row separator:
        writer.println("\t<Row/>");

        /*
         * Actual Income vs. Planned Income
         */
        // Total actual income row:
        writer.println("\t<Row ss:StyleID=\"BudgetItemRow\">");
        writer.println("\t\t<Cell><Data ss:Type=\"String\">Actual Income:</Data></Cell>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + " " + "</Data></Cell>");
        writer.println("\t\t\t<Cell ss:StyleID=\"BoldAmount\"><Data ss:Type=\"Number\">" +
                budgetTotalsReportRow.getTotalActualIncomeInPeriod() + "</Data></Cell>");
        writer.println("\t</Row>");

        // Total planned income row:
        writer.println("\t<Row ss:StyleID=\"BudgetItemRow\">");
        writer.println("\t\t<Cell><Data ss:Type=\"String\">Planned income:</Data></Cell>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + " " + "</Data></Cell>");
        writer.println("\t\t\t<Cell ss:StyleID=\"BoldAmount\"><Data ss:Type=\"Number\">" +
                budgetTotalsReportRow.getTotalPlannedIncomeInPeriod() + "</Data></Cell>");
        writer.println("\t</Row>");

        // Horizontal rule:
        writer.println("\t<Row ss:StyleID=\"BudgetItemRow\">");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + " " + "</Data></Cell>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + " " + "</Data></Cell>");
        writer.println("\t\t<Cell><Data ss:Type=\"String\">--------------</Data></Cell>");
        writer.println("\t</Row>");

        // Difference row:
        writer.println("\t<Row ss:StyleID=\"BudgetItemRow\">");
        amount = budgetTotalsReportRow.getTotalActualIncomeInPeriod() -
                budgetTotalsReportRow.getTotalPlannedIncomeInPeriod();
        if (amount > 0) {
            writer.println("\t\t<Cell><Data ss:Type=\"String\">Over Earnings:</Data></Cell>");
        } else {
            writer.println("\t\t<Cell><Data ss:Type=\"String\">Under Earnings:</Data></Cell>");
        }
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + " " + "</Data></Cell>");
        writer.println("\t\t\t<Cell ss:StyleID=\"BoldAmount\"><Data ss:Type=\"Number\">" + amount + "</Data></Cell>");
        writer.println("\t</Row>");

        //logger.debug("Exit renderBudgetSummarySummaryRow()");
    }
}
