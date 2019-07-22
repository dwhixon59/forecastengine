package com.hixon.financial.view.register;

import com.hixon.financial.Utility;
import com.hixon.financial.view.ViewException;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Calendar;
import java.util.GregorianCalendar;

public class ExcelRegisterView implements RegisterView {

    // Create the tab delimited file with the forecast data to import into Excel:
    @Override
    public boolean renderFrom(Calendar startDate) throws FileNotFoundException, UnsupportedEncodingException,
            ViewException {

        PrintWriter writer = new PrintWriter("C:\\Users\\dwhix\\Dropbox\\Hixon Family Personal Business\\" +
                "Finances\\Expenses\\Register.tsv", "UTF-8");

        // Get a list of forecast transactions starting on the start date:
        String query = "select Transaction.actualDate, BudgetItem.payee, Transaction.actualAmount, " +
                "Transaction.cleared, Budget.name, Transaction.payee, Register.name, bin_to_UUID(idTransaction) from " +
                "ForecastDatabase.Transaction, ForecastDatabase.BudgetItem, ForecastDatabase.Budget, " +
                "ForecastDatabase.Register where Transaction.Register_idRegister = Register.idRegister and " +
                "Transaction.BudgetItem_idBudgetItem = BudgetItem.idBudgetItem and BudgetItem.Budget_idBudget = " +
                "Budget.idBudget and Transaction.actualDate >= " + Utility.calendarDateToSqlStringDate(startDate) + "order by" +
                " 1 asc";
        try {
            Statement statement = Utility.getDbConnection().createStatement();
            ResultSet rs = statement.executeQuery(query);

            // For each transaction in the list of transactions:
            int i = 0;
            while (rs.next()) {
                String credit;
                String debit;
                double amount = rs.getDouble(3);
                if (amount > 0) {
                    credit = Utility.formatDollarAmount(amount);
                    debit = "";
                } else {
                    credit = "";
                    debit = Utility.formatDollarAmount(-amount);
                }
                String cleared = new String((rs.getBoolean(4)) ? "*" : "");
                writer.println(
                        Utility.SqlDateToStringDate(rs.getDate(1)) + "\t" +
                                rs.getString(2) + "\t" +
                                credit + "\t" +
                                debit + "\t" +
                                cleared + "\t" +
                                "\t" + // skip the running balance column
                                rs.getString(5) + "\t" +
                                "\t"  + // skip the comments column
                                rs.getString(6) + "\t" +
                                rs.getString(7) + "\t" +
                                rs.getString(8)
                );
                i++;
            }
            writer.close();
            System.out.println(i + " records written to the file register.tsv");
        }
        catch (SQLException e) {
            ViewException ve = new ViewException("Database error occurred trying to get a transaction for export.");
            ve.initCause(e);
            throw ve;
        }

        // Create a summary report of the register transactions starting on the start date for 30 days:
        writer = new PrintWriter("C:\\Users\\dwhix\\Dropbox\\Hixon Family Personal Business\\Finances\\" +
                "Expenses\\Summary.tsv", "UTF-8");
        Calendar endDate = new GregorianCalendar();
        endDate.set(startDate.get(Calendar.YEAR), startDate.get(Calendar.MONTH),
                startDate.getActualMaximum(Calendar.DAY_OF_MONTH));
        query = "select category, payee, sum(plannedAmount) as plannedAmount, sum(actualAmount) as actualAmount, " +
                "sum(plannedVsActual) as plannedVsActual " +
                "from (" +
                   "select B.category as category, B.payee as payee, B.amount as 'plannedAmount', 0 as 'actualAmount'," +
                   " -B.amount as 'plannedVsActual' " +
                      "from forecastdatabase.ForecastTransaction A, forecastdatabase.forecastitem B " +
                      "where A.ForecastItem_idForecastItem = B.idForecastItem and A.plannedDate < " +
                          Utility.calendarDateToSqlStringDate(startDate) + " " +
                      "union " +
                      "select B.category, B.payee, 0 as 'plannedAmount', A.actualAmount as 'actualAmount', " +
                          "A.actualAmount as 'plannedVsActual' " +
                      "from forecastdatabase.transaction A, forecastdatabase.budgetitem B " +
                      "where A.BudgetItem_idBudgetItem = B.idBudgetItem and A.actualDate >= " +
                          Utility.calendarDateToSqlStringDate(startDate) + " and " +
                          "A.actualDate <= " + Utility.calendarDateToSqlStringDate(startDate) +
               ") as T " +
               "group by 1,2 order by 1,2";

        try {
            Statement statement = Utility.getDbConnection().createStatement();
            ResultSet rs = statement.executeQuery(query);

            // For each transaction in the list of transactions:
            int i = 0;
            while (rs.next()) {
                String credit;
                String debit;
                double amount = rs.getDouble(3);
                if (amount > 0) {
                    credit = Utility.formatDollarAmount(amount);
                    debit = "";
                } else {
                    credit = "";
                    debit = Utility.formatDollarAmount(-amount);
                }
                String cleared = new String((rs.getBoolean(4)) ? "*" : "");
                writer.println(
                        rs.getString(1) + "\t" +
                        rs.getString(2) + "\t" +
                        rs.getDouble(3) + "\t" +
                        rs.getDouble(4) + "\t" +
                        rs.getDouble(5)
                );
                i++;
            }
            writer.close();
            System.out.println(i + " records written to the file register.tsv");
            return true;
        }
        catch (SQLException e) {
            ViewException ve = new ViewException("Database error occurred trying to get a transaction for export.");
            ve.initCause(e);
            throw ve;
        }
    }
}
