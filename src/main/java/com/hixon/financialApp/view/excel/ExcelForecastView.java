package com.hixon.financialApp.view.excel;

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
import com.hixon.financialApp.view.csv.CsvForecastView;
import com.hixon.financialApp.view.text.*;
import com.hixon.financialApp.view.text.EnvelopeReport;
import lombok.Getter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.List;

public class ExcelForecastView extends AbstractForecastView {

    protected String longTermForecastFilename;
    /*
     * Getters and setters:
     */
    @Getter
    protected String shortTermForecastFilename;
    protected String importForecastFilename;
    protected String encoding;
    private PrintWriter writer;
    private String lastDate = "";
    private boolean firstItem = true;
    private boolean firstItemInMonth;
    private String category;
    private String lastCategory;
    private CsvForecastView csvForecastView = null;
    private Workbook workbook;
    private Sheet sheet;
    String[] headers = {"Date", "Category", "Payee", "Memo", "Credit", "Debit", "Balance", "", "Imp", "Occ", "Transaction ID", "Version", "Amt"};


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


    /*
     * Constructors:
     */
    public ExcelForecastView() throws EntityException, SQLException {
        super(Forecast.getMostRecent());
        shortTermForecastFilename = "C:\\Users\\dwhix\\Dropbox\\Hixon Family Personal Business\\Finances\\Expenses\\" +
                "ShortTermForecast-" + forecast.getDescription().replaceAll("\\s", "") + ".xlsx";
        longTermForecastFilename = "C:\\Users\\dwhix\\Dropbox\\Hixon Family Personal Business\\Finances\\Expenses\\" +
                "LongTermForecast-" + forecast.getDescription().replaceAll("\\s", "") + ".xlsx";
        encoding = "UTF-8";
        csvForecastView = new CsvForecastView(forecast);
    }


    public ExcelForecastView(Forecast forecast) throws EntityException, SQLException {
        super(forecast);
        shortTermForecastFilename = "C:\\Users\\dwhix\\Dropbox\\Hixon Family Personal Business\\Finances\\Expenses\\" +
                "ShortTermForecast-" + forecast.getDescription().replaceAll("\\s", "") + ".xlsx";
        longTermForecastFilename = "C:\\Users\\dwhix\\Dropbox\\Hixon Family Personal Business\\Finances\\Expenses\\" +
                "LongTermForecast-" + forecast.getDescription().replaceAll("\\s", "") + ".xlsx";
        encoding = "UTF-8";
        csvForecastView = new CsvForecastView(forecast);
    }

    public ExcelForecastView(Forecast forecast, String shortTermForecastFilename, String longTermForecastFilename,
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
        style.setAlignment(HorizontalAlignment.LEFT);
        return style;
    }

