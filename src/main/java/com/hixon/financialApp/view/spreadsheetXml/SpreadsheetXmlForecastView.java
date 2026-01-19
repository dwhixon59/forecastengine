package com.hixon.financialApp.view.spreadsheetXml;

import com.hixon.financialApp.controller.ControllerException;
import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.Item;
import com.hixon.financialApp.model.entity.Entity;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.model.user.UserResource;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.ViewException;
import com.hixon.financialApp.view.base.AbstractForecastView;
import com.hixon.financialApp.view.base.RowTransactionPair;
import com.hixon.financialApp.view.csv.CsvForecastView;
import com.hixon.financialApp.view.text.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.List;


public class SpreadsheetXmlForecastView extends AbstractForecastView {

    protected String longTermForecastFilename;
    protected String shortTermForecastFilename;
    protected String importForecastFilename;
    protected String encoding;
    private PrintWriter writer;
    private String lastDate = "";
    private String category;
    private String lastCategory;
    private CsvForecastView csvForecastView = null;
    private Workbook workbook;
    private Sheet sheet;
    String[] envelopeHeaders = {"Date", "Category", "Payee", "Memo", "Credit", "Debit", "Balance", "", "ID", "Version"};


    /*
     * Getters and setters:
     */
    public String getShortTermForecastFilename() {
        return shortTermForecastFilename;
    }

    public void setShortTermForecastFilename(String shortTermForecastFilename) {
        this.shortTermForecastFilename = shortTermForecastFilename;
    }

    public String getLongTermForecastFilename() {
        return longTermForecastFilename;
    }

    public void setLongTermForecastFilename(String longTermForecastFilename) {
        this.longTermForecastFilename = longTermForecastFilename;
    }

    public String getImportForecastFilename() {
        return importForecastFilename;
    }

    public void setImportForecastFilename(String importForecastFilename) {
        this.importForecastFilename = importForecastFilename;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }


    /*
     * Abstract methods:
     */
    @Override
    protected TrackingItemsOfInterestReport getTrackingItemsOfInterestReport(User user, List<Entity> items, File file) {
        return null;
    }

    @Override
    protected UpcomingItemsOfInterestReport getUpcomingItemsOfInterestReport(User user, List<Entity> items, File reportFile)
            throws FileNotFoundException {
        return null;
    }

    @Override
    protected OverdueItemsReport getOverdueItemsReport(Forecast forecast, List<Entity> items, File reportFile) {
        return null;
    }

    @Override
    protected UpcomingItemsReport getUpcomingItemsReport(Forecast forecast, List<Entity> items, File reportFile) {
        return null;
    }

    @Override
    public EnvelopeReport getEnvelopeReport(Forecast forecast, List<Entity> items, File reportFile) throws FileNotFoundException {
        return null;
    }

    @Override
    public List<UserResource> renderOverdueItemsReport(Forecast forecast) {
        return null;
    }

    @Override
    public UserResource renderOverdueItemsReport(Forecast forecast, User user) throws EntityException, ViewException {
        return null;
    }

    @Override
    public boolean renderOverdueItemsReport(Forecast forecast, User user, File file) throws EntityException, ViewException {
        return false;
    }

    @Override
    public List<UserResource> renderUpcomingItemsReport(Forecast forecast) throws EntityException, ViewException {
        return null;
    }

    @Override
    public UserResource renderUpcomingItemsReport(Forecast forecast, User user) throws EntityException, ViewException {
        return null;
    }

    @Override
    public boolean renderUpcomingItemsReport(Forecast forecast, User user, File file) throws EntityException, ViewException {
        return false;
    }

    @Override
    protected void openLongTermForecastOutput(@NotNull String reportType) throws FileNotFoundException, UnsupportedEncodingException,
            ForecastException {

        // Route this call to the appropriate method based on the report type:
        if (reportType.equals("Periodic")) {
            openPeriodicLongTermForecastOutput();
        } else if (reportType.equals("Envelope")) {
            openEnvelopLongTermForecastOutput();
        } else {
            throw new ForecastException("Unknown report type: " + reportType);
        }
    }

