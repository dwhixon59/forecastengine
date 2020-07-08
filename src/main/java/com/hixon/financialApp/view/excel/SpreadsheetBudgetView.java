package com.hixon.financialApp.view.excel;

import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.register.TransactionSplit;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.ViewException;
import com.hixon.financialApp.view.base.BudgetView;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Calendar;

public class SpreadsheetBudgetView extends BudgetView {

   /*
    * Fields:
    */
   private PrintWriter writer;
   private String category;
   private String lastCategory;
   private String spendingReportFilename;
   private String encoding;


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


   /*
    * Constructors:
    */
   public SpreadsheetBudgetView(String spendingReportFilename, String encoding) {
      this.spendingReportFilename = spendingReportFilename;
      this.encoding = encoding;
   }

   public SpreadsheetBudgetView() {
      this.spendingReportFilename = "C:\\Users\\dwhix\\Dropbox\\Hixon Family Personal Business\\Finances\\Expenses\\" +
              "SpendingReport.xml";
      this.encoding = "UTF-8";
   }


   /*
    * Main methods:
    */
   public void openSpendingReportOutput() throws FileNotFoundException, UnsupportedEncodingException, ViewException {
      com.hixon.financialApp.utility.Utility.getResolver().say("MTD Spending Report will be rendered to the file: "
              + spendingReportFilename);
      Boolean done = false;
      while(!done) {
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
      // Write the header information to the output file:
      writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
      writer.println("<?mso-application progid=\"Excel.Sheet\"?>");
      writer.println("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\"");
      writer.println("xmlns:o=\"urn:schemas-microsoft-com:office:office\"");
      writer.println("xmlns:x=\"urn:schemas-microsoft-com:office:excel\"");
      writer.println("xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\"");
      writer.println("xmlns:html=\"http://www.w3.org/TR/REC-html40\">");
      writer.println("<Worksheet ss:Name=\"Table1\">");
      writer.println("<Table>");
      writer.println("<Column ss:Index=\"1\" ss:AutoFitWidth=\"0\" ss:Width=\"110\"/>");
      writer.println("<Column ss:Index=\"2\" ss:AutoFitWidth=\"0\" ss:Width=\"110\"/>");
      writer.println("<Column ss:Index=\"3\" ss:AutoFitWidth=\"0\" ss:Width=\"110\"/>");
      writer.println("<Row>");
      writer.println("<Cell><Data ss:Type=\"String\">Category</Data></Cell>");
      writer.println("<Cell><Data ss:Type=\"String\">Payee</Data></Cell>");
      writer.println("<Cell><Data ss:Type=\"String\">Budgeted Amount</Data></Cell>");
      writer.println("<Cell><Data ss:Type=\"String\">Actual Amount</Data></Cell>");
      writer.println("</Row>");
   }

   @Override
   public void renderBudgetItem(BudgetItem budgetItem, Calendar startDate, Calendar endDate, double total) throws
           ForecastException {
      writer.println("<Row>");
      writer.println("</Row>");
      writer.println("<Row>");
      category = budgetItem.getCategory();
      if (category.equals(lastCategory)) {
         category = " ";
      } else {
         lastCategory = category;
      }
      writer.println("<Cell><Data ss:Type=\"String\">" + category + "</Data></Cell>");
      writer.println("<Cell><Data ss:Type=\"String\">" + budgetItem.getPayee() + "</Data></Cell>");
      writer.println("<Cell><Data ss:Type=\"Number\">" + budgetItem.getAmountForDateRange(startDate, endDate) +
              "</Data></Cell>");
      writer.println("<Cell><Data ss:Type=\"Number\">" + total + "</Data></Cell>");
      writer.println("</Row>");

   }

   @Override
   public void renderTransactionSplit(TransactionSplit split) {
      writer.println("<Row>");
      writer.println("<Cell><Data ss:Type=\"String\">" + " " + "</Data></Cell>");
      writer.println("<Cell><Data ss:Type=\"String\">" + " " + "</Data></Cell>");
      writer.println("<Cell><Data ss:Type=\"String\">" + split + "</Data></Cell>");
      writer.println("</Row>");
   }

   @Override
   public void renderSpendingReportBackMatter() {
      writer.println("</Table>");
      writer.println("</Worksheet>");
      writer.println("</Workbook>");
   }

   @Override
   public void closeSpendingReportOutput() {
      writer.close();
   }

}
