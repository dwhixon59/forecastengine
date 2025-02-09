package com.hixon.financialApp.view.excel;

import com.hixon.financialApp.controller.ControllerException;
import com.hixon.financialApp.model.budget.BudgetException;
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
import com.hixon.financialApp.view.text.EnvelopeReport;
import com.hixon.financialApp.view.text.*;
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
    String[] headers = {"Date", "Category", "Payee", "Memo", "Credit", "Debit", "Balance", "", "ID", "Version"};


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
        style.setAlignment(HorizontalAlignment.CENTER);
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

        // Header row
        Row headerRow = sheet.createRow(sheet.getLastRowNum() + 1);

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(getHeaderCellStyle(workbook));
        }
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
        row.createCell(4).setCellValue(credit);

        // The amount for an expense item (debit):
        row.createCell(5).setCellValue(debit);

        // The running balance:
        if (firstItem) {
            row.createCell(6).setCellFormula("=R[-2]C+RC[-2]-RC[-1]");
            firstItem = false;
            firstItemInMonth = false;
        } else {
            if (firstItemInMonth) {
                row.createCell(6).setCellFormula("=R[-3]C+RC[-2]-RC[-1]");
                firstItemInMonth = false;
            } else {
                row.createCell(6).setCellFormula("=R[-1]C+RC[-2]-RC[-1]");
            }
        }

        // A blank column to separate a right justified column from a left justified column:
        row.createCell(7).setCellValue("");

        // Unique ID for round trip forecast transaction matching:
        row.createCell(8).setCellValue(forecastTransaction.getId().toString());

        // The version for round trip forecast transaction matching:
        row.createCell(9).setCellValue(Utility.calendarDateToLongStringDate(forecastTransaction.getVersion()));
        writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" +
                Utility.calendarDateToLongStringDate(forecastTransaction.getVersion()) + "</Data></Cell>");

        return 0;
    }

    @Override
    protected void renderLongTermForecastBackMatter(String reportType) throws IOException {

        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
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