    @Override
    protected void renderLongTermForecastFrontMatter(@NotNull String reportType) throws ForecastException {
        // Route this call to the appropriate method based on the report type:
        if (reportType.equals("Periodic")) {
            renderPeriodicLongTermForecastFrontMatter();
        } else if (reportType.equals("Envelope")) {
            renderEnvelopeLongTermForecastFrontMatter();
        } else {
            throw new ForecastException("Unknown report type: " + reportType);
        }
    }

    @Override
    protected void renderLongTermForecastMonthHeader(@NotNull String reportType, Calendar plannedDate, double runningBalance)
            throws ForecastException {
        // Route this call to the appropriate method based on the report type:
        if (reportType.equals("Periodic")) {
            renderPeriodicLongTermForecastMonthHeader(plannedDate, runningBalance);
        } else if (reportType.equals("Envelope")) {
            renderEnvelopeLongTermForecastMonthHeader(plannedDate, runningBalance);
        } else {
            throw new ForecastException("Unknown report type: " + reportType);
        }
    }

    @Override
    protected int renderLongTermForecastTransaction(@NotNull String reportType, ForecastTransaction forecastTransaction,
                                                    double credit, double debit) throws EntityException, SQLException, ForecastException, BudgetException {
        // Route this call to the appropriate method based on the report type:
        if (reportType.equals("Periodic")) {
            return renderPeriodicForecastTransaction(forecastTransaction, credit, debit);
        } else if (reportType.equals("Envelope")) {
            return renderEnvelopeForecastTransaction(forecastTransaction, credit, debit);
        }

        // If the report type is not recognized, throw an exception:
        throw new ForecastException("Unknown report type: " + reportType);
    }

    @Override
    protected void renderLongTermForecastBackMatter(@NotNull String reportType) throws IOException, ForecastException {
        // Route this call to the appropriate method based on the report type:
        if (reportType.equals("Periodic")) {
            renderPeriodicLongTermForecastBackMatter();
        } else if (reportType.equals("Envelope")) {
            renderEnvelopeLongTermForecastBackMatter();
        } else {
            throw new ForecastException("Unknown report type: " + reportType);
        }
    }

    @Override
    protected void closeLongTermForecastOutput(String reportType) throws IOException, ForecastException {
        // Route this call to the appropriate method based on the report type:
        if (reportType.equals("Periodic")) {
            closePeriodicLongTermForecastOutput();
        } else if (reportType.equals("Envelope")) {
            closeEnvelopeLongTermForecastOutput();
        } else {
            throw new ForecastException("Unknown report type: " + reportType);
        }
    }

    @Override
    public void editLongTermForecast() throws Exception {
        throw new ViewException("SpreadsheetXML forecast editing is not supported. Use Excel view instead.");
    }

    /*
     * Constructors:
     */
    public SpreadsheetXmlForecastView() throws EntityException, SQLException {
        super(Forecast.getMostRecent());
        shortTermForecastFilename = "C:\\Users\\dwhix\\Dropbox\\Hixon Family Personal Business\\Finances\\Expenses\\" +
                "ShortTermForecast-" + forecast.getDescription().replaceAll("\\s", "");
        longTermForecastFilename = "C:\\Users\\dwhix\\Dropbox\\Hixon Family Personal Business\\Finances\\Expenses\\" +
                "LongTermForecast-" + forecast.getDescription().replaceAll("\\s", "");
        encoding = "UTF-8";
        csvForecastView = new CsvForecastView(forecast);
    }