    /**
     * Creates a cell style for currency formatting (USD, no decimals, red for negatives).
     * Format: $#,##0;[Red]-$#,##0
     *
     * @param workbook the workbook to create the style in
     * @return the configured cell style
     */
    private static CellStyle getCurrencyCellStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        // Format string: positive as $#,##0 and negative as [Red]-$#,##0
        style.setDataFormat(format.getFormat("$#,##0;[Red]-$#,##0"));
        return style;
    }


    /*
     * Main methods:
     */
    @Override
    public void openLongTermForecastOutput(String reportType) {
        Utility.getView().say("Long term forecast will be rendered to the file: " + longTermForecastFilename);
        Utility.versionFile(longTermForecastFilename);
        workbook = new XSSFWorkbook();
        sheet = workbook.createSheet("Savings Envelopes");
     }

    @Override
    public void renderLongTermForecastFrontMatter(String reportType) {
        // Nothing to do here.
    }

    @Override
    public void renderLongTermForecastMonthHeader(String reportType, Calendar plannedDate, double runningBalance) {

        // Month title row (e.g., "November - 2025")
        Row titleRow = sheet.createRow(sheet.getLastRowNum() + 1);

        // Set row height to 1.5 times the default (default is 15 points = 300 units in POI)
        titleRow.setHeightInPoints(22.5f); // 15 * 1.5 = 22.5

        String monthYear = new java.text.SimpleDateFormat("MMMM - yyyy").format(plannedDate.getTime());
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(monthYear);
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 16);
        CellStyle titleStyle = workbook.createCellStyle();
        titleStyle.setFont(titleFont);
        titleCell.setCellStyle(titleStyle);

        // Display starting balance in the same row with the same font style
        Cell startingBalanceCell = titleRow.createCell(6);
        startingBalanceCell.setCellValue(runningBalance);
        CellStyle balanceStyle = workbook.createCellStyle();
        balanceStyle.setFont(titleFont);
        DataFormat format = workbook.createDataFormat();
        balanceStyle.setDataFormat(format.getFormat("$#,##0;[Red]-$#,##0"));
        startingBalanceCell.setCellStyle(balanceStyle);

        // Header row with column names
        Row headerRow = sheet.createRow(sheet.getLastRowNum() + 1);

        // Create a font for column headers (12 point, bold)
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 12);
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFont(headerFont);
        headerStyle.setAlignment(HorizontalAlignment.LEFT);

        // Only show the first 7 columns (Date through Balance) - hide ID and Version
        for (int i = 0; i < 7; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }


        // Reset the firstItemInMonth flag for this new month
        firstItemInMonth = true;
    }

    @Override
    public int renderLongTermForecastTransaction(String reportType, ForecastTransaction forecastTransaction, double credit, double debit)
            throws EntityException,
            SQLException, ForecastException, BudgetException {

        // Create a new row:
        Row row = sheet.createRow(sheet.getLastRowNum() + 1);

        // The planned date of this forecast transaction:
        String dateString = Integer.toString(forecastTransaction.getPlannedDate().get(Calendar.DATE));
        if (dateString.equals("1")) {
            dateString = "1st";
        } else if (dateString.equals("2")){
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
        row.createCell(0).setCellValue(dateString);

        // The category name:
        row.createCell(1).setCellValue(forecastTransaction.getForecastItem().getCategory());

        // The payee for this forecast transaction:
        row.createCell(2).setCellValue(forecastTransaction.getForecastItem().getPayee());

        // The description for this forecast transaction:
        String memo = forecastTransaction.getMemo();
        if (memo == null) { memo = ""; }
        row.createCell(3).setCellValue(memo);

        // The amount for an income item (credit):
        Cell creditCell = row.createCell(4);
        creditCell.setCellValue(credit);
        creditCell.setCellStyle(getCurrencyCellStyle(workbook));

        // The amount for an expense item (debit):
        Cell debitCell = row.createCell(5);
        debitCell.setCellValue(debit);
        debitCell.setCellStyle(getCurrencyCellStyle(workbook));

        // The running balance:
        int currentRow = row.getRowNum() + 1; // Excel rows are 1-based
        Cell balanceCell = row.createCell(6);
        if (firstItem) {
            // Very first item in the entire forecast: reference starting balance 2 rows above (title row) + credit - debit
            balanceCell.setCellFormula("G" + (currentRow - 2) + "+E" + currentRow + "-F" + currentRow);
            firstItem = false;
            firstItemInMonth = false;
        } else {
            if (firstItemInMonth) {
                // First item in a new month: reference balance 3 rows above (skipping title and header) + credit - debit
                balanceCell.setCellFormula("G" + (currentRow - 3) + "+E" + currentRow + "-F" + currentRow);
                firstItemInMonth = false;
            } else {
                // Regular item: reference previous row's balance + credit - debit
                balanceCell.setCellFormula("G" + (currentRow - 1) + "+E" + currentRow + "-F" + currentRow);
            }
        }
        balanceCell.setCellStyle(getCurrencyCellStyle(workbook));

        // A blank column to separate a right justified column from a left justified column:
        row.createCell(7).setCellValue("");

        // The importance (discretionary, essential, etc.):
        row.createCell(8).setCellValue(Item.generateHowImportant(
                forecastTransaction.getForecastItem().getHowImportant()));

        // How the transactions occur (periodic, collection, etc.):
        row.createCell(9).setCellValue(Item.generateHowOccurs(
                forecastTransaction.getForecastItem().getHowOccurs()));

        // Unique ID for round trip forecast transaction matching:
        row.createCell(10).setCellValue(forecastTransaction.getId().toString());

        // The version for round trip forecast transaction matching:
        row.createCell(11).setCellValue(Utility.calendarDateToLongStringDate(forecastTransaction.getVersion()));

        // The amount of the transaction for short form reporting:
        Cell amountCell = row.createCell(12);
        amountCell.setCellFormula("E" + currentRow + "-F" + currentRow);
        amountCell.setCellStyle(getCurrencyCellStyle(workbook));

        return 0;
    }

    @Override
    protected void renderLongTermForecastBackMatter(String reportType) throws IOException {

        // Set the first column (Date) width to just fit the header "Date"
        // Width is in units of 1/256th of a character width
        // Approximate width for "Date" (4 characters) plus a bit of padding
        sheet.setColumnWidth(0, 256 * 6);  // ~6 character widths

        // Auto-size the remaining visible columns (Category through Balance)
        for (int i = 1; i < 7; i++) {
            sheet.autoSizeColumn(i);
        }

        // Hide columns 8-12 (Imp, Occ, Transaction ID, Version, Amt)
        for (int i = 8; i <= 12; i++) {
            sheet.setColumnHidden(i, true);
        }

        // Write the workbook to the output file:
        try (FileOutputStream outputStream = new FileOutputStream(longTermForecastFilename)) {
            workbook.write(outputStream);
        }
    }

    @Override
    protected void closeLongTermForecastOutput(String reportType) throws IOException {

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
