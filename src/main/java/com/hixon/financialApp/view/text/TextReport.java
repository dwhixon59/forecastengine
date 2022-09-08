package com.hixon.financialApp.view.text;

import com.hixon.financialApp.model.entity.Entity;
import com.hixon.financialApp.view.ViewException;
import com.hixon.financialApp.view.base.AbstractReport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

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
     * Constants:
     */
    public static final String SPACE = " ";
    public static final int SPACE_INDEX = 32;
    public static final String AMOUNT_LESS_THAN_EXPECTED = "-";
    public static final String AMOUNT_MORE_THAN_EXPECTED = "+";
    public static final String TRANSACTION_UNEXPECTED = "*";
    public static final double MAX_PAYEE_LENGTH = 1.3125;
    public static final String INDENT = "   ";
    public static final String COMMA = ",";
    public static final String TAB = "\t";
    public static final int TAB_INDEX = 9;
    public static final String[] PAD_STRING_SPACES = {
            "", " ", "  ", "   ", "    ", "     ", "      ", "       ", "        ", "         ", "          ",
            "           ", "            ", "             ", "              ", "               ", "                "
    };

    /*
     * ASCII table:
     *
     * Dec  Char                           Dec  Char     Dec  Char     Dec  Char
     * ---------                           ---------     ---------     ----------
     *  0  NUL (null)                      32  SPACE     64  @         96  `
     *  1  SOH (start of heading)          33  !         65  A         97  a
     *  2  STX (start of text)             34  "         66  B         98  b
     *  3  ETX (end of text)               35  #         67  C         99  c
     *  4  EOT (end of transmission)       36  $         68  D        100  d
     *  5  ENQ (enquiry)                   37  %         69  E        101  e
     *  6  ACK (acknowledge)               38  &         70  F        102  f
     *  7  BEL (bell)                      39  '         71  G        103  g
     *  8  BS  (backspace)                 40  (         72  H        104  h
     *  9  TAB (horizontal tab)            41  )         73  I        105  i
     * 10  LF  (NL line feed, new line)    42  *         74  J        106  j
     * 11  VT  (vertical tab)              43  +         75  K        107  k
     * 12  FF  (NP form feed, new page)    44  ,         76  L        108  l
     * 13  CR  (carriage return)           45  -         77  M        109  m
     * 14  SO  (shift out)                 46  .         78  N        110  n
     * 15  SI  (shift in)                  47  /         79  O        111  o
     * 16  DLE (data link escape)          48  0         80  P        112  p
     * 17  DC1 (device control 1)          49  1         81  Q        113  q
     * 18  DC2 (device control 2)          50  2         82  R        114  r
     * 19  DC3 (device control 3)          51  3         83  S        115  s
     * 20  DC4 (device control 4)          52  4         84  T        116  t
     * 21  NAK (negative acknowledge)      53  5         85  U        117  u
     * 22  SYN (synchronous idle)          54  6         86  V        118  v
     * 23  ETB (end of trans. block)       55  7         87  W        119  w
     * 24  CAN (cancel)                    56  8         88  X        120  x
     * 25  EM  (end of medium)             57  9         89  Y        121  y
     * 26  SUB (substitute)                58  :         90  Z        122  z
     * 27  ESC (escape)                    59  ;         91  [        123  {
     * 28  FS  (file separator)            60  <         92  \        124  |
     * 29  GS  (group separator)           61  =         93  ]        125  }
     * 30  RS  (record separator)          62  >         94  ^        126  ~
     * 31  US  (unit separator)            63  ?         95  _        127  DEL
     */

    // The size in inches of each type of ASCII character in the iPhone11 font:
    public static final double IPHONE_11_TAB_WIDTH = 0.1693125;
    public static final double IPHONE_11_8_TAB_WIDTH = 0.1693125 * 8;
    public static final double[] iPhone11FontSizes = {
            0, 0, 0, 0, 0, 0, 0, 0,
            0, IPHONE_11_TAB_WIDTH, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            IPHONE_11_8_TAB_WIDTH / 58.5, IPHONE_11_8_TAB_WIDTH / 53.0, IPHONE_11_8_TAB_WIDTH / 34.1, IPHONE_11_8_TAB_WIDTH / 25.8, // 32
            IPHONE_11_8_TAB_WIDTH / 25.7, IPHONE_11_8_TAB_WIDTH / 17.4, IPHONE_11_8_TAB_WIDTH / 22.8, IPHONE_11_8_TAB_WIDTH / 56.0, // 36
            IPHONE_11_8_TAB_WIDTH / 43.0, IPHONE_11_8_TAB_WIDTH / 43.0, IPHONE_11_8_TAB_WIDTH / 35.2, IPHONE_11_8_TAB_WIDTH / 25.7, // 40
            IPHONE_11_8_TAB_WIDTH / 56.0, IPHONE_11_8_TAB_WIDTH / 34.7, IPHONE_11_8_TAB_WIDTH / 56.0, IPHONE_11_8_TAB_WIDTH / 54.1, // 44
            IPHONE_11_8_TAB_WIDTH / 25.8, IPHONE_11_8_TAB_WIDTH / 35.1, IPHONE_11_8_TAB_WIDTH / 27.0, IPHONE_11_8_TAB_WIDTH / 26.0, // 48
            IPHONE_11_8_TAB_WIDTH / 25.1, IPHONE_11_8_TAB_WIDTH / 26.3, IPHONE_11_8_TAB_WIDTH / 25.3, IPHONE_11_8_TAB_WIDTH / 27.5, // 52
            IPHONE_11_8_TAB_WIDTH / 25.3, IPHONE_11_8_TAB_WIDTH / 25.4, IPHONE_11_8_TAB_WIDTH / 56.0, IPHONE_11_8_TAB_WIDTH / 56.0, // 56
            IPHONE_11_8_TAB_WIDTH / 25.7, IPHONE_11_8_TAB_WIDTH / 25.8, IPHONE_11_8_TAB_WIDTH / 25.7, IPHONE_11_8_TAB_WIDTH / 31.1, // 60
            IPHONE_11_8_TAB_WIDTH / 17.4, IPHONE_11_8_TAB_WIDTH / 24.1, IPHONE_11_8_TAB_WIDTH / 24.9, IPHONE_11_8_TAB_WIDTH / 22.5, // 64
            IPHONE_11_8_TAB_WIDTH / 22.2, IPHONE_11_8_TAB_WIDTH / 27.2, IPHONE_11_8_TAB_WIDTH / 28.4, IPHONE_11_8_TAB_WIDTH / 21.6, // 68
            IPHONE_11_8_TAB_WIDTH / 21.9, IPHONE_11_8_TAB_WIDTH / 62.0, IPHONE_11_8_TAB_WIDTH / 30.1, IPHONE_11_8_TAB_WIDTH / 24.6, // 72
            IPHONE_11_8_TAB_WIDTH / 28.6, IPHONE_11_8_TAB_WIDTH / 18.4, IPHONE_11_8_TAB_WIDTH / 21.8, IPHONE_11_8_TAB_WIDTH / 21.0, // 76
            IPHONE_11_8_TAB_WIDTH / 25.6, IPHONE_11_8_TAB_WIDTH / 21.0, IPHONE_11_8_TAB_WIDTH / 24.7, IPHONE_11_8_TAB_WIDTH / 25.3, // 80
            IPHONE_11_8_TAB_WIDTH / 25.4, IPHONE_11_8_TAB_WIDTH / 22.0, IPHONE_11_8_TAB_WIDTH / 24.1, IPHONE_11_8_TAB_WIDTH / 16.6, // 84
            IPHONE_11_8_TAB_WIDTH / 23.9, IPHONE_11_8_TAB_WIDTH / 24.7, IPHONE_11_8_TAB_WIDTH / 24.4, IPHONE_11_8_TAB_WIDTH / 43.0, // 88
            IPHONE_11_8_TAB_WIDTH / 54.0, IPHONE_11_8_TAB_WIDTH / 43.0, IPHONE_11_8_TAB_WIDTH / 25.8, IPHONE_11_8_TAB_WIDTH / 29.7, // 92
            IPHONE_11_8_TAB_WIDTH / 32.5, IPHONE_11_8_TAB_WIDTH / 29.4, IPHONE_11_8_TAB_WIDTH / 26.4, IPHONE_11_8_TAB_WIDTH / 29.0, // 96
            IPHONE_11_8_TAB_WIDTH / 26.3, IPHONE_11_8_TAB_WIDTH / 28.4, IPHONE_11_8_TAB_WIDTH / 45.1, IPHONE_11_8_TAB_WIDTH / 26.5, //100
            IPHONE_11_8_TAB_WIDTH / 27.5, IPHONE_11_8_TAB_WIDTH / 67.0, IPHONE_11_8_TAB_WIDTH / 67.5, IPHONE_11_8_TAB_WIDTH / 30.0, //104
            IPHONE_11_8_TAB_WIDTH / 66.0, IPHONE_11_8_TAB_WIDTH / 18.5, IPHONE_11_8_TAB_WIDTH / 27.9, IPHONE_11_8_TAB_WIDTH / 27.4, //108
            IPHONE_11_8_TAB_WIDTH / 26.6, IPHONE_11_8_TAB_WIDTH / 26.5, IPHONE_11_8_TAB_WIDTH / 42.0, IPHONE_11_8_TAB_WIDTH / 31.7, //112
            IPHONE_11_8_TAB_WIDTH / 45.1, IPHONE_11_8_TAB_WIDTH / 27.8, IPHONE_11_8_TAB_WIDTH / 30.0, IPHONE_11_8_TAB_WIDTH / 20.8, //116
            IPHONE_11_8_TAB_WIDTH / 31.0, IPHONE_11_8_TAB_WIDTH / 30.0, IPHONE_11_8_TAB_WIDTH / 30.1, IPHONE_11_8_TAB_WIDTH / 43.0, //120
            IPHONE_11_8_TAB_WIDTH / 64.5, IPHONE_11_8_TAB_WIDTH / 43.1, IPHONE_11_8_TAB_WIDTH / 25.7, 0     //124
    };

