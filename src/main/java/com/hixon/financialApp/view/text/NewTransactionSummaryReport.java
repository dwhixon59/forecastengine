package com.hixon.financialApp.view.text;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.Entity;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.model.register.TransactionSplit;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.ViewException;
import com.hixon.financialApp.view.base.AbstractRegisterReport;

import java.io.*;
import java.sql.SQLException;
import java.util.List;

public class NewTransactionSummaryReport extends AbstractRegisterReport {

    private final User user;
    private final List<Entity> items;
    private final File reportFile;
    private PrintWriter pw;

    public NewTransactionSummaryReport(Register register, User user, List<Entity> items, File file) {
        super(register);

        this.user = user;
        this.items = items;
        this.reportFile = file;
    }

    /*
     * Output the report:
     */
    @Override
    public void openReportOutput() throws FileNotFoundException, UnsupportedEncodingException, ViewException {
        boolean append = false;
        boolean autoFlush = true;
        String charset = "UTF-8";

        FileOutputStream fos = new FileOutputStream(reportFile, append);
        OutputStreamWriter osw = new OutputStreamWriter(fos, charset);
        BufferedWriter bw = new BufferedWriter(osw);
        pw = new PrintWriter(bw, autoFlush);
    }

    @Override
    public void renderReportFrontMatter() {
        pw.println("New transaction summary:");
    }

    @Override
    public void renderHeaderRow() {

    }

    @Override
    public List<Entity> getItems() {
        return items;
    }

    @Override
    public void renderItemRow(Entity item) throws EntityException, ForecastException, SQLException, BudgetException,
            RegisterException {
        Transaction transaction = (Transaction) item;

        // Use a short version of the date to take less space:
        String date = Utility.calendarDateToMonthDayDate(
                (transaction.getAuthorizationDate() != null) ? transaction.getAuthorizationDate() : transaction.getPostDate()
        );

        // Round off the amount to save space by not displaying the cents:
        String amount = Utility.formatRoundedDollarAmount(Math.abs(transaction.getAmount()));

        // Seems like we have about another 25 characters before text wrap on the iPhone 11, so get as much of the payee
        // as possible based on the length of the amount:
        String merchant = transaction.getMerchant().getName();
        int truncatedMerchantLength = 25 - amount.length();
        if (merchant.length() > truncatedMerchantLength) {
            merchant = merchant.substring(0, truncatedMerchantLength);
        }

        // Output the transaction line:
        pw.println(date + " " + merchant + " " + amount);

        // Output the splits under the payee indented one tab:
        List<TransactionSplit> splits = TransactionSplit.getSplitsForTransaction(transaction);
        if (splits != null) {
            for (TransactionSplit split : splits
            ) {
                String splitAmount = Utility.formatRoundedDollarAmount(Math.abs(split.getAmount()));

                // Only print the amount if it is different than the transaction amount, which only happens when there is
                // more that one split:
                splitAmount = (splits.size() > 1) ? " " + splitAmount : "";

                // Print as much of the split payee that will fit with the amount on an iPhone 11:
                String splitPayee = split.getBudgetItem().getPayee();
                int truncatedPayeeLength = 27 - splitAmount.length();
                if (splitPayee.length() > truncatedPayeeLength) {
                    splitPayee = splitPayee.substring(0, truncatedPayeeLength);
                }

                // Output the split line without the memo:
                String line = "\t" + splitPayee + splitAmount;
                pw.print(line);

                // Add the memo if there is one.
                String memo = "";
                if (split.getMemo() != null) {

                    int remainingSpace = 27 - line.length();
                    int memoLength = split.getMemo().length();

                    // Put it on the same line if it fits:
                    if (memoLength <= remainingSpace) {
                        memo = " " + split.getMemo();
                    } else if (remainingSpace > 5) {
                        // truncate the memo if there is room for at least the first 6 characters:
                        memo = " " + split.getMemo().substring(0, remainingSpace);
                    } else {
                        //  otherwise put it on the next line:
                        int len = (split.getMemo().length() <= 21) ? split.getMemo().length() : 21;
                        memo = "\n\tMemo: " + split.getMemo().substring(0, len);
                    }
                }

                // Go to the next line:
                pw.println(memo);
            }
        }
    }

    @Override
    public void renderSummaryRow() {

    }

    @Override
    public void renderReportBackMatter() {

    }

    @Override
    public void closeReportOutput() {
        pw.close();
    }
}

