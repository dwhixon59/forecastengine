package com.hixon.financialApp.view.excel;

import com.hixon.financialApp.controller.ControllerException;
import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.base.ForecastView;
import com.hixon.financialApp.view.csv.CsvForecastView;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class SpreadsheetForecastView extends ForecastView {

   protected String longTermForecastFilename;
   protected String shortTermForecastFilename;
   protected String importForecastFilename;
   protected String encoding;
   private PrintWriter writer;
   private String lastDate = "";
   private boolean firstItem;
   private boolean firstItemInMonth;
   private String category;
   private String lastCategory;
   private final CsvForecastView csvForecastView = new CsvForecastView();


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
    * Constructors:
    */

   public SpreadsheetForecastView() {
      super();
      shortTermForecastFilename = "C:\\Users\\dwhix\\Dropbox\\Hixon Family Personal Business\\Finances\\Expenses\\" +
              "ShortTermForecast.xml";
      longTermForecastFilename = "C:\\Users\\dwhix\\Dropbox\\Hixon Family Personal Business\\Finances\\Expenses\\" +
              "LongTermForecast.xml";
      encoding = "UTF-8";
   }

   public SpreadsheetForecastView(String shortTermForecastFilename, String longTermForecastFilename,
                                  String importForecastFilename, String encoding) {
      super();
      this.shortTermForecastFilename = shortTermForecastFilename;
      this.longTermForecastFilename = longTermForecastFilename;
      this.importForecastFilename = importForecastFilename;
      this.encoding = encoding;
      firstItem = true;
      firstItemInMonth = true;
   }


   /*
    * Helper methods:
    */


   /*
    * Main methods:
    */
   @Override
   public void openLongTermForecastOutput() throws FileNotFoundException, UnsupportedEncodingException {
      Utility.getResolver().say("Long term forecast will be rendered to the file: " + longTermForecastFilename);
      writer = new PrintWriter(longTermForecastFilename, encoding);
   }

   @Override
   public void renderLongTermForecastFrontMatter() {

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

         // The header row default cell font style:
         writer.println("\t<Style ss:ID=\"HeaderRow\">");
            writer.println("\t\t<Font ss:FontName=\"Calibri\" x:Family=\"Swiss\" ss:Size=\"12\" ss:Color=\"#000000\"");
            writer.println("\t\tss:Bold=\"1\"/>");
         writer.println("\t</Style>");

         // The centered header string cell style:
         writer.println("\t<Style ss:ID=\"CenteredHeader\">");
            writer.println("\t\t<Font ss:FontName=\"Calibri\" x:Family=\"Swiss\" ss:Size=\"11\" ss:Color=\"#000000\"");
            writer.println("\t\tss:Bold=\"1\"/>");
            writer.println("\t\t<Alignment ss:Horizontal=\"Center\" ss:Vertical=\"Bottom\"/>");
         writer.println("\t</Style>");

         // The Forecast Transaction row default cell style:
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

      writer.println("</Styles>");

      // Define the sheet and the table:
      writer.println("<Worksheet ss:Name=\"LongTermForecast\">");
      writer.println("\t<Table ss:DefaultRowHeight=\"15\">");

      /*
       * Define the columns that will appear in the spreadsheet:
       */
      // The planned date of the forecast transaction:
      writer.println("\t\t<Column ss:Index=\"1\" ss:AutoFitWidth=\"0\" ss:Width=\"30\"/>");

      // The payee column:
      writer.println("\t\t<Column ss:Index=\"2\" ss:AutoFitWidth=\"0\" ss:Width=\"135\"/>");

      // The credit (income) column:
      writer.println("\t\t<Column ss:Index=\"3\" ss:StyleID=\"Amount\" ss:AutoFitWidth=\"0\" ss:Width=\"55\"/>");

      // The debit (expense) column:
      writer.println("\t\t<Column ss:Index=\"4\" ss:StyleID=\"Amount\" ss:AutoFitWidth=\"0\" ss:Width=\"55\"/>");

      // The running balance column:
      writer.println("\t\t<Column ss:Index=\"5\" ss:StyleID=\"Amount\" ss:AutoFitWidth=\"0\" ss:Width=\"55\"/>");

      // A blank column for spacing between a right justified column followed by a left justified column:
      writer.println("\t\t<Column ss:Index=\"6\" ss:AutoFitWidth=\"0\" ss:Width=\"20\"/>");

      // The category column:
      writer.println("\t\t<Column ss:Index=\"7\" ss:AutoFitWidth=\"0\" ss:Width=\"150\"/>");

      // The "how important" (discretionary/essential) column:
      writer.println("\t\t<Column ss:Index=\"8\" ss:AutoFitWidth=\"0\" ss:Width=\"135\"/>");

      // The "how occurs" (discretionary/essential) column:
      writer.println("\t\t<Column ss:Index=\"9\" ss:AutoFitWidth=\"0\" ss:Width=\"70\"/>");

      // The transaction ID column:
      writer.println("\t\t<Column ss:Index=\"10\" ss:AutoFitWidth=\"0\" ss:Width=\"200\"/>");

      // The amount column for short form reporting:
      writer.println("\t\t<Column ss:Index=\"11\" ss:StyleID=\"Amount\" ss:AutoFitWidth=\"0\" ss:Width=\"40\"/>");

      // The running balance for short form reporting:
      writer.println("\t\t<Column ss:Index=\"12\" ss:StyleID=\"Amount\" ss:AutoFitWidth=\"0\" ss:Width=\"40\"/>");
   }

   @Override
   public void renderMonthHeader(Calendar plannedDate) {
      writer.println("\t\t<Row ss:Height=\"25\" ss:StyleID=\"MonthRow\">");
      writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + plannedDate.getDisplayName(Calendar.MONTH, Calendar.LONG,
              Locale.US) + "</Data></Cell>");
      writer.println("\t\t</Row>");
      writer.println("\t\t<Row ss:Height=\"18.75\" ss:StyleID=\"HeaderRow\">");
      writer.println("\t\t\t<Cell><Data ss:Type=\"String\">Date</Data></Cell>");
      writer.println("\t\t\t<Cell><Data ss:Type=\"String\">Payee</Data></Cell>");
      writer.println("\t\t\t<Cell><Data ss:Type=\"String\">Credit</Data></Cell>");
      writer.println("\t\t\t<Cell><Data ss:Type=\"String\">Debit</Data></Cell>");
      writer.println("\t\t\t<Cell><Data ss:Type=\"String\">Balance</Data></Cell>");
      writer.println("\t\t\t<Cell><Data ss:Type=\"String\"></Data></Cell>");
      writer.println("\t\t\t<Cell><Data ss:Type=\"String\">Category</Data></Cell>");
      writer.println("\t\t\t<Cell><Data ss:Type=\"String\">Importance</Data></Cell>");
      writer.println("\t\t\t<Cell><Data ss:Type=\"String\">How Occurs</Data></Cell>");
      writer.println("\t\t\t<Cell><Data ss:Type=\"String\">Transaction ID</Data></Cell>");
      writer.println("\t\t\t<Cell ss:StyleID=\"CenteredHeader\"><Data ss:Type=\"String\">Amt</Data></Cell>");
      writer.println("\t\t\t<Cell ss:StyleID=\"CenteredHeader\"><Data ss:Type=\"String\">Bal</Data></Cell>");
      writer.println("\t\t</Row>");
      firstItemInMonth = true;
   }

   @Override
   public void renderForecastTransaction(ForecastTransaction forecastTransaction, int credit, int debit) throws EntityException,
           SQLException, ForecastException, BudgetException {

      // Output the forecast transaction and item:
      writer.println("\t\t<Row>");

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
      writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + dateString + "</Data></Cell>");

      // The payee for this forecast transaction:
      writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + forecastTransaction.getForecastItem().getPayee() +
              "</Data></Cell>");

      // The amount for an income item (credit):
      writer.println("\t\t\t<Cell><Data ss:Type=\"Number\">" + credit + "</Data></Cell>");

      // The amount for an expense item (debit):
      writer.println("\t\t\t<Cell><Data ss:Type=\"Number\">" + debit + "</Data></Cell>");

      // The running balance:
      if (firstItem) {
         writer.println("\t\t\t<Cell><Data ss:Type=\"Number\">" + Utility.doubleToInt(forecastTransaction.getRunningBalance()) +
                 "</Data></Cell>");
         firstItem = false;
         firstItemInMonth = false;
      } else if (firstItemInMonth) {
         writer.println("\t\t\t<Cell ss:Formula=\"=R[-3]C+RC[-2]-RC[-1]\"><Data ss:Type=\"Number\"></Data></Cell>");
         firstItemInMonth = false;
      } else {
         writer.println("\t\t\t<Cell ss:Formula=\"=R[-1]C+RC[-2]-RC[-1]\"><Data ss:Type=\"Number\"></Data></Cell>");
      }

      // A blank column to separate a right justified column from a left justified column:
      writer.println("\t\t\t<Cell><Data ss:Type=\"String\"></Data></Cell>");

      // The category name:
      writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + forecastTransaction.getForecastItem().getCategory() +
              "</Data></Cell>");

      // The importance (discretionary, essential, etc.):
      writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + forecastTransaction.getForecastItem().getHowImportant() +
              "</Data></Cell>");

      // How the transactions occur (periodic, collection, etc.):
      writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + forecastTransaction.getForecastItem().getHowOccurs() +
              "</Data></Cell>");

      // Unique ID for round trip forecast transaction matching:
      writer.println("\t\t\t<Cell><Data ss:Type=\"String\">" + forecastTransaction.getId().toString() + "</Data></Cell>");

      // The amount of the transaction for short form reporting:
      writer.println("\t\t\t<Cell ss:Formula=\"=RC[-8]-RC[-7]\"><Data ss:Type=\"Number\"></Data></Cell>");

      // The running balance for short form reporting:
      writer.println("\t\t\t<Cell ss:Formula=\"=RC[-7]\"><Data ss:Type=\"Number\"></Data></Cell>");

      writer.println("\t\t</Row>");
   }

   @Override
   protected void renderLongTermForecastBackMatter() {
      writer.println("</Table>");
      writer.println("</Worksheet>");
      writer.println("</Workbook>");
   }

   @Override
   protected void closeLongTermForecastOutput() {
      writer.close();
   }

   @Override
   // For now, defer to the CSV view for importing forecast transactions:
   public List<ForecastTransaction> openForecastTransactionSource() throws IOException, ControllerException,
           BudgetException {
      return csvForecastView.openForecastTransactionSource();
   }
}