    public SpreadsheetXmlForecastView(Forecast forecast) throws EntityException, SQLException {
        super(forecast);
        shortTermForecastFilename = "C:\\Users\\dwhix\\Dropbox\\Hixon Family Personal Business\\Finances\\Expenses\\" +
                "ShortTermForecast-" + forecast.getDescription().replaceAll("\\s", "") + ".xml";
        longTermForecastFilename = "C:\\Users\\dwhix\\Dropbox\\Hixon Family Personal Business\\Finances\\Expenses\\" +
                "LongTermForecast-" + forecast.getDescription().replaceAll("\\s", "");
        encoding = "UTF-8";
        csvForecastView = new CsvForecastView(forecast);
    }

    public SpreadsheetXmlForecastView(Forecast forecast, String shortTermForecastFilename, String longTermForecastFilename,
                                      String importForecastFilename, String encoding) throws EntityException, SQLException {
        super(forecast);
        this.forecast = Forecast.getMostRecent();
        this.shortTermForecastFilename = shortTermForecastFilename;
        this.longTermForecastFilename = longTermForecastFilename;
        this.importForecastFilename = importForecastFilename;
        this.encoding = encoding;
        firstItem = true;
        firstItemInMonth = true;
        csvForecastView = new CsvForecastView(forecast);
    }


