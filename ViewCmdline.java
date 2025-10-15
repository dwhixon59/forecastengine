package com.hixon.financialApp.view.cmdLine;

import com.hixon.financialApp.controller.CancelException;
import com.hixon.financialApp.controller.ImportController.TerminationCondition;
import com.hixon.financialApp.controller.QuitException;
import com.hixon.financialApp.controller.SkipException;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.IndependentEntityInt;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.base.EntityOrStringResult;
import com.hixon.financialApp.view.base.NumberOrStringResponse;
import com.hixon.financialApp.view.base.ViewInt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.hixon.financialApp.controller.ImportController.TerminationCondition.QUIT;

public class ViewCmdline implements ViewInt {

    /**
     * Enum to track the type of heading or output that was last displayed.
     * This allows for smart spacing between different heading levels.
     */
    public enum HeadingLevel {
        H1,    // Major section header
        H2,    // Sub-section header
        H3,    // Minor header
        NONE   // Regular output or no heading
    }

    /*
     * Fields for ViewCmdline:
     */
    private TerminationCondition terminationCondition;
    private final Scanner in;
    private HeadingLevel lastHeading;

    // Help text properties loaded from file
    private static final Properties helpText = new Properties();

    static {
        try (InputStream input = ViewCmdline.class.getClassLoader()
                .getResourceAsStream("help-text.properties")) {
            if (input == null) {
                throw new RuntimeException("Unable to find help-text.properties");
            }
            helpText.load(input);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to load help text properties", ex);
        }
    }


    /*
     * Getters and setters for ViewCmdline:
     */
    public TerminationCondition getTerminationCondition() {
        return terminationCondition;
    }


    /*
     * Constructors for ViewCmdline:
     */
    public ViewCmdline() {
        terminationCondition = QUIT;
        in = new Scanner(System.in);
        lastHeading = HeadingLevel.NONE;
    }


    /*
     * Helper methods for TransactionResolverCmdLine:
     */

    /**
     * Displays a blank line to the console.
     * Resets the last heading level to `NONE`.
     */
    public void say() {
        System.out.println();
        lastHeading = HeadingLevel.NONE;
    }

    /**
     * Displays the specified string to the console.
     * Resets the last heading level to `NONE`.
     *
     * @param s The string to display.
     */    public void say(String s) {
        System.out.println(s);
        lastHeading = HeadingLevel.NONE;
    }

    /**
     * Reads a line of input from the user.
     * This method centralizes all input reading, making it easy to mock for unit testing.
     *
     * @return The line of text entered by the user
     */
    protected String getLine() {
        return in.nextLine();
    }

    /**
     * {@inheritDoc}
     */
    public double getDouble(String prompt, String errorMessage) {
        if (!prompt.isEmpty()) {
            ask(prompt);
        }
        double doubleValue;
        while (true) {
            try {
                String doubleString = getLine().trim();
                doubleValue = Double.parseDouble(doubleString);
                return doubleValue;
            } catch (NumberFormatException nfe) {
                ask(errorMessage + " please re-enter:  ");
            }
        }
    }

    /**
     * Implementation of getDollarAmount for cmdLine view.
     * Prompts the user to enter a dollar amount and validates input.
     */
    @Override
    public double getDollarAmount() {
        return getDouble("Please enter the dollar amount:  ", "Invalid dollar amount,");
    }

    /**
     * Displays a major section header (H1) with emphasis.
     * Format: blank line before, ALL CAPS text with equals signs above and below, blank line after.
     * The length of the decoration adapts to the text length.
     *
     * @param s the header text to display
     */
    public void sayH1(String s) {
        String upperText = s.toUpperCase();
        String decoration = "=".repeat(upperText.length());
        System.out.println();
        System.out.println(decoration);
        System.out.println(upperText);
        System.out.println(decoration);
        System.out.println();
        lastHeading = HeadingLevel.H1;
    }

    /**
     * Displays a sub-section header (H2) with moderate emphasis.
     * Format: blank line before (unless preceded by H1), First Letter Capitalized with dashes below.
     * The length of the decoration adapts to the text length.
     *
     * @param s the header text to display
     */
    public void sayH2(String s) {
        String decoration = "─".repeat(s.length());
        // Only print blank line if not immediately following H1
        if (lastHeading != HeadingLevel.H1) {
            System.out.println();
        }
        System.out.println(s);
        System.out.println(decoration);
        lastHeading = HeadingLevel.H2;
    }

    /**
     * Displays a minor header (H3) with subtle emphasis.
     * Format: blank line before (unless preceded by H1 or H2), text with a visual marker (▸).
     *
     * @param s the header text to display
     */
    public void sayH3(String s) {
        // Only print blank line if not immediately following a higher-level heading
        if (lastHeading != HeadingLevel.H1 && lastHeading != HeadingLevel.H2) {
            System.out.println();
        }
        System.out.println("▸ " + s);
        lastHeading = HeadingLevel.H3;
    }

    public void ask(String s) {
        System.out.print(s);
        lastHeading = HeadingLevel.NONE;
    }

    public boolean getYesOrNo(String question) {

        // Call the full version of getYesOrNo() with the false values for the cancel, quit and skip parameters:
        try {
            return getYesOrNo(question, ViewInt.DO_NOT_ALLOW_CANCEL, ViewInt.DO_NOT_ALLOW_QUIT,
                    ViewInt.DO_NOT_ALLOW_SKIP);
        } catch (CancelException | QuitException | SkipException e) {
            // These exceptions since we specified that they should not be allowed:
            throw new RuntimeException("Logic error, received a cancel, skip or quit exception which should not happen.");
        }
    }

