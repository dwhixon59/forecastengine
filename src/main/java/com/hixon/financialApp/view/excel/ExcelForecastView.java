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
import com.hixon.financialApp.view.csv.ForecastTransactionView;
import com.hixon.financialApp.view.text.*;
import com.hixon.financialApp.view.text.EnvelopeReport;
import lombok.Getter;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

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
        shortTermForecastFilename = "C:\\Users\\dwhix\\OneDrive\\Shared Data\\Hixon Family Personal Business\\Finances\\Expenses\\" +
                "ShortTermForecast-" + forecast.getDescription().replaceAll("\\s", "") + ".xlsx";
        longTermForecastFilename = "C:\\Users\\dwhix\\OneDrive\\Shared Data\\Hixon Family Personal Business\\Finances\\Expenses\\" +
                "LongTermForecast-" + forecast.getDescription().replaceAll("\\s", "") + ".xlsx";
        encoding = "UTF-8";
        csvForecastView = new CsvForecastView(forecast);
    }


    public ExcelForecastView(Forecast forecast) throws EntityException, SQLException {
        super(forecast);
        shortTermForecastFilename = "C:\\Users\\dwhix\\OneDrive\\Shared Data\\Hixon Family Personal Business\\Finances\\Expenses\\" +
                "ShortTermForecast-" + forecast.getDescription().replaceAll("\\s", "") + ".xlsx";
        longTermForecastFilename = "C:\\Users\\dwhix\\OneDrive\\Shared Data\\Hixon Family Personal Business\\Finances\\Expenses\\" +
                "LongTermForecast-" + forecast.getDescription().replaceAll("\\s", "") + ".xlsx";
        encoding = "UTF-8";
        csvForecastView = new CsvForecastView(forecast);
    }

    public ExcelForecastView(Forecast forecast, String shortTermForecastFilename, String longTermForecastFilename,
                                      String importForecastFilename, String encoding) throws EntityException, SQLException {
        super(forecast);
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

        // Reset state flags for new render
        // Without this, re-rendering after import would have wrong state from previous render
        firstItem = true;
        firstItemInMonth = true;
     }

    @Override
    public void renderLongTermForecastFrontMatter(String reportType) {
        // Nothing to do here.
    }

    @Override
    public void renderLongTermForecastMonthHeader(String reportType, Calendar plannedDate, double runningBalance) {

        // Month title row (e.g., "November - 2025")
        // Get the next row number - if sheet is empty, getPhysicalNumberOfRows() returns 0
        int nextRowNum = sheet.getPhysicalNumberOfRows();
        Row titleRow = sheet.createRow(nextRowNum);

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
        nextRowNum = sheet.getPhysicalNumberOfRows();
        Row headerRow = sheet.createRow(nextRowNum);

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
        int nextRowNum = sheet.getPhysicalNumberOfRows();
        Row row = sheet.createRow(nextRowNum);

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
    public void editLongTermForecast() throws Exception {
        if (longTermForecastFilename == null || longTermForecastFilename.trim().isEmpty()) {
            throw new ViewException("Long term forecast filename is not set. Cannot open forecast for editing.");
        }

        File forecastFile = new File(longTermForecastFilename);
        if (!forecastFile.exists()) {
            throw new ViewException("Long term forecast file does not exist: " + longTermForecastFilename);
        }

        Utility.getView().say("Opening the forecast file in Excel: " + longTermForecastFilename);
        Utility.getView().say("The process will resume after you close Excel.");

        try {
            // Open Excel and wait for it to close
            Process process = Runtime.getRuntime().exec("cmd /c start /wait excel \"" + longTermForecastFilename + "\"");
            process.waitFor();

            Utility.getView().sayH4("Excel closed. Continuing...");
        } catch (IOException | InterruptedException e) {
            throw new ViewException("Error opening forecast file in Excel: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ForecastTransaction> openForecastTransactionSource(String sourceName) throws IOException, ControllerException,
            BudgetException {
        int i = 0;
        int missingIdCount = 0;
        int totalRowsWithIds = 0;
        List<ForecastTransaction> forecastTransactions = new ArrayList<>();

        // Adjust POI's zip bomb detection threshold
        // Our Excel files have efficient compression that can trigger the default threshold
        // Set to a lower ratio (0.001) to allow our files while still protecting against actual attacks
        ZipSecureFile.setMinInflateRatio(0.001);

        Path sourcePath = Paths.get(sourceName);
        if (!Files.exists(sourcePath)) {
            throw new ControllerException("Excel file not found: " + sourceName);
        }

        try (RandomAccessFile raf = Utility.openFileWithRetry(sourcePath);
             FileInputStream fis = new FileInputStream(raf.getFD());
             Workbook workbook = new XSSFWorkbook(fis)) {

            Utility.getView().say("\nUpdate the forecast from the forecast transactions in the Excel file " + sourceName);

            // Get the first sheet
            Sheet sheet = workbook.getSheetAt(0);

            // Find the header row (look for "Date" in column A)
            int headerRowNum = -1;
            for (Row row : sheet) {
                Cell firstCell = row.getCell(0);
                if (firstCell != null && firstCell.getCellType() == CellType.STRING
                    && firstCell.getStringCellValue().equalsIgnoreCase("Date")) {
                    headerRowNum = row.getRowNum();
                    break;
                }
            }

            if (headerRowNum == -1) {
                throw new ControllerException("Could not find header row in Excel file. Expected 'Date' in first column.");
            }

            // Iterate over rows starting after the header
            Calendar plannedDate = Calendar.getInstance();
            for (int rowNum = headerRowNum + 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null) continue;

                i++;

                // Get the date cell
                Cell dateCell = row.getCell(0);
                if (dateCell != null) {
                    String dateStr = getCellValueAsString(dateCell);
                    if (!dateStr.isEmpty()) {
                        // Check if this is a month header row
                        try {
                            plannedDate = Utility.MonthYearLongDateToCalendarDate(dateStr);
                            continue;
                        } catch (Exception pe) {
                            // Not a month header, try to parse as day of month
                            if (dateStr.matches("[0-9]{1,2}(st|nd|rd|th)")) {
                                int length = dateStr.length() - 2;
                                plannedDate.set(Calendar.DATE, Integer.parseInt(dateStr.substring(0, length)));
                            } else {
                                // This row doesn't have a valid date format, skip it
                                continue;
                            }
                        }
                    }
                }

                // Get payee - if empty, skip this row
                Cell payeeCell = row.getCell(2);
                String payee = getCellValueAsString(payeeCell);
                if (payee.isEmpty()) {
                    continue;
                }

                // Parse the transaction ID first (Column 10)
                String transactionIdStr = getCellValueAsString(row.getCell(10));
                UUID transactionId = null;
                if (!transactionIdStr.isEmpty()) {
                    totalRowsWithIds++;
                    try {
                        transactionId = UUID.fromString(transactionIdStr);
                    } catch (IllegalArgumentException e) {
                        // Invalid UUID format - warn and skip this row
                        Utility.getView().say("WARNING: Row " + (rowNum + 1) + ": Invalid transaction ID format '" +
                                transactionIdStr + "'. Skipping this row.");
                        continue;
                    }
                }

                // Create ForecastTransactionView
                ForecastTransactionView forecastTransactionView;

                if (transactionId == null) {
                    // No ID provided - create new forecast transaction
                    forecastTransactionView = new ForecastTransactionView();
                    forecastTransactionView.getForecastItem().setForecast(forecast);
                } else {
                    // ID provided - load from database
                    ForecastTransaction dbForecastTransaction = ForecastTransaction.getById(transactionId);
                    if (dbForecastTransaction == null) {
                        // ID not found in database - track and warn
                        missingIdCount++;
                        Utility.getView().say("WARNING: Row " + (rowNum + 1) + ": Transaction ID '" +
                                transactionId + "' not found in database. Skipping this row.");
                        continue;
                    }

                    // Found in database - create view from existing transaction
                    forecastTransactionView = new ForecastTransactionView();
                    // Copy all fields from database transaction to use as defaults
                    forecastTransactionView.setId(dbForecastTransaction.getId());
                    forecastTransactionView.setPlannedDate(dbForecastTransaction.getPlannedDate());
                    forecastTransactionView.setRemainingAmount(dbForecastTransaction.getRemainingAmount());
                    forecastTransactionView.setRunningBalance(dbForecastTransaction.getRunningBalance());
                    forecastTransactionView.setVersion(dbForecastTransaction.getVersion());
                    forecastTransactionView.setMemo(dbForecastTransaction.getMemo());
                    forecastTransactionView.setOverridden(dbForecastTransaction.isOverridden());
                    forecastTransactionView.setFound(dbForecastTransaction.isFound());
                    forecastTransactionView.setIdForecastItem(dbForecastTransaction.getIdForecastItem());
                    forecastTransactionView.setForecastItem(dbForecastTransaction.getForecastItem());
                }

                // Now overwrite with values from Excel spreadsheet

                // Planned date
                forecastTransactionView.setDate((Calendar) plannedDate.clone());

                // Column 1: Category
                String category = getCellValueAsString(row.getCell(1));
                if (!category.isEmpty()) {
                    forecastTransactionView.setCategory(category);
                }

                // Column 2: Payee
                forecastTransactionView.setPayee(payee);

                // Column 3: Memo
                String memo = getCellValueAsString(row.getCell(3));
                if (!memo.isEmpty()) {
                    forecastTransactionView.setMemo(memo);
                }

                // Column 4: Credit
                double credit = getCellValueAsDouble(row.getCell(4));
                forecastTransactionView.setCredit(credit);

                // Column 5: Debit
                double debit = getCellValueAsDouble(row.getCell(5));
                forecastTransactionView.setDebit(debit);

                // Column 6: Balance (running balance)
                double balance = getCellValueAsDouble(row.getCell(6));
                if (balance != 0.0) {
                    forecastTransactionView.setRunningBalance(balance);
                }

                // Column 8: Importance
                String importanceStr = getCellValueAsString(row.getCell(8));
                if (!importanceStr.isEmpty()) {
                    forecastTransactionView.setHowImportant(Item.parseHowImportant(importanceStr));
                }

                // Column 9: How Occurs
                String howOccursStr = getCellValueAsString(row.getCell(9));
                if (!howOccursStr.isEmpty()) {
                    forecastTransactionView.setHowOccurs(Item.parseHowOccurs(howOccursStr));
                }

                // Column 11: Version
                String versionStr = getCellValueAsString(row.getCell(11));
                if (!versionStr.isEmpty()) {
                    forecastTransactionView.setVersion(Utility.stringTimeStampToCalendarDate(versionStr));
                }

                // Column 12: Amount (only set if explicitly provided)
                String amountStr = getCellValueAsString(row.getCell(12));
                if (!amountStr.isEmpty()) {
                    forecastTransactionView.setAmount(Utility.parseDollarAmount(amountStr));
                }

                // Add to the list
                forecastTransactions.add(forecastTransactionView);
            }

            // Check if most/all transaction IDs were not found - this indicates the forecast was updated
            // after the Excel file was rendered, causing all UUIDs to change
            if (totalRowsWithIds > 0 && missingIdCount > 0) {
                double missingPercentage = (double) missingIdCount / totalRowsWithIds * 100;
                if (missingPercentage > 50) {
                    Utility.getView().say("\n" +
                            "╔════════════════════════════════════════════════════════════════════════════╗\n" +
                            "║ WARNING: Most forecast transaction IDs were not found in the database     ║\n" +
                            "║                                                                            ║\n" +
                            "║ " + String.format("%-74s", missingIdCount + " out of " + totalRowsWithIds + " transaction IDs were not found.") + " ║\n" +
                            "║                                                                            ║\n" +
                            "║ This usually means the forecast was UPDATED after this Excel file was     ║\n" +
                            "║ rendered. When a forecast is updated, all transactions are regenerated    ║\n" +
                            "║ with new IDs, making the IDs in this Excel file obsolete.                 ║\n" +
                            "║                                                                            ║\n" +
                            "║ RECOMMENDED SOLUTION:                                                      ║\n" +
                            "║ 1. RENDER the forecast again to create a new Excel file with current IDs  ║\n" +
                            "║ 2. Make your changes in the NEWLY rendered Excel file                     ║\n" +
                            "║ 3. Import changes from the new file                                       ║\n" +
                            "║                                                                            ║\n" +
                            "║ Or, if you want to proceed with the few transactions that were found,     ║\n" +
                            "║ you can continue, but most of your changes will not be imported.          ║\n" +
                            "╚════════════════════════════════════════════════════════════════════════════╝\n");
                }
            }

        } catch (FileNotFoundException e) {
            throw new ControllerException("Excel file could not be opened (it may be locked by another program such as Excel): " + sourceName);
        } catch (IOException e) {
            ControllerException ce = new ControllerException("I/O error reading Excel file " + sourceName + " at row " + i);
            ce.initCause(e);
            throw ce;
        } catch (Exception e) {
            ControllerException ce = new ControllerException("Exception processing Excel file " + sourceName + " at row " + i);
            ce.initCause(e);
            throw ce;
        }

        return forecastTransactions;
    }

    /**
     * Helper method to get cell value as string, handling different cell types
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toString();
                }
                // Format number without decimal if it's a whole number
                double numValue = cell.getNumericCellValue();
                if (numValue == (long) numValue) {
                    return String.format("%d", (long) numValue);
                } else {
                    return String.valueOf(numValue);
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue().trim();
                } catch (IllegalStateException e) {
                    try {
                        return String.valueOf(cell.getNumericCellValue());
                    } catch (IllegalStateException e2) {
                        return "";
                    }
                }
            case BLANK:
            default:
                return "";
        }
    }

    /**
     * Helper method to get cell value as double, handling different cell types
     */
    private double getCellValueAsDouble(Cell cell) {
        if (cell == null) return 0.0;

        switch (cell.getCellType()) {
            case NUMERIC:
                return cell.getNumericCellValue();
            case STRING:
                String strValue = cell.getStringCellValue().trim();
                return Utility.parseDollarAmount(strValue);
            case FORMULA:
                try {
                    return cell.getNumericCellValue();
                } catch (IllegalStateException e) {
                    return 0.0;
                }
            case BLANK:
            default:
                return 0.0;
        }
    }

    // For now, defer to the CSV view for closing (nothing to do for Excel):
    @Override
    public void closeForecastTransactionSource(String sourceName) throws ViewException {
        csvForecastView.closeForecastTransactionSource(sourceName);
    }
}
