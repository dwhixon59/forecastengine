package com.hixon.financialApp.view.excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class EnvelopeReport {

    private final Connection m_Connection;

    public EnvelopeReport(Connection dbConnection) {
        m_Connection = dbConnection;
    }

    public boolean createReport() {

        String excelFilePath = "C:\\Users\\dwhix\\Downloads\\SavingsEnvelopesReport.xlsx";

        String sql = "SELECT r.Name AS Envelope, t.postDate AS Date, " +
                "t.payee AS Payee, t.amount AS Amount, t.balance AS Balance " +
                "FROM register r " +
                "JOIN transaction t ON r.idRegister = t.Register_idRegister " +
                "ORDER BY t.postDate DESC";

        try (Statement statement = m_Connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("Savings Envelopes");

            // Header row
            String[] headers = {"Date", "Envelope", "Payee", "Credit/Debit", "Balance"};
            Row headerRow = sheet.createRow(0);

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(getHeaderCellStyle(workbook));
            }

            // Populate rows from database
            int rowNum = 1;
            while (resultSet.next()) {
                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(resultSet.getDate("Date").toString());
                row.createCell(1).setCellValue(resultSet.getString("Envelope"));
                row.createCell(2).setCellValue(resultSet.getString("Payee"));
                row.createCell(3).setCellValue(resultSet.getDouble("Amount"));
                row.createCell(4).setCellValue(resultSet.getDouble("Balance"));
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream outputStream = new FileOutputStream(excelFilePath)) {
                workbook.write(outputStream);
            }

            workbook.close();
            System.out.println("Excel file " + excelFilePath + " created successfully!");

            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static CellStyle getHeaderCellStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }
}