    public boolean getYesOrNo(String question, boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException {

        // Add the cancel, skip and quit prompt strings if they are allowed:
        String cancelSkipOrQuitPrompt = getCancelSkipOrQuitPrompt(isCancelAllowed, isQuitAllowed, isSkipAllowed);
        ask(question + " ('y' or 'n'" + cancelSkipOrQuitPrompt + "): ");
        while (true) {
            String line = getLine();
            if (line.equalsIgnoreCase("y")) return true;
            if (line.equalsIgnoreCase("n")) return false;

            // If the user entered a special value, then throw the appropriate exception:
            if (isCancelAllowed) {
                if (line.equalsIgnoreCase("c")) {
                    throw new CancelException("User asked to cancel this operation.");
                }
            }
            if (isQuitAllowed) {
                if (line.equalsIgnoreCase("q")) {
                    throw new QuitException("User asked to abort processing.");
                }
            }
            if (isSkipAllowed) {
                if (line.equalsIgnoreCase("s")) {
                    throw new SkipException("User asked to skip this item.");
                }
            }

            ask("\nPlease enter 'y' or 'n'" + cancelSkipOrQuitPrompt + ": ");
        }
    }

    public boolean askContinue(String prompt) {
        ask(prompt + "  Do you want to continue?  " + "(y/n): ");
        while (true) {
            String line = getLine();
            if (line.equalsIgnoreCase("y")) return true;
            if (line.equalsIgnoreCase("n")) return false;
            ask("\nPlease enter 'y' or 'n': ");
        }
    }

    /**
     * Prompts the user for a dollar amount, allowing them to accept a default value by pressing enter.
     * If the user enters a value, it is validated and returned as a string.
     *
     * @param prompt The prompt to display to the user.
     * @param defaultAmount The default amount to use if the user presses enter.
     * @return The entered or default dollar amount as a string.
     */
    public String parseDollarAmount(String prompt, double defaultAmount) {
        // Delegate to the full version with cancel, quit, skip all set to false
        try {
            return parseDollarAmount(prompt, defaultAmount, false, false, false);
        } catch (CancelException | QuitException | SkipException e) {
            // These should never occur since we disabled them
            throw new RuntimeException("Unexpected exception", e);
        }
    }

    /**
     * Prompts the user for a dollar amount, allowing cancel, quit, and skip options.
     * @param prompt The prompt to display to the user.
     * @param defaultAmount The default amount to use if the user presses enter.
     * @param isCancelAllowed Whether cancel is allowed.
     * @param isQuitAllowed Whether quit is allowed.
     * @param isSkipAllowed Whether skip is allowed.
     * @return The entered or default dollar amount as a string.
     * @throws CancelException If the user cancels.
     * @throws QuitException If the user quits.
     * @throws SkipException If the user skips.
     */
    public String parseDollarAmount(String prompt, double defaultAmount, boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException {
        while (true) {
            String options = "";
            if (isCancelAllowed) options += " [type 'cancel' to cancel]";
            if (isQuitAllowed) options += " [type 'quit' to quit]";
            if (isSkipAllowed) options += " [type 'skip' to skip]";
            say(prompt + ", or just press enter to accept the amount " +
                com.hixon.financialApp.utility.Utility.formatDollarAmount(Math.abs(defaultAmount)) + options + ":  ");
            String newAmount = getLine();
            if (newAmount.isEmpty()) {
                return Double.toString(defaultAmount);
            }
            if (isCancelAllowed && newAmount.equalsIgnoreCase("cancel")) {
                throw new CancelException("User requested to cancel");
            }
            if (isQuitAllowed && newAmount.equalsIgnoreCase("quit")) {
                throw new QuitException("User requested to quit");
            }
            if (isSkipAllowed && newAmount.equalsIgnoreCase("skip")) {
                throw new SkipException("User requested to skip");
            }
            try {
                double parsed = getDouble(newAmount, "Invalid amount,");
                return String.valueOf(parsed);
            } catch (NumberFormatException e) {
                say("Invalid amount, please try again.");
            }
        }
    }

    /**
     * @inheritDoc
     */
    public int getNumberBetween(String prompt, int min, int max) throws CancelException, SkipException, QuitException {
        return getNumberBetween(prompt, min, max, null, false, false, false);
    }

    /**
     * @inheritDoc
     */
    public int getNumberBetween(String prompt, int min, int max, boolean isCancelAllowed, boolean isQuitAllowed,
                                boolean isSkipAllowed) throws CancelException, QuitException, SkipException {
        return getNumberBetween(prompt, min, max, null, isCancelAllowed, isQuitAllowed, isSkipAllowed);
    }

    /**
     * Enhanced getNumberBetween that supports a default value.
     */
    public int getNumberBetween(String prompt, int min, int max, Integer defaultValue, boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException {
        int result;
        String cancelSkipOrQuitPrompt = getCancelSkipOrQuitPrompt(isCancelAllowed, isQuitAllowed, isSkipAllowed);
        StringBuilder fullPrompt = new StringBuilder(prompt + " (" + min + " to " + max + ")");
        fullPrompt.append(cancelSkipOrQuitPrompt).append(": ");
        ask(fullPrompt.toString());
        boolean allowNone = (defaultValue != null);
        while (true) {
            String response = getResponseString("", null, false, allowNone, isCancelAllowed, isQuitAllowed, isSkipAllowed, null);
            if ((response == null || response.trim().isEmpty()) && defaultValue != null) {
                return defaultValue;
            }
            try {
                result = Integer.parseInt(response);
                if (result >= min && result <= max) {
                    break;
                }
                ask("Please enter a number from " + min + " to " + max + cancelSkipOrQuitPrompt + ":");
            } catch (NumberFormatException numberFormatException) {
                say("Please enter a number from " + min + " to " + max + cancelSkipOrQuitPrompt + ":");
            }
        }
        return result;
    }

    /**
     * @inheritDoc
     */
    @Override
    public NumberOrStringResponse getNumberBetweenOrString(String prompt, int min, int max) {
        try {
            return getNumberBetweenOrString(prompt, min, max, false, false, false);
        } catch (CancelException | QuitException | SkipException ignored) {
            return new NumberOrStringResponse(0);
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public NumberOrStringResponse getNumberBetweenOrString(
            String prompt,
            int min,
            int max,
            boolean isCancelAllowed,
            boolean isQuitAllowed,
            boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException {

        // Set up the skip and quit prompt strings if they are allowed.
        String cancelSkipOrQuitPrompt = getCancelSkipOrQuitPrompt(isCancelAllowed, isQuitAllowed, isSkipAllowed);
        String fullPrompt = prompt + " (" + min + " to " + max + ", a string" + cancelSkipOrQuitPrompt + "): ";
        while (true) {
            String response = "";
            try {
                ask(fullPrompt);
                response = getResponseString("", null, false, false, isCancelAllowed, isQuitAllowed, isSkipAllowed, null);
                if (response.equalsIgnoreCase("h")) continue;
                int result = Integer.parseInt(response);
                if (result >= min && result <= max) {
                    return new NumberOrStringResponse(result);
                } else {
                    say("The number you entered is not between " + min + " and " + max + ".");
                }
            } catch (NumberFormatException e) {
                if (response.equalsIgnoreCase("h")) continue;
                return new NumberOrStringResponse(response);
            }
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public String getResponseString() {
        String response;
        response = getResponseString("");
        return response;
    }

    /**
     * @inheritDoc
     */
    @Override
    public String getResponseString(String prompt) {
        String response;
        try {
            response = getResponseString(prompt, null, false, true, false, false, false, null);
        } catch (CancelException | QuitException | SkipException ignored) {
            response = "";
        }
        return response;
    }

    /**
     * @inheritDoc
     */
    @Override
    public String getResponseString(boolean allowNone, boolean showCancelQuitSkip, boolean isCancelAllowed, boolean isQuitAllowed,
                                    boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException {
        return getResponseString("", null, showCancelQuitSkip, allowNone, isCancelAllowed, isQuitAllowed, isSkipAllowed, null);
    }

    /**
     * @inheritDoc
     */
    @Override
    public String getResponseString(boolean allowNone, boolean showCancelQuitSkip, boolean isCancelAllowed, boolean isQuitAllowed,
                                    boolean isSkipAllowed, Supplier<String> helpCallback)
            throws CancelException, QuitException, SkipException {
        return getResponseString("", null, showCancelQuitSkip, allowNone, isCancelAllowed, isQuitAllowed, isSkipAllowed, helpCallback);
    }

    /**
     * Master method that handles all string input scenarios with prompt, default value, and full option support.
     * This is the core implementation that all other getResponseString methods delegate to.
     *
     * @param prompt             The prompt to display to the user (can be empty)
     * @param defaultValue       The default value to show and return if user hits enter (can be null)
     * @param showCancelQuitSkip If true, displays the cancel/quit/skip options in the prompt
     * @param allowNone          If true, allows empty input (user just hits enter)
     * @param isCancelAllowed    If true, allows the user to cancel by entering 'c'
     * @param isQuitAllowed      If true, allows the user to quit by entering 'q'
     * @param isSkipAllowed      If true, allows the user to skip by entering 's'
     * @param helpCallback       Optional callback function to provide help text when user enters 'h'
     * @return The string entered by the user
     * @throws CancelException If the user cancels the operation
     * @throws QuitException   If the user quits the operation
     * @throws SkipException   If the user skips the operation
     */
    @Override
    public String getResponseString(String prompt, String defaultValue, boolean showCancelQuitSkip, boolean allowNone,
                                    boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed,
                                    Supplier<String> helpCallback)
            throws CancelException, QuitException, SkipException {

        // Build the full prompt with default value and options
        StringBuilder fullPrompt = new StringBuilder();

        // Add the main prompt
        if (prompt != null && !prompt.isEmpty()) {
            fullPrompt.append(prompt);
        }

        // Add default value in brackets if provided
        if (defaultValue != null && !defaultValue.isEmpty()) {
            fullPrompt.append(" [").append(defaultValue).append("]");
            allowNone = true; // If there's a default, we must allow empty input
        }

        // Add cancel/quit/skip options if requested
        if (showCancelQuitSkip) {
            String cancelSkipOrQuitPrompt = getCancelSkipOrQuitPrompt(isCancelAllowed, isQuitAllowed, isSkipAllowed);
            if (!cancelSkipOrQuitPrompt.isEmpty()) {
                fullPrompt.append(cancelSkipOrQuitPrompt);
            }
        }

        // Add colon and space if we have any prompt
        if (fullPrompt.length() > 0) {
            fullPrompt.append(":  ");
            ask(fullPrompt.toString());
        }

        // Input loop
        while (true) {
            String response = getLine();

            // Check for help request
            if (helpCallback != null && response.equalsIgnoreCase("h")) {
                say(helpCallback.get());
                // Redisplay the prompt after help
                if (fullPrompt.length() > 0) {
                    ask(fullPrompt.toString());
                }
                continue;
            }

            // Check if response is empty
            if (response.isEmpty()) {
                if (defaultValue != null && !defaultValue.isEmpty()) {
                    return defaultValue; // Return default value
                } else if (allowNone) {
                    return ""; // Return empty string if allowed
                } else {
                    ask("Please enter a value:  ");
                    continue;
                }
            }

            // Check for special commands
            if (isCancelAllowed && response.equalsIgnoreCase("c")) {
                throw new CancelException("User asked to cancel this operation.");
            }
            if (isQuitAllowed && response.equalsIgnoreCase("q")) {
                throw new QuitException("User asked to abort processing.");
            }
            if (isSkipAllowed && response.equalsIgnoreCase("s")) {
                throw new SkipException("User asked to skip this item.");
            }

            return response; // Return the valid response
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public String getResponseStringMenuSelection(String prompt, boolean allowNone, boolean isCancelAllowed, boolean isQuitAllowed,
                                                 boolean isSkipAllowed) throws CancelException, QuitException, SkipException {
        return getResponseString(prompt, null, false, allowNone, isCancelAllowed, isQuitAllowed, isSkipAllowed, null);
    }

    /**
     * @inheritDoc
     */
    @Override
    public String getResponseStringMenuSelection(String prompt, boolean allowNone, boolean isCancelAllowed, boolean isQuitAllowed,
                                                 boolean isSkipAllowed, Supplier<String> helpCallback) throws CancelException, QuitException, SkipException {
        return getResponseString(prompt, null, false, allowNone, isCancelAllowed, isQuitAllowed, isSkipAllowed, helpCallback);
    }

    /**
     * @inheritDoc
     */
    @Override
    public String getResponseStringTextBox(String prompt, String defaultValue, boolean showCancelQuitSkip, boolean allowNone,
                                           boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException {
        return getResponseString(prompt, defaultValue, showCancelQuitSkip, allowNone, isCancelAllowed, isQuitAllowed, isSkipAllowed, null);
    }

    /**
     * @inheritDoc
     */
    @Override
    public String getResponseStringTextBox(String prompt, String defaultValue, boolean showCancelQuitSkip, boolean allowNone,
                                           boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed, Supplier<String> helpCallback)
            throws CancelException, QuitException, SkipException {
        return getResponseString(prompt, defaultValue, showCancelQuitSkip, allowNone, isCancelAllowed, isQuitAllowed, isSkipAllowed, helpCallback);
    }

    /**
     * @inheritDoc
     */
    @Override
    public Double getResponseDouble(String prompt, Double defaultValue, boolean showCancelQuitSkip, boolean allowNone,
                                    boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException {
        return getResponseDouble(prompt, defaultValue, showCancelQuitSkip, allowNone, isCancelAllowed, isQuitAllowed, isSkipAllowed, null);
    }

    /**
     * @inheritDoc
     */
    @Override
    public Double getResponseDouble(String prompt, Double defaultValue, boolean showCancelQuitSkip, boolean allowNone,
                                    boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed, Supplier<String> helpCallback)
            throws CancelException, QuitException, SkipException {

        // Build the full prompt with default value and options
        StringBuilder fullPrompt = new StringBuilder();

        // Add the main prompt
        if (prompt != null && !prompt.isEmpty()) {
            fullPrompt.append(prompt);
        }

        // Add default value in brackets if provided
        if (defaultValue != null) {
            fullPrompt.append(" [").append(defaultValue).append("]");
            allowNone = true; // If there's a default, we must allow empty input
        }

        // Add cancel/quit/skip options if requested
        if (showCancelQuitSkip) {
            String cancelSkipOrQuitPrompt = getCancelSkipOrQuitPrompt(isCancelAllowed, isQuitAllowed, isSkipAllowed);
            if (!cancelSkipOrQuitPrompt.isEmpty()) {
                fullPrompt.append(cancelSkipOrQuitPrompt);
            }
        }

        // Add colon and space if we have any prompt
        if (fullPrompt.length() > 0) {
            fullPrompt.append(":  ");
            ask(fullPrompt.toString());
        }

        // Input loop
        while (true) {
            String response = getLine();

            // Check for help request
            if (helpCallback != null && response.equalsIgnoreCase("h")) {
                say(helpCallback.get());
                // Redisplay the prompt after help
                if (fullPrompt.length() > 0) {
                    ask(fullPrompt.toString());
                }
                continue;
            }

            // Check if response is empty
            if (response.isEmpty()) {
                if (defaultValue != null) {
                    return defaultValue; // Return default value
                } else if (allowNone) {
                    return null; // Return null if allowed
                } else {
                    ask("Please enter a value:  ");
                    continue;
                }
            }

            // Check for special commands
            if (isCancelAllowed && response.equalsIgnoreCase("c")) {
                throw new CancelException("User asked to cancel this operation.");
            }
            if (isQuitAllowed && response.equalsIgnoreCase("q")) {
                throw new QuitException("User asked to abort processing.");
            }
            if (isSkipAllowed && response.equalsIgnoreCase("s")) {
                throw new SkipException("User asked to skip this item.");
            }

            // Try to parse as double
            try {
                return Double.parseDouble(response);
            } catch (NumberFormatException e) {
                ask("Invalid number format. Please enter a valid number:  ");
            }
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public Double getResponseCurrency(String prompt, Double defaultValue, boolean showCancelQuitSkip, boolean allowNone,
                                      boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException {
        return getResponseCurrency(prompt, defaultValue, showCancelQuitSkip, allowNone, isCancelAllowed, isQuitAllowed, isSkipAllowed, null);
    }

    /**
     * @inheritDoc
     */
    @Override
    public Double getResponseCurrency(String prompt, Double defaultValue, boolean showCancelQuitSkip, boolean allowNone,
                                      boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed, Supplier<String> helpCallback)
            throws CancelException, QuitException, SkipException {

        // Build the full prompt with default value and options
        StringBuilder fullPrompt = new StringBuilder();

        // Add the main prompt
        if (prompt != null && !prompt.isEmpty()) {
            fullPrompt.append(prompt);
        }

        // Add default value in brackets if provided
        if (defaultValue != null) {
            fullPrompt.append(" [").append(String.format("%.2f", defaultValue)).append("]");
            allowNone = true; // If there's a default, we must allow empty input
        }

        // Add cancel/quit/skip options if requested
        if (showCancelQuitSkip) {
            String cancelSkipOrQuitPrompt = getCancelSkipOrQuitPrompt(isCancelAllowed, isQuitAllowed, isSkipAllowed);
            if (!cancelSkipOrQuitPrompt.isEmpty()) {
                fullPrompt.append(cancelSkipOrQuitPrompt);
            }
        }

        // Add colon and space if we have any prompt
        if (fullPrompt.length() > 0) {
            fullPrompt.append(":  ");
            ask(fullPrompt.toString());
        }

        // Input loop
        while (true) {
            String response = getLine();

            // Check for help request
            if (helpCallback != null && response.equalsIgnoreCase("h")) {
                say(helpCallback.get());
                // Redisplay the prompt after help
                if (fullPrompt.length() > 0) {
                    ask(fullPrompt.toString());
                }
                continue;
            }

            // Check if response is empty
            if (response.isEmpty()) {
                if (defaultValue != null) {
                    return defaultValue; // Return default value
                } else if (allowNone) {
                    return null; // Return null if allowed
                } else {
                    ask("Please enter a value:  ");
                    continue;
                }
            }

            // Check for special commands
            if (isCancelAllowed && response.equalsIgnoreCase("c")) {
                throw new CancelException("User asked to cancel this operation.");
            }
            if (isQuitAllowed && response.equalsIgnoreCase("q")) {
                throw new QuitException("User asked to abort processing.");
            }
            if (isSkipAllowed && response.equalsIgnoreCase("s")) {
                throw new SkipException("User asked to skip this item.");
            }

            // Try to parse as currency (must have at most 2 decimal places)
            try {
                double value = Double.parseDouble(response);
                // Validate currency format (max 2 decimal places)
                String valueStr = String.format("%.2f", value);
                double roundedValue = Double.parseDouble(valueStr);
                if (Math.abs(value - roundedValue) > 0.001) {
                    ask("Currency must have at most 2 decimal places. Please re-enter:  ");
                    continue;
                }
                return value;
            } catch (NumberFormatException e) {
                ask("Invalid currency format. Please enter a valid amount:  ");
            }
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public Integer getResponseInt(String prompt, Integer defaultValue, boolean showCancelQuitSkip, boolean allowNone,
                                  boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException {
        return getResponseInt(prompt, defaultValue, showCancelQuitSkip, allowNone, true, isCancelAllowed, isQuitAllowed, isSkipAllowed, null);
    }

    /**
     * @inheritDoc
     */
    @Override
    public Integer getResponseInt(String prompt, Integer defaultValue, boolean showCancelQuitSkip, boolean allowNone,
                                  boolean allowNegativeValues, boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException {
        return getResponseInt(prompt, defaultValue, showCancelQuitSkip, allowNone, allowNegativeValues, isCancelAllowed, isQuitAllowed, isSkipAllowed, null);
    }

    /**
     * @inheritDoc
     */
    @Override
    public Integer getResponseInt(String prompt, Integer defaultValue, boolean showCancelQuitSkip, boolean allowNone,
                                  boolean allowNegativeValues, boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed,
                                  Supplier<String> helpCallback)
            throws CancelException, QuitException, SkipException {

        // Build the full prompt with default value and options
        StringBuilder fullPrompt = new StringBuilder();

        // Add the main prompt
        if (prompt != null && !prompt.isEmpty()) {
            fullPrompt.append(prompt);
        }

        // Add default value in brackets if provided
        if (defaultValue != null) {
            fullPrompt.append(" [").append(defaultValue).append("]");
            allowNone = true; // If there's a default, we must allow empty input
        }

        // Add cancel/quit/skip options if requested
        if (showCancelQuitSkip) {
            String cancelSkipOrQuitPrompt = getCancelSkipOrQuitPrompt(isCancelAllowed, isQuitAllowed, isSkipAllowed);
            if (!cancelSkipOrQuitPrompt.isEmpty()) {
                fullPrompt.append(cancelSkipOrQuitPrompt);
            }
        }

        // Add colon and space if we have any prompt
        if (fullPrompt.length() > 0) {
            fullPrompt.append(":  ");
            ask(fullPrompt.toString());
        }

        // Input loop
        while (true) {
            String response = getLine();

            // Check for help request
            if (helpCallback != null && response.equalsIgnoreCase("h")) {
                say(helpCallback.get());
                // Redisplay the prompt after help
                if (fullPrompt.length() > 0) {
                    ask(fullPrompt.toString());
                }
                continue;
            }

            // Check if response is empty
            if (response.isEmpty()) {
                if (defaultValue != null) {
                    return defaultValue; // Return default value
                } else if (allowNone) {
                    return null; // Return null if allowed
                } else {
                    ask("Please enter a value:  ");
                    continue;
                }
            }

            // Check for special commands
            if (isCancelAllowed && response.equalsIgnoreCase("c")) {
                throw new CancelException("User asked to cancel this operation.");
            }
            if (isQuitAllowed && response.equalsIgnoreCase("q")) {
                throw new QuitException("User asked to abort processing.");
            }
            if (isSkipAllowed && response.equalsIgnoreCase("s")) {
                throw new SkipException("User asked to skip this item.");
            }

            // Try to parse as integer
            try {
                int value = Integer.parseInt(response);
                // Check if negative values are allowed
                if (!allowNegativeValues && value < 0) {
                    ask("Negative values are not allowed. Please enter a positive number:  ");
                    continue;
                }
                return value;
            } catch (NumberFormatException e) {
                ask("Invalid number format. Please enter a valid integer:  ");
            }
        }
    }

    /**
     * Helper method to build the cancel/quit/skip prompt string.
     */
    @Override
    public String getCancelSkipOrQuitPrompt(boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed) {
        StringBuilder prompt = new StringBuilder();
        if (isCancelAllowed || isQuitAllowed || isSkipAllowed) {
            prompt.append(", ");
            List<String> options = new ArrayList<>();
            if (isCancelAllowed) options.add("'c' to cancel");
            if (isQuitAllowed) options.add("'q' to quit");
            if (isSkipAllowed) options.add("'s' to skip");
            prompt.append(String.join(", ", options));
        }
        return prompt.toString();
    }

    /**
     * Helper method for selectFromMenu to find a unique letter for a menu option.
     */
    private String findUniqueLetter(String option, List<String> usedLetters) {
        // Try first letter
        String firstLetter = option.substring(0, 1).toLowerCase();
        if (!usedLetters.contains(firstLetter)) {
            return firstLetter;
        }

        // Try first letter of each word
        String[] words = option.split("\\s+");
        for (String word : words) {
            if (word.length() > 0) {
                String letter = word.substring(0, 1).toLowerCase();
                if (!usedLetters.contains(letter)) {
                    return letter;
                }
            }
        }

        // Try all letters in the option
        for (char c : option.toLowerCase().toCharArray()) {
            if (Character.isLetter(c)) {
                String letter = String.valueOf(c);
                if (!usedLetters.contains(letter)) {
                    return letter;
                }
            }
        }

        // Fallback to any letter a-z
        for (char c = 'a'; c <= 'z'; c++) {
            String letter = String.valueOf(c);
            if (!usedLetters.contains(letter)) {
                return letter;
            }
        }

        // Should never reach here, but return 'x' as last resort
        return "x";
    }

    /**
     * Helper class to pair letters with options for menu sorting.
     */
    private static class LetterOptionPair {
        final String letter;
        final String option;

        LetterOptionPair(String letter, String option) {
            this.letter = letter;
            this.option = option;
        }
    }

    /**
     * Presents a menu of options to the user, allowing selection by unique letter.
     * Returns the selected option string, or null if none is selected.
     */
    @Override
    public String selectFromMenu(String prompt, List<String> options, boolean allowNone, boolean isCancelAllowed,
                                 boolean isQuitAllowed, boolean isSkipAllowed) throws CancelException, QuitException, SkipException {

        // Delegate to the selectFromFirstLetterList method:
        selectFromFirstLetterList(prompt, options, allowNone, isCancelAllowed, isQuitAllowed, isSkipAllowed);
    }

    /**
     * Presents a list of items with letter options and returns the user's selection.
     */
    protected String selectFromFirstLetterList(String prompt, String menuOptions) throws QuitException {
        return selectFromFirstLetterList(prompt, menuOptions, false, false, false);
    }

    protected String selectFromFirstLetterList(String prompt, String menuOptions, boolean isCancelAllowed,
                                               boolean isQuitAllowed, boolean isSkipAllowed) throws QuitException {
        try {
            return selectFromFirstLetterList(prompt, new ArrayList<>(), menuOptions, false, isCancelAllowed, isQuitAllowed, isSkipAllowed);
        } catch (CancelException | SkipException e) {
            return "";
        }
    }

    /**
     * Enhanced: Presents a list of items with unique letter options and returns the user's selection letter.
     * Uses findUniqueLetter to assign a unique letter to each option.
     */
    protected String selectFromFirstLetterList(String prompt, List<String> options, boolean allowNone,
                                               boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException {
        // Assign unique letters to each option
        List<String> usedLetters = new ArrayList<>();
        List<String> menuLetters = new ArrayList<>();
        for (String option : options) {
            String letter = findUniqueLetter(option, usedLetters);
            menuLetters.add(letter);
            usedLetters.add(letter);
        }
        // Add 'n' for None if allowed
        if (allowNone) {
            menuLetters.add("n");
            options.add("None");
        }
        // Display the menu
        sayH3(prompt);
        for (int i = 0; i < options.size(); i++) {
            say("  " + menuLetters.get(i) + " - " + options.get(i));
        }
        String menuOptionList = String.join(",", menuLetters);
        // Build prompt with available commands
        String cancelSkipOrQuitPrompt = getCancelSkipOrQuitPrompt(isCancelAllowed, isQuitAllowed, isSkipAllowed);
        ask("Enter your choice (" + menuOptionList + cancelSkipOrQuitPrompt + "): ");
        // Get response
        while (true) {
            String response = getLine().trim().toLowerCase();
            // Check for special commands
            if (isCancelAllowed && response.equalsIgnoreCase("c")) {
                throw new CancelException("User asked to cancel this operation.");
            }
            if (isQuitAllowed && response.equalsIgnoreCase("q")) {
                throw new QuitException("User asked to abort processing.");
            }
            if (isSkipAllowed && response.equalsIgnoreCase("s")) {
                throw new SkipException("User asked to skip this item.");
            }
            // Check if it's a valid menu option
            for (String letter : menuLetters) {
                if (response.equals(letter)) {
                    return letter;
                }
            }
            ask("Invalid choice. Please enter one of (" + menuOptionList + cancelSkipOrQuitPrompt + "): ");
        }
    }

    /**
     * Select from a numbered list using an enum with a default value.
     */
    protected <T extends Enum<T>> T selectFromNumberedList(String prompt, T defaultValue, Class<T> enumType)
            throws CancelException, QuitException, SkipException {
        T[] values = enumType.getEnumConstants();
        List<String> options = new ArrayList<>();
        for (T value : values) {
            options.add(value.toString());
        }

        int defaultIndex = defaultValue != null ? defaultValue.ordinal() : -1;

        sayH3(prompt);
        int i = 1;
        for (String option : options) {
            say("\t" + i++ + " - " + option);
        }

        String optionPrompt = "Select an option";
        if (defaultValue != null) {
            optionPrompt += " [" + defaultValue.toString() + "]";
        }
        optionPrompt += " (1 to " + (i - 1) + "): ";

        while (true) {
            ask(optionPrompt);
            String response = getLine().trim();

            if (response.isEmpty() && defaultValue != null) {
                return defaultValue;
            }

            try {
                int selection = Integer.parseInt(response);
                if (selection >= 1 && selection <= values.length) {
                    return values[selection - 1];
                } else {
                    say("Please enter a number between 1 and " + values.length + ".");
                }
            } catch (NumberFormatException e) {
                say("Invalid input. Please enter a number.");
            }
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public <T extends Enum<T>> T selectFromList(String prompt, T defaultValue, Class<T> enumType)
            throws CancelException, QuitException, SkipException {
        return selectFromNumberedList(prompt, defaultValue, enumType);
    }

    /**
     * @inheritDoc
     */
    @Override
    public Integer selectFromList(String prompt, List<String> items, Boolean allowNone) {
        return selectFromNumberedList(prompt, items, allowNone);
    }

    /**
     * @inheritDoc
     */
    @Override
    public Integer selectFromList(
            String prompt,
            List<String> items,
            Boolean allowNone,
            boolean isCancelAllowed,
            boolean isQuitAllowed,
            boolean isSkipAllowed
    ) throws CancelException, QuitException, SkipException {
        sayH3(prompt);
        for (int i = 0; i < items.size(); i++) {
            say("\t" + (i + 1) + " - " + items.get(i));
        }
        String cancelSkipOrQuitPrompt = getCancelSkipOrQuitPrompt(isCancelAllowed, isQuitAllowed, isSkipAllowed);
        String optionPrompt = "Select an option (1 to " + items.size() + cancelSkipOrQuitPrompt + "): ";
        while (true) {
            ask(optionPrompt);
            String response = getLine().trim();
            if (response.isEmpty() && Boolean.TRUE.equals(allowNone)) {
                return null;
            }
            if (isCancelAllowed && response.equalsIgnoreCase("c")) {
                throw new CancelException("User asked to cancel this operation.");
            }
            if (isQuitAllowed && response.equalsIgnoreCase("q")) {
                throw new QuitException("User asked to abort processing.");
            }
            if (isSkipAllowed && response.equalsIgnoreCase("s")) {
                throw new SkipException("User asked to skip this item.");
            }
            try {
                int selection = Integer.parseInt(response);
                if (selection >= 1 && selection <= items.size()) {
                    return items.get(selection - 1);
                } else {
                    say("Please enter a number between 1 and " + items.size() + ".");
                }
            } catch (NumberFormatException e) {
                say("Invalid input. Please enter a number.");
            }
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public NumberOrStringResponse selectFromListOrString(String prompt, List<String> items, boolean allowNone) {
        try {
            return selectFromNumberedListOrString(prompt, items, allowNone, false, false, false);
        } catch (CancelException | QuitException | SkipException ignored) {
            return new NumberOrStringResponse(0);
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public NumberOrStringResponse selectFromListOrString(
            String prompt,
            List<String> items,
            boolean allowNone,
            boolean allowCreate,
            boolean isCancelAllowed,
            boolean isQuitAllowed,
            boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException {
        return selectFromNumberedListOrString(prompt, items, allowNone, allowCreate, isCancelAllowed, isQuitAllowed, isSkipAllowed);
    }

    /**
     * @inheritDoc
     */
    @Override
    public boolean existsFileWithRetry(String fileContent, String filename) throws QuitException {
        boolean found = false;
        boolean done = false;
        while (!done) {
            try {
                Path path = Paths.get(filename);
                if (Files.exists(path) && !Files.isDirectory(path) && Files.size(path) > 0) {
                    done = true;
                    found = true;
                } else {
                    say("\n" + fileContent + " file " + filename + " does not exist or is empty.");
                    if (!getYesOrNo("Do you want to try again?")) {
                        done = true;
                    }
                }
            } catch (Exception e) {
                say("\n" + "Exception occurred trying to access " + fileContent + " file " + filename);
                say("\n" + "Exception was:  " + e);
                // Ask if user wants to retry, continue, or quit
                say();
                String prompt = "What would you like to do:  retry, continue without retrying, or quit?";
                String option = selectFromFirstLetterList(prompt, "r,c,q");

                switch (option) {
                    case "r":
                        // Continue the loop to retry
                        break;
                    case "c":
                        done = true;
                        break;
                    case "q":
                        throw new QuitException("Operation aborted at user request.");
                    default:
                        done = true;
                        break;
                }
            }
        }
        return found;
    }

    /**
     * @inheritDoc
     */
    @Override
    public boolean askRetryContinueQuit() throws QuitException {
        // Until the user makes a valid selection:
        boolean choice = false;

        // Ask the user if they would like to retry the operation, continue without retrying, or quit:
        say();
        String prompt = "What would you like to do:  retry, continue without retrying, or quit?";
        String option = selectFromFirstLetterList(prompt, "r,c,q");

        // Invoke a function to execute the user's request:
        switch (option) {
            case "r":
                choice = true;
                break;
            case "c":
                choice = false;
                break;
            case "q":
                throw new QuitException("Operation aborted at user request.");
        }
        return choice;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void showUser(User user) {
        sayH1("User Information");
        say("Name: " + user.getName());
        say("Email: " + user.getEmail());
        say("Phone: " + user.getPhoneNumber());
        say("Address: " + user.getAddress());
        say("Balance: " + Utility.formatDollarAmount(user.getBalance()));
        say("Credit Limit: " + Utility.formatDollarAmount(user.getCreditLimit()));
        say("Rewards Points: " + user.getRewardsPoints());
        say("Membership Level: " + user.getMembershipLevel());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void showTransaction(Transaction transaction) {
        sayH1("Transaction Details");
        say("ID: " + transaction.getId());
        say("Date: " + transaction.getDate());
        say("Amount: " + Utility.formatDollarAmount(transaction.getAmount()));
        say("Type: " + transaction.getType());
        say("Status: " + transaction.getStatus());
        say("Description: " + transaction.getDescription());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void showEntity(IndependentEntityInt entity) {
        sayH1("Entity Details");
        say("ID: " + entity.getId());
        say("Name: " + entity.getName());
        say("Type: " + entity.getType());
        say("Status: " + entity.getStatus());
        say("Created Date: " + entity.getCreatedDate());
        say("Modified Date: " + entity.getModifiedDate());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void showError(String message) {
        sayH1("Error");
        say(message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void showMessage(String message) {
        sayH1("Message");
        say(message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void showHelp(String topic) {
        String help = helpText.getProperty(topic);
        if (help != null) {
            sayH1("Help: " + topic);
            say(help);
        } else {
            say("No help available for this topic.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void pause(String message) {
        say(message);
        say("Press Enter to continue...");
        getLine();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        say("Thank you for using the application. Goodbye!");
        in.close();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getStartDate(String prompt, String defaultValue) {
        return getDate(prompt, defaultValue, "start date");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getEndDate(String prompt, String defaultValue) {
        return getDate(prompt, defaultValue, "end date");
    }

    /**
     * Helper method to get a date from the user with validation.
     */
    private String getDate(String prompt, String defaultValue, String dateType) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        dateFormat.setLenient(false);
        while (true) {
            String dateString = getResponseString(prompt, defaultValue, true, true, true, null);
            if (dateString.isEmpty()) {
                return null;
            }
            try {
                dateFormat.parse(dateString);
                return dateString;
            } catch (ParseException e) {
                say("Invalid " + dateType + " format. Please use yyyy-MM-dd.");
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void showCalendar(Calendar calendar) {
        sayH1("Calendar Events");
        for (String event : calendar.getEvents()) {
            say("Event: " + event);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void showEntityOrStringResult(EntityOrStringResult result) {
        if (result.isEntity()) {
            showEntity(result.getEntity());
        } else {
            say("String Result: " + result.getStringValue());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void showException(EntityException exception) {
        sayH1("Exception Details");
        say("Type: " + exception.getClass().getSimpleName());
        say("Message: " + exception.getMessage());
        say("Code: " + exception.getErrorCode());
        say("SQL State: " + exception.getSqlState());
    }

    /**
     * @inheritDoc
     */
    @Override
    public Calendar getStartDate() throws QuitException {
        say("Please enter the start date for the operation:");
        while (true) {
            try {
                String dateInput = getResponseString("Enter date (MM/dd/yyyy or MM-dd-yyyy)", null, false, false, false, false, true, null);
                SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
                dateFormat.setLenient(false);
                Calendar calendar = Calendar.getInstance();
                try {
                    calendar.setTime(dateFormat.parse(dateInput));
                    return calendar;
                } catch (ParseException e) {
                    // Try alternative format
                    dateFormat = new SimpleDateFormat("MM-dd-yyyy");
                    try {
                        calendar.setTime(dateFormat.parse(dateInput));
                        return calendar;
                    } catch (ParseException e2) {
                        say("Invalid date format. Please use MM/dd/yyyy or MM-dd-yyyy format.");
                    }
                }
            } catch (QuitException e) {
                throw e;
            } catch (CancelException | SkipException e) {
                // Should not happen since we disabled these options
                throw new RuntimeException("Unexpected exception", e);
            }
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public void beginImportItem(Transaction transaction) {
        sayH2("Processing Transaction");
        say("Transaction ID: " + transaction.getId());
        say("Date: " + transaction.getDate());
        say("Amount: " + Utility.formatDollarAmount(transaction.getAmount()));
        say("Description: " + transaction.getDescription());
        say();
    }

    /**
     * @inheritDoc
     */
    @Override
    public String[] getAndParseCsvLine(String prompt, int numberOfRequiredValues, boolean allowNullEntry, boolean allowSingleValue) {
        while (true) {
            String input = getResponseString(prompt);
            if (input.isEmpty() && allowNullEntry) {
                return null;
            }
            if (input.isEmpty() && !allowNullEntry) {
                say("Input is required. Please enter a value.");
                continue;
            }

            String[] values = input.split(",");
            for (int i = 0; i < values.length; i++) {
                values[i] = values[i].trim();
            }

            if (allowSingleValue && values.length == 1) {
                return values;
            }

            if (values.length == numberOfRequiredValues) {
                return values;
            }

            say("Expected " + numberOfRequiredValues + " comma-separated values, but got " + values.length + ". Please try again.");
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public User getUser(String prompt, List<User> users, boolean allowNull) {
        try {
            return getUser(prompt, users, allowNull, false, false, false);
        } catch (CancelException | QuitException | SkipException e) {
            // Should not happen since we disabled these options
            throw new RuntimeException("Unexpected exception", e);
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public User getUser(String prompt, List<User> users, boolean allowNull, boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException {
        if (users.isEmpty()) {
            say("No users available.");
            return null;
        }

        List<String> userNames = new ArrayList<>();
        for (User user : users) {
            userNames.add(user.getName() + " (" + user.getEmail() + ")");
        }

        Integer selection = selectFromList(prompt, userNames, allowNull, isCancelAllowed, isQuitAllowed, isSkipAllowed);
        if (selection == null) {
            return null;
        }

        return users.get(selection);
    }

    /**
     * @inheritDoc
     */
    @Override
    public int parseInt(String intString, String errorMessage) {
        while (true) {
            try {
                return Integer.parseInt(intString.trim());
            } catch (NumberFormatException e) {
                say(errorMessage);
                intString = getResponseString("Please enter a valid integer");
            }
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public <T extends IndependentEntityInt> T selectByNameFromList(String prompt, List<T> list, boolean allowNone)
            throws SQLException, EntityException {
        try {
            return selectByNameFromList(prompt, list, allowNone, false, false, false);
        } catch (CancelException | QuitException | SkipException e) {
            // Should not happen since we disabled these options
            throw new RuntimeException("Unexpected exception", e);
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public <T extends IndependentEntityInt> T selectByNameFromList(String prompt, List<T> list, boolean allowNone,
                                                                  boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed)
            throws SQLException, EntityException, CancelException, QuitException, SkipException {
        return selectByNameFromList(prompt, list, null, allowNone, isCancelAllowed, isQuitAllowed, isSkipAllowed);
    }

    /**
     * @inheritDoc
     */
    @Override
    public <T extends IndependentEntityInt> T selectByNameFromList(String prompt, List<T> list, T defaultValue,
                                                                  boolean allowNone, boolean isCancelAllowed,
                                                                  boolean isQuitAllowed, boolean isSkipAllowed)
            throws SQLException, EntityException, CancelException, QuitException, SkipException {
        if (list.isEmpty()) {
            say("No items available.");
            return null;
        }

        List<String> itemNames = new ArrayList<>();
        for (T item : list) {
            itemNames.add(item.getName());
        }

        sayH3(prompt);
        for (int i = 0; i < itemNames.size(); i++) {
            say("\t" + (i + 1) + " - " + itemNames.get(i));
        }

        String optionPrompt = "Select an option (1 to " + itemNames.size() + ")";
        if (defaultValue != null) {
            optionPrompt += " [" + defaultValue.getName() + "]";
        }
        if (allowNone) {
            optionPrompt += " or 0 for none";
        }

        String cancelSkipOrQuitPrompt = getCancelSkipOrQuitPrompt(isCancelAllowed, isQuitAllowed, isSkipAllowed);
        optionPrompt += cancelSkipOrQuitPrompt + ": ";

        while (true) {
            ask(optionPrompt);
            String response = getLine().trim();

            if (response.isEmpty() && defaultValue != null) {
                return defaultValue;
            }

            if (isCancelAllowed && response.equalsIgnoreCase("c")) {
                throw new CancelException("User asked to cancel this operation.");
            }
            if (isQuitAllowed && response.equalsIgnoreCase("q")) {
                throw new QuitException("User asked to abort processing.");
            }
            if (isSkipAllowed && response.equalsIgnoreCase("s")) {
                throw new SkipException("User asked to skip this item.");
            }

            try {
                int selection = Integer.parseInt(response);
                if (selection == 0 && allowNone) {
                    return null;
                }
                if (selection >= 1 && selection <= list.size()) {
                    return list.get(selection - 1);
                } else {
                    say("Please enter a number between " + (allowNone ? "0" : "1") + " and " + list.size() + ".");
                }
            } catch (NumberFormatException e) {
                say("Invalid input. Please enter a number.");
            }
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public <T extends IndependentEntityInt> EntityOrStringResult<T> selectByNameFromListOrString(
            String prompt, List<T> list, boolean allowNone, boolean allowCreate) throws EntityException {
        return selectByNameFromListOrString(prompt, list, IndependentEntityInt::getName, allowNone, allowCreate, false, false, false);
    }

    /**
     * @inheritDoc
     */
    @Override
    public <T extends IndependentEntityInt> EntityOrStringResult<T> selectByNameFromListOrString(
            String prompt, List<T> list, Function<T, String> getDisplayString, boolean allowNone, boolean allowCreate,
            boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed)
            throws EntityException, CancelException, QuitException, SkipException {

        if (list.isEmpty()) {
            if (allowCreate) {
                String newName = getResponseString("Enter name for new item", null, false, allowNone, isCancelAllowed, isQuitAllowed, isSkipAllowed, null);
                return new EntityOrStringResult<>(newName);
            } else {
                say("No items available.");
                return new EntityOrStringResult<>((T) null);
            }
        }

        List<String> itemNames = new ArrayList<>();
        for (T item : list) {
            itemNames.add(getDisplayString.apply(item));
        }

        sayH3(prompt);
        for (int i = 0; i < itemNames.size(); i++) {
            say("\t" + (i + 1) + " - " + itemNames.get(i));
        }

        String optionPrompt = "Select an option (1 to " + itemNames.size() + ")";
        if (allowNone) {
            optionPrompt += ", 0 for none";
        }
        if (allowCreate) {
            optionPrompt += ", or enter a new name";
        }

        String cancelSkipOrQuitPrompt = getCancelSkipOrQuitPrompt(isCancelAllowed, isQuitAllowed, isSkipAllowed);
        optionPrompt += cancelSkipOrQuitPrompt + ": ";

        while (true) {
            ask(optionPrompt);
            String response = getLine().trim();

            if (isCancelAllowed && response.equalsIgnoreCase("c")) {
                throw new CancelException("User asked to cancel this operation.");
            }
            if (isQuitAllowed && response.equalsIgnoreCase("q")) {
                throw new QuitException("User asked to abort processing.");
            }
            if (isSkipAllowed && response.equalsIgnoreCase("s")) {
                throw new SkipException("User asked to skip this item.");
            }

            try {
                int selection = Integer.parseInt(response);
                if (selection == 0 && allowNone) {
                    return new EntityOrStringResult<>((T) null);
                }
                if (selection >= 1 && selection <= list.size()) {
                    return new EntityOrStringResult<>(list.get(selection - 1));
                } else {
                    say("Please enter a number between " + (allowNone ? "0" : "1") + " and " + list.size() + ".");
                }
            } catch (NumberFormatException e) {
                if (allowCreate && !response.isEmpty()) {
                    return new EntityOrStringResult<>(response);
                } else {
                    say("Invalid input. Please enter a number" + (allowCreate ? " or a name" : "") + ".");
                }
            }
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public Calendar parseCalendarDate(String prompt, Calendar defaultDate) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
        dateFormat.setLenient(false);

        String defaultDateStr = defaultDate != null ? dateFormat.format(defaultDate.getTime()) : null;

        while (true) {
            String dateInput = getResponseString(prompt, defaultDateStr);
            if (dateInput.isEmpty() && defaultDate != null) {
                return defaultDate;
            }

            try {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(dateFormat.parse(dateInput));
                return calendar;
            } catch (ParseException e) {
                say("Invalid date format. Please use MM/dd/yyyy format.");
            }
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public String parseStringDate(String prompt, Calendar defaultDate) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
        String defaultDateStr = defaultDate != null ? dateFormat.format(defaultDate.getTime()) : null;

        while (true) {
            String dateInput = getResponseString(prompt, defaultDateStr);
            if (dateInput.isEmpty() && defaultDate != null) {
                return defaultDateStr;
            }

            try {
                dateFormat.parse(dateInput);
                return dateInput;
            } catch (ParseException e) {
                say("Invalid date format. Please use MM/dd/yyyy format.");
            }
        }
    }

    /**
     * Helper method for selectFromList that works with strings and returns the correct index.
     */
    protected Integer selectFromNumberedList(String prompt, List<String> items, Boolean allowNone) {
        sayH3(prompt);
        for (int i = 0; i < items.size(); i++) {
            say("\t" + (i + 1) + " - " + items.get(i));
        }
        String optionPrompt = "Select an option (1 to " + items.size() + ")";
        if (Boolean.TRUE.equals(allowNone)) {
            optionPrompt += " or 0 for none";
        }
        optionPrompt += ": ";

        while (true) {
            ask(optionPrompt);
            String response = getLine().trim();
            if (response.isEmpty() && Boolean.TRUE.equals(allowNone)) {
                return null;
            }
            try {
                int selection = Integer.parseInt(response);
                if (selection == 0 && Boolean.TRUE.equals(allowNone)) {
                    return null;
                }
                if (selection >= 1 && selection <= items.size()) {
                    return selection - 1; // Return 0-based index
                } else {
                    say("Please enter a number between " + (Boolean.TRUE.equals(allowNone) ? "0" : "1") + " and " + items.size() + ".");
                }
            } catch (NumberFormatException e) {
                say("Invalid input. Please enter a number.");
            }
        }
    }

    /**
     * Helper method for selectFromListOrString methods.
     */
    protected NumberOrStringResponse selectFromNumberedListOrString(String prompt, List<String> items, boolean allowNone, boolean allowCreate,
                                                                   boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException {
        sayH3(prompt);
        for (int i = 0; i < items.size(); i++) {
            say("\t" + (i + 1) + " - " + items.get(i));
        }

        String optionPrompt = "Select an option (1 to " + items.size() + ")";
        if (allowNone) {
            optionPrompt += ", 0 for none";
        }
        if (allowCreate) {
            optionPrompt += ", or enter a new value";
        }

        String cancelSkipOrQuitPrompt = getCancelSkipOrQuitPrompt(isCancelAllowed, isQuitAllowed, isSkipAllowed);
        optionPrompt += cancelSkipOrQuitPrompt + ": ";

        while (true) {
            ask(optionPrompt);
            String response = getLine().trim();

            if (isCancelAllowed && response.equalsIgnoreCase("c")) {
                throw new CancelException("User asked to cancel this operation.");
            }
            if (isQuitAllowed && response.equalsIgnoreCase("q")) {
                throw new QuitException("User asked to abort processing.");
            }
            if (isSkipAllowed && response.equalsIgnoreCase("s")) {
                throw new SkipException("User asked to skip this item.");
            }

            try {
                int selection = Integer.parseInt(response);
                if (selection == 0 && allowNone) {
                    return new NumberOrStringResponse(-1); // -1 indicates none selected
                }
                if (selection >= 1 && selection <= items.size()) {
                    return new NumberOrStringResponse(selection - 1); // Return 0-based index
                } else {
                    say("Please enter a number between " + (allowNone ? "0" : "1") + " and " + items.size() + ".");
                }
            } catch (NumberFormatException e) {
                if (allowCreate && !response.isEmpty()) {
                    return new NumberOrStringResponse(response);
                } else {
                    say("Invalid input. Please enter a number" + (allowCreate ? " or a value" : "") + ".");
                }
            }
        }
    }

    /**
     * Fix the selectFromMenu method to return the selected option string.
     */
    @Override
    public String selectFromMenu(String prompt, List<String> options, boolean allowNone, boolean isCancelAllowed,
                                 boolean isQuitAllowed, boolean isSkipAllowed) throws CancelException, QuitException, SkipException {
        String selectedLetter = selectFromFirstLetterList(prompt, options, allowNone, isCancelAllowed, isQuitAllowed, isSkipAllowed);

        // Map the selected letter back to the option string
        List<String> usedLetters = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            String letter = findUniqueLetter(options.get(i), usedLetters);
            usedLetters.add(letter);
            if (letter.equals(selectedLetter)) {
                return options.get(i);
            }
        }

        if (allowNone && selectedLetter.equals("n")) {
            return null;
        }

        return null; // Should not reach here
    }

    /**
     * Fix the selectFromFirstLetterList method that takes menuOptions parameter.
     */
    protected String selectFromFirstLetterList(String prompt, List<String> options, String menuOptions, boolean allowNone,
                                               boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException {
        // Parse the menuOptions string into individual options
}