/*
    public static final String tenWs = "WWWWWWWWWW";
    public static final double tenWsWidth = 0.84375; // Equals 13.5/16ths (0.84375) of an inch
    public static final double[] iPhone11FontInfo = {
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0.1693125, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            tenWsWidth / 36, tenWsWidth / 32, tenWsWidth / 21, tenWsWidth / 15, tenWsWidth / 15, tenWsWidth / 10, tenWsWidth / 14, tenWsWidth / 33,
            tenWsWidth / 26, tenWsWidth / 26, tenWsWidth / 21, tenWsWidth / 16, tenWsWidth / 34, tenWsWidth / 21, tenWsWidth / 33, tenWsWidth / 32,
            tenWsWidth / 15, tenWsWidth / 21, tenWsWidth / 16, tenWsWidth / 15, tenWsWidth / 15, tenWsWidth / 16, tenWsWidth / 15, tenWsWidth / 16,
            tenWsWidth / 15, tenWsWidth / 15, tenWsWidth / 33, tenWsWidth / 34, tenWsWidth / 15, tenWsWidth / 16, tenWsWidth / 15, tenWsWidth / 19,
            tenWsWidth / 11, tenWsWidth / 15, tenWsWidth / 15, tenWsWidth / 14, tenWsWidth / 13, tenWsWidth / 16, tenWsWidth / 17, tenWsWidth / 13,
            tenWsWidth / 13, tenWsWidth / 37, tenWsWidth / 18, tenWsWidth / 15, tenWsWidth / 17, tenWsWidth / 11, tenWsWidth / 13, tenWsWidth / 13,
            tenWsWidth / 15, tenWsWidth / 12, tenWsWidth / 15, tenWsWidth / 15, tenWsWidth / 15, tenWsWidth / 13, tenWsWidth / 14, tenWsWidth / 16.0,
            tenWsWidth / 14, tenWsWidth / 15, tenWsWidth / 15, tenWsWidth / 26, tenWsWidth / 32, tenWsWidth / 26, tenWsWidth / 16, tenWsWidth / 18,
            tenWsWidth / 20, tenWsWidth / 18, tenWsWidth / 16, tenWsWidth / 17, tenWsWidth / 16, tenWsWidth / 17, tenWsWidth / 27, tenWsWidth / 16,
            tenWsWidth / 17, tenWsWidth / 41, tenWsWidth / 41, tenWsWidth / 18, tenWsWidth / 40, tenWsWidth / 11, tenWsWidth / 17, tenWsWidth / 16,
            tenWsWidth / 16, tenWsWidth / 16, tenWsWidth / 25, tenWsWidth / 19, tenWsWidth / 43.3, tenWsWidth / 17, tenWsWidth / 18, tenWsWidth / 13,
            tenWsWidth / 19, tenWsWidth / 18, tenWsWidth / 18, tenWsWidth / 26, tenWsWidth / 38, tenWsWidth / 26, tenWsWidth / 16, 0
    };
*/



    /*
     * Fields:
     */
    protected final File reportFile;
    protected PrintWriter pw;

    /*
     * Getters and setters:
     */


    /*
     * Constructors:
     */
    public TextReport(List<Entity> items, File file) {
        super(items);
        this.reportFile = file;
    }


    /*
     * Helper methods:
     */

    /**
     * Compute the width of the string in inches by adding up the widths of each character in the string.  If the width
     * exceeds, or is less than 1/4 of a tab stop from the maximum width, trim the string so that it is.
     *
     * @param string The string to computer the width of in inches (and possibly trim).
     * @param maxWidthInTabs The maximum width of the string in multiples of the width of a tab.
     * @param fontInfo The widths in inches of the ASCII characters in the desired font.
     * @return The width in inches of the (possibly trimmed) string.
     */
     static double getStringWidthInInches(@NotNull String string, @Range(from = 1,to = Integer.MAX_VALUE) int maxWidthInTabs,
                                         @NotNull double[] fontInfo) {
        // Compute the length of the payee and trim it if it is too long:
        double widthInInches = 0.0;
        double maxWidthInInches = maxWidthInTabs * fontInfo[TAB_INDEX] - fontInfo[TAB_INDEX] / 4;
        for (int i = 0; i < string.length(); i++) {
            widthInInches += fontInfo[string.charAt(i)];
            if (widthInInches > maxWidthInInches) {
                widthInInches -= fontInfo[string.charAt(i)];
                string = string.substring(0, i - 1);
                break;
            }
        }
       return widthInInches;
    }

    /**
     * First trim the string if necessary so that it is not closer than 1/4 of a tab from the width specified in the
     * fieldWidthInTabs parameter. Then add any required tabs to make the string width in tabs wide.  The last character
     * in the string will always be a tab.  Also, pad with spaces at the end of the string if necessary such that
     * the string never ends less than a 1/4 of a tab from the previous or following tab stop.  This is so that
     * rounding errors won't cause an extra tab to be inserted.
     *
     * @param string The string to be padded with tabs, and trimmed if too long.
     * @param fieldWidthInTabs Desired width of the string as a multiple of the width of a tab.
     * @param fontInfo An array that specifies the width of the ASCII character in the desired font.
     * @return The padded (and possibly trimmed) string.
     */
    static String padStringWithTabs(@NotNull String string, @Range(from = 1, to = Integer.MAX_VALUE) int fieldWidthInTabs,
                                    @NotNull double[] fontInfo) {

        double tabWidthInInches = fontInfo[TAB_INDEX];
        double spaceWidthInInches =  fontInfo[SPACE_INDEX];

        // Compute the length of the payee and trim it if it is too long:
        double widthInInches = getStringWidthInInches(string, fieldWidthInTabs, fontInfo);

        // Pad the string half a tab stop if it is within quarter of a tab stop of the next tab stop to provide a margin
        // of error:
        double remainder = (widthInInches / tabWidthInInches) - ((int) (widthInInches / tabWidthInInches));
        if (remainder < 0.25 || remainder > 0.75) {
            String pad = PAD_STRING_SPACES[(int) Math.round(tabWidthInInches / spaceWidthInInches / 2)];
            string += pad;
            widthInInches += pad.length() * spaceWidthInInches;
        }

        // Add one or more tabs to get to the amount column:
        string += TAB;
        widthInInches = ((int) (widthInInches / tabWidthInInches) + 1) * tabWidthInInches;
        while (widthInInches < fieldWidthInTabs * tabWidthInInches) {
            string += TAB;
            widthInInches += tabWidthInInches;
        }
        return string;
    }

    /**
     * This method formats a dollar amount stored in a double in to string that is right justified in a fields
     * whose width is expressed in terms of a number of tabs.
     *
     * @param amount The dollar amount to format.
     * @param fieldWidthInTabs The width of the field in tabs.
     * @return The formatted string.
     */
    static String formatRoundedDollarAmountField(double amount, int fieldWidthInTabs, double[] fontInfo) {
        String formattedAmount = "$" + Long.toString(Math.round(amount));
        double fieldWidthInInches = fieldWidthInTabs * fontInfo[TAB_INDEX] - fontInfo[TAB_INDEX] / 4;
        double amountWidthInInches = getStringWidthInInches(formattedAmount, fieldWidthInTabs, fontInfo);
        int padSpaces =  (int) Math.round((fieldWidthInInches - amountWidthInInches) / fontInfo[SPACE_INDEX]);
        formattedAmount = PAD_STRING_SPACES[padSpaces] + formattedAmount;
        return formattedAmount;
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