    /*
     * Helper methods:
     */
    private static CellStyle getHeaderCellStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }


    /*
     * Main methods:
     */
    public void openPeriodicLongTermForecastOutput() throws FileNotFoundException, UnsupportedEncodingException {
        longTermForecastFilename += ".xml";
        Utility.getView().say("Long term forecast will be rendered to the file: " + longTermForecastFilename);
        Utility.versionFile(longTermForecastFilename);
        writer = new PrintWriter(longTermForecastFilename, encoding);
    }

    public void renderPeriodicLongTermForecastFrontMatter() {

        // Write the header information to the output file:
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

        // The month break row default font style:
        writer.println("\t<Style ss:ID=\"MonthRow\">");
        writer.println("\t\t<Font ss:FontName=\"Calibri\" x:Family=\"Swiss\" ss:Size=\"16\" ss:Color=\"#000000\"");
        writer.println("\t\tss:Bold=\"1\"/>");
        writer.println("\t</Style>");

        // The header row default Cell font style:
        writer.println("\t<Style ss:ID=\"HeaderRow\">");
        writer.println("\t\t<Font ss:FontName=\"Calibri\" x:Family=\"Swiss\" ss:Size=\"12\" ss:Color=\"#000000\"");
        writer.println("\t\tss:Bold=\"1\"/>");
        writer.println("\t</Style>");

        // The centered header string Cell style:
        writer.println("\t<Style ss:ID=\"CenteredHeader\">");
        writer.println("\t\t<Font ss:FontName=\"Calibri\" x:Family=\"Swiss\" ss:Size=\"11\" ss:Color=\"#000000\"");
        writer.println("\t\tss:Bold=\"1\"/>");
        writer.println("\t\t<Alignment ss:Horizontal=\"Center\" ss:Vertical=\"Bottom\"/>");
        writer.println("\t</Style>");

        // The Forecast Transaction row default Cell style:
        writer.println("\t<Style ss:ID=\"ForecastTransaction\" ss:Name=\"Normal\">");
        writer.println("\t\t<Alignment ss:Vertical=\"Bottom\"/>");
        writer.println("\t\t<Borders/>");
        writer.println("\t\t<Font ss:FontName=\"Calibri\" x:Family=\"Swiss\" ss:Size=\"11\" ss:Color=\"#000000\"/>");
        writer.println("\t\t<Interior/>");
        writer.println("\t\t<NumberFormat/>");
        writer.println("\t\t<Protection/>");
        writer.println("\t</Style>");

        // The Date column style:
        writer.println("\t<Style ss:ID=\"Date\">");
        writer.println("\t\t<Alignment ss:Horizontal=\"Center\" ss:Vertical=\"Bottom\"/>");
        writer.println("\t\t<NumberFormat ss:Format=\"&quot;$&quot;#,##0\"/>");
        writer.println("\t</Style>");

        // The Amount column style:
        writer.println("\t<Style ss:ID=\"Amount\">");
        writer.println("\t\t<NumberFormat ss:Format=\"&quot;$&quot;#,##0\"/>");
        writer.println("\t</Style>");

        // The Balance column style:
        writer.println("\t<Style ss:ID=\"Balance\">");
        writer.println("\t\t<NumberFormat ss:Format=\"&quot;$&quot;#,##0;[Red]&quot;$&quot;#,##0\"/>");
        writer.println("\t</Style>");

        // The Header Balance column style:
        writer.println("\t<Style ss:ID=\"HeaderBalance\">");
        writer.println("\t\t<Font ss:FontName=\"Calibri\" x:Family=\"Swiss\" ss:Size=\"16\" ss:Color=\"#000000\"");
        writer.println("\t\tss:Bold=\"1\"/>");
        writer.println("\t\t<NumberFormat ss:Format=\"&quot;$&quot;#,##0;[Red]&quot;$&quot;#,##0\"/>");
        writer.println("\t</Style>");

        writer.println("</Styles>");

        // Define the sheet and the table:
        writer.println("<Worksheet ss:Name=\"LongTermForecast\">");
        writer.println("\t<Table ss:DefaultRowHeight=\"15\">");

        /*
         * Define the columns that will appear in the spreadsheet:
         */
        // The planned date of the forecast transaction:
        writer.println("\t\t<Column ss:Index=\"1\" ss:AutoFitWidth=\"0\" ss:Width=\"30\"/>");

        // The category column:
        writer.println("\t\t<Column ss:Index=\"2\" ss:AutoFitWidth=\"0\" ss:Width=\"100\"/>");

        // The payee column:
        writer.println("\t\t<Column ss:Index=\"3\" ss:AutoFitWidth=\"0\" ss:Width=\"135\"/>");

        // The memo column:
        writer.println("\t\t<Column ss:Index=\"4\" ss:AutoFitWidth=\"0\" ss:Width=\"100\"/>");

        // The credit (income) column:
        writer.println("\t\t<Column ss:Index=\"5\" ss:StyleID=\"Amount\" ss:AutoFitWidth=\"0\" ss:Width=\"55\"/>");

        // The debit (expense) column:
        writer.println("\t\t<Column ss:Index=\"6\" ss:StyleID=\"Amount\" ss:AutoFitWidth=\"0\" ss:Width=\"55\"/>");

        // The running balance column:
        writer.println("\t\t<Column ss:Index=\"7\" ss:StyleID=\"Balance\" ss:AutoFitWidth=\"0\" ss:Width=\"55\"/>");

        // A blank column for spacing between a right justified column followed by a left justified column:
        writer.println("\t\t<Column ss:Index=\"8\" ss:AutoFitWidth=\"0\" ss:Width=\"20\"/>");

        // The "how important" (discretionary/essential) column:
        writer.println("\t\t<Column ss:Index=\"9\" ss:AutoFitWidth=\"0\" ss:Width=\"30\"/>");

        // The "how occurs" (discretionary/essential) column:
        writer.println("\t\t<Column ss:Index=\"10\" ss:AutoFitWidth=\"0\" ss:Width=\"30\"/>");

        // The transaction ID column:
        writer.println("\t\t<Column ss:Index=\"11\" ss:AutoFitWidth=\"0\" ss:Width=\"200\"/>");

        // The version column:
        writer.println("\t\t<Column ss:Index=\"12\" ss:StyleID=\"Amount\" ss:AutoFitWidth=\"0\" ss:Width=\"100\"/>");

        // The amount column for short form reporting:
        writer.println("\t\t<Column ss:Index=\"13\" ss:StyleID=\"Amount\" ss:AutoFitWidth=\"0\" ss:Width=\"40\"/>");
    }

    public void renderPeriodicLongTermForecastMonthHeader(Calendar plannedDate, double runningBalance) {

        writer.println("\t\t<Row ss:Height=\"25\" ss:StyleID=\"MonthRow\">");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + Utility.calendarDateToMonthYearLongDate(plannedDate) +
                "</Data></Cell>");
        writer.println("\t\t\t<Cell/><Cell/><Cell/><Cell/><Cell/>");
        if (firstItem) {
            writer.println("\t\t\t<Cell ss:StyleID=\"HeaderBalance\"><Data ss:Type=\"Number\">" + runningBalance + "</Data></Cell>");
        } else {
            writer.println("\t\t\t<Cell ss:StyleID=\"HeaderBalance\" ss:Formula=\"=R[-1]C\">" +
                    "<Data ss:Type=\"Number\"></Data></Cell>");
        }
        writer.println("\t\t</Row>");

        writer.println("\t\t<Row ss:Height=\"18.75\" ss:StyleID=\"HeaderRow\">");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">Date</Data></Cell>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">Category</Data></Cell>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">Payee</Data></Cell>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">Memo</Data></Cell>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">Credit</Data></Cell>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">Debit</Data></Cell>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">Balance</Data></Cell>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\"></Data></Cell>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">Imp</Data></Cell>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">Occ</Data></Cell>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">Transaction ID</Data></Cell>");
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">Version</Data></Cell>");
        writer.println("\t\t\t<Cell ss:StyleID=\"CenteredHeader\"><Data ss:Type=\"String\">Amt</Data></Cell>");
        writer.println("\t\t</Row>");
        firstItemInMonth = true;
    }

    public int renderPeriodicForecastTransaction(ForecastTransaction forecastTransaction, double credit, double debit)
            throws EntityException,
            SQLException, ForecastException, BudgetException {

        // Output the forecast transaction and item:
        writer.println("\t\t<Row>");

        // The planned date of this forecast transaction:
        String dateString = Integer.toString(forecastTransaction.getPlannedDate().get(Calendar.DATE));
        if (dateString.equals("1")) {
            dateString = "1st";
        } else if (dateString.equals("2")) {
            dateString = "2nd";
        } else if (dateString.equals("3")) {
            dateString = "3rd";
        } else {
            dateString = dateString + "th";
        }
        if (dateString.equals(lastDate)) {
            dateString = "";
        } else {
            lastDate = dateString;
        }
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + dateString + "</Data></Cell>");

        // The category name:
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + forecastTransaction.getForecastItem().getCategory() +
                "</Data></Cell>");

        // The payee for this forecast transaction:
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + forecastTransaction.getForecastItem().getPayee() +
                "</Data></Cell>");

        // The description for this forecast transaction:
        String memo = forecastTransaction.getMemo();
        if (memo == null) {
            memo = "";
        }
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + memo +
                "</Data></Cell>");

        // The amount for an income item (credit):
        writer.println("\t\t\t<Cell><Data ss:Type=\"Number\">" + credit + "</Data></Cell>");

        // The amount for an expense item (debit):
        writer.println("\t\t\t<Cell><Data ss:Type=\"Number\">" + debit + "</Data></Cell>");

        // The running balance:
        if (firstItem) {
            writer.println("\t\t\t<Cell ss:StyleID=\"Balance\" ss:Formula=\"=R[-2]C+RC[-2]-RC[-1]\"><Data ss:Type=\"Number\"/>" +
                    "</Cell>");
            firstItem = false;
            firstItemInMonth = false;
        } else if (firstItemInMonth) {
            writer.println("\t\t\t<Cell ss:StyleID=\"Balance\" ss:Formula=\"=R[-3]C+RC[-2]-RC[-1]\"><Data ss:Type=\"Number\">" +
                    "</Data></Cell>");
            firstItemInMonth = false;
        } else {
            writer.println("\t\t\t<Cell ss:StyleID=\"Balance\" ss:Formula=\"=R[-1]C+RC[-2]-RC[-1]\"><Data ss:Type=\"Number\">" +
                    "</Data></Cell>");
        }

        // A blank column to separate a right justified column from a left justified column:
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\"></Data></Cell>");

        // The importance (discretionary, essential, etc.):
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + Item.generateHowImportant(
                forecastTransaction.getForecastItem().getHowImportant()) + "</Data></Cell>");

        // How the transactions occur (periodic, collection, etc.):
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + Item.generateHowOccurs(
                forecastTransaction.getForecastItem().getHowOccurs()) + "</Data></Cell>");

        // Unique ID for round trip forecast transaction matching:
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + forecastTransaction.getId().toString() + "</Data></Cell>");

        // The version for round trip forecast transaction matching:
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" +
                Utility.calendarDateToLongStringDate(forecastTransaction.getVersion()) + "</Data></Cell>");

        // The amount of the transaction for short form reporting:
        writer.println("\t\t\t<Cell ss:Formula=\"=RC[-8]-RC[-7]\"><Data ss:Type=\"Number\"></Data></Cell>");

        writer.println("\t\t</Row>");
        return 0;
    }

    protected void renderPeriodicLongTermForecastBackMatter() {
        writer.println("</Table>");
        writer.println("</Worksheet>");
        writer.println("</Workbook>");
    }

    protected void closePeriodicLongTermForecastOutput() {
        writer.close();
    }


    // Envelope style report methods:
    public void openEnvelopLongTermForecastOutput() {
        longTermForecastFilename += ".xlsx";
        Utility.getView().say("Long term forecast will be rendered to the file: " + longTermForecastFilename);
        Utility.versionFile(longTermForecastFilename);
        workbook = new XSSFWorkbook();
        sheet = workbook.createSheet("Savings Envelopes");
    }

    public void renderEnvelopeLongTermForecastFrontMatter() {
        // Nothing to do here.
    }

    public void renderEnvelopeLongTermForecastMonthHeader(Calendar plannedDate, double runningBalance) {

        // Header row with just the month, year, and balance:
        Row headerRow = sheet.createRow(sheet.getLastRowNum() + 1);
        Cell cell = headerRow.createCell(0);
        cell.setCellValue(Utility.calendarDateToMonthYearLongDate(plannedDate));
        cell.setCellStyle(getHeaderCellStyle(workbook));

        // if this is not the first month, then sum the amounts in the previous month:
        if (!firstMonth) {
            headerRow.createCell(4).setCellFormula("SUM(E" + firstItemInMonthRowNum + ":E" + (headerRow.getRowNum()) + ")");
            headerRow.createCell(5).setCellFormula("SUM(F" + firstItemInMonthRowNum + ":F" + (headerRow.getRowNum()) + ")");
            headerRow.createCell(6).setCellFormula("SUM(G" + firstItemInMonthRowNum + ":G" + (headerRow.getRowNum()) + ")");
        }

        // Header row with the column headers:
        headerRow = sheet.createRow(sheet.getLastRowNum() + 1);
        for (int i = 0; i < envelopeHeaders.length; i++) {
            cell = headerRow.createCell(i);
            cell.setCellValue(envelopeHeaders[i]);
            cell.setCellStyle(getHeaderCellStyle(workbook));
        }

        // Set the first item in the month flag indicating that a special formula is needed for the first item in the month:
        firstItemInMonth = true;
        firstItemInMonthRowNum = headerRow.getRowNum() + 2;
    }

    public int renderEnvelopeForecastTransaction(ForecastTransaction forecastTransaction, double credit, double debit)
            throws EntityException,
            SQLException, ForecastException, BudgetException {

        // Create a new row:
        Row row = sheet.createRow(sheet.getLastRowNum() + 1);

        // The planned date of this forecast transaction:
        String dateString = Integer.toString(forecastTransaction.getPlannedDate().get(Calendar.DATE));
        if (dateString.charAt(dateString.length() - 1) == '1') {
            dateString = dateString + "st";
        } else if (dateString.charAt(dateString.length() - 1) == '2') {
            dateString = dateString + "nd";
        } else if (dateString.charAt(dateString.length() - 1) == '3') {
            dateString = dateString + "rd";
        } else {
            dateString = dateString + "th";
        }
        if (dateString.equals(lastDate)) {
            dateString = "";
        } else {
            lastDate = dateString;
        }
        row.createCell(0).setCellValue(dateString);

        // The category name:
        row.createCell(1).setCellValue(forecastTransaction.getForecastItem().getCategory());

        // The payee for this forecast transaction:
        row.createCell(2).setCellValue(forecastTransaction.getForecastItem().getPayee());

        // The description for this forecast transaction:
        String memo = forecastTransaction.getMemo();
        if (memo == null) {
            memo = "";
        }
        row.createCell(3).setCellValue(memo);

        // The amount added to the envelope in the current period of the item (credit):
        row.createCell(4).setCellValue(forecastTransaction.getForecastItem().getAmount());

        // The amount spent on the item in the current period (debit):
        row.createCell(5).setCellValue(forecastTransaction.getForecastItem().getAmount() -
                forecastTransaction.getRemainingAmount());

        // The formula for the balance of the envelope is the balance from the previous instance of a forecast
        // transaction, for the same forecast item (if there is one) plus the credit amount minus the debit amount:
        List<RowTransactionPair> rowTransactionPairList
                = forecastItemToRowsMap.get(forecastTransaction.getForecastItem().getId());

        if (rowTransactionPairList != null) {
            if (rowTransactionPairList.isEmpty()) {
                throw new ForecastException("The rowTransactionPairList is empty for the forecast item: " +
                        forecastTransaction.getForecastItem().getId());
            }
            row.createCell(6).setCellFormula(
                    "G" + (rowTransactionPairList.get(rowTransactionPairList.size() - 1).getRowNum() + 1) +
                    "+E" + (row.getRowNum() + 1) +
                    "-F" + (row.getRowNum() + 1)
            );
        }
        else {
            row.createCell(6).setCellFormula(
                    "E" + (row.getRowNum() +1) +
                    "-F" + (row.getRowNum() + 1)
            );
        }

        // A blank column to separate a right justified column from a left justified column:
        row.createCell(7).setCellValue("");

        // Unique ID for round trip forecast transaction matching:
        row.createCell(8).setCellValue(forecastTransaction.getId().toString());

        // The version for round trip forecast transaction matching:
        row.createCell(9).setCellValue(Utility.calendarDateToLongStringDate(forecastTransaction.getVersion()));

        // Return the row number:
        return row.getRowNum();
    }

    protected void renderEnvelopeLongTermForecastBackMatter() throws IOException {

        // Auto-size columns
        for (int i = 0; i < envelopeHeaders.length; i++) {
            sheet.autoSizeColumn(i);
        }

        // Hide the ID and Version columns:
        sheet.setColumnHidden(8, true);
        sheet.setColumnHidden(9, true);

        // Write the workbook to the output file:
        try (FileOutputStream outputStream = new FileOutputStream(longTermForecastFilename)) {
            workbook.write(outputStream);
        }
    }

    protected void closeEnvelopeLongTermForecastOutput() throws IOException {

        // Close the workbook:
        workbook.close();
    }

    @Override
    // For now, defer to the CSV view for importing forecast transactions:
    public List<ForecastTransaction> openForecastTransactionSource(String sourceName) throws IOException, ControllerException,
            BudgetException {
        return csvForecastView.openForecastTransactionSource(sourceName);
    }

    // For now, defer to the CSV view for importing forecast transactions:
    @Override
    public void closeForecastTransactionSource(String sourceName) throws ViewException {
        csvForecastView.closeForecastTransactionSource(sourceName);
    }
}
