package com.hixon.financialApp.view.text;

import com.hixon.financialApp.model.entity.Entity;
import com.hixon.financialApp.view.ViewException;
import com.hixon.financialApp.view.base.AbstractReport;

import java.io.*;
import java.util.List;

/**
 * The TextReport class encapsulates all the logic that is common to reports that are formatted as text.  For example,
 * all text reports use the {@link PrintWriter} class to output the items of the report to a file.  So this class contains
 * member variables for a {@link PrintWriter} and a {@link File}.  It also contains various constants useful for
 * formatting text.
 */
public class TextReport extends AbstractReport {

    /*
     * Fields:
     */
    public static final String SPACE = " ";
    public static final String AMOUNT_LESS_THAN_EXPECTED = "-";
    public static final String AMOUNT_MORE_THAN_EXPECTED = "+";
    public static final String TRANSACTION_UNEXPECTED = "*";
    public static final int MAX_PAYEE_LENGTH = 24;
    public static final String INDENT = "   ";
    public static final String COMMA = ",";
    public static final String TAB = "\t";
    protected final File reportFile;
    protected PrintWriter pw;

    /*
     * Getters and setters:
     */


    /*
     * Constructors:
     */
    public TextReport(List<Entity> items, File file) throws FileNotFoundException {
        super(items);
        this.reportFile = file;
    }

    /*
     * Main methods:
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
    public void closeReportOutput() {
        pw.flush();
        pw.close();
    }
}


