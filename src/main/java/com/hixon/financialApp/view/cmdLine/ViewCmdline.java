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
import java.util.*;
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
     * Reads a line of input from the user.
     * This method centralizes all input reading, making it easy to mock for unit testing.
     *
     * @return The line of text entered by the user
     */
    protected String getLine() {
        return in.nextLine();
    }

    public void say() {
        System.out.println();
        lastHeading = HeadingLevel.NONE;
    }

    public void say(String s) {
        System.out.println(s);
        lastHeading = HeadingLevel.NONE;
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

        if (s == null || s.isEmpty()) {
            return; // Do nothing for null or empty strings
        }

        // Only print blank line if not immediately following a higher-level heading
        if (lastHeading != HeadingLevel.H1 && lastHeading != HeadingLevel.H2) {
            System.out.println();
        }
        say("▸ " + s);
        lastHeading = HeadingLevel.H3;
    }

    public void ask(String s) {
        System.out.print(s);
        lastHeading = HeadingLevel.NONE;
    }

    /**
     * Wraps text to fit within a specified line width for command line display.
     * Preserves paragraph breaks (double newlines) and respects existing single newlines.
     * Words longer than the line width are not broken.
     *
     * @param text the text to wrap
     * @param maxLineWidth the maximum width of each line (default: 80 characters)
     * @return the wrapped text with appropriate line breaks
     */
    protected String wrapText(String text, int maxLineWidth) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        StringBuilder result = new StringBuilder();
        // Split by paragraph (double newline or \n\n)
        String[] paragraphs = text.split("\\n\\s*\\n");

        for (int p = 0; p < paragraphs.length; p++) {
            String paragraph = paragraphs[p].trim();

            if (paragraph.isEmpty()) {
                continue;
            }

            // Split paragraph into words
            String[] words = paragraph.split("\\s+");
            StringBuilder line = new StringBuilder();

            for (String word : words) {
                // If adding this word would exceed the line width, start a new line
                if (line.length() > 0 && line.length() + word.length() + 1 > maxLineWidth) {
                    result.append(line).append("\n");
                    line = new StringBuilder();
                }

                if (line.length() > 0) {
                    line.append(" ");
                }
                line.append(word);
            }

            // Append any remaining content in the line
            if (line.length() > 0) {
                result.append(line);
            }

            // Add paragraph break (but not after the last paragraph)
            if (p < paragraphs.length - 1) {
                result.append("\n\n");
            }
        }

        return result.toString();
    }

    /**
     * Wraps text to fit within 80 characters for command line display.
     *
     * @param text the text to wrap
     * @return the wrapped text with appropriate line breaks
     */
    protected String wrapText(String text) {
        return wrapText(text, 80);
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
                if (line.equals("C")) {
                    throw new CancelException("User asked to cancel this operation.");
                }
            }
            if (isQuitAllowed) {
                if (line.equals("Q")) {
                    throw new QuitException("User asked to abort processing.");
                }
            }
            if (isSkipAllowed) {
                if (line.equals("S")) {
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
     * @inheritDoc
     */
    public int getResponseIntBetween(String prompt, int min, int max) {
        try {
            return getResponseIntBetween(prompt, min, max, null, false, true, false, false, false, null);
        } catch (CancelException | QuitException | SkipException ignored) {
            // This should not happen since we specified that cancel, quit and skip are not allowed.
            return 0;
        }
    }

    /**
     * @inheritDoc
     */
    public int getResponseIntBetween(String prompt, int min, int max, boolean isCancelAllowed, boolean isQuitAllowed,
                                     boolean isSkipAllowed) throws CancelException, QuitException, SkipException {
        return getResponseIntBetween(prompt, min, max, null, false, true, isCancelAllowed, isQuitAllowed, isSkipAllowed,
                null);
    }

    /**
     * Enhanced getNumberBetween that supports a default value.
     */
    public int getResponseIntBetween(String prompt, int min, int max, Integer defaultValue, boolean allowNone,
                boolean showCancelQuitSkip, boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed,
                Supplier<String> helpCallBack)
            throws CancelException, QuitException, SkipException
    {
        Integer response;

        if (min >= max) {
            throw new IllegalArgumentException("The 'min' value must be less than the 'max' value.");
        }

        while (true) {
            response = getResponseInt(prompt, defaultValue, allowNone, showCancelQuitSkip,
                    isCancelAllowed, isQuitAllowed, isSkipAllowed, helpCallBack);
                 if (response >= min && response <= max) {
                    break;
                } else {
                    say("The number you entered is not between " + min + " and " + max + ".");
                }
        }

        return response;
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
                if (response.equals("?")) continue;
                int result = Integer.parseInt(response);
                if (result >= min && result <= max) {
                    return new NumberOrStringResponse(result);
                } else {
                    say("The number you entered is not between " + min + " and " + max + ".");
                }
            } catch (NumberFormatException e) {
                if (response.equals("?")) continue;
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
            response = getResponseString(prompt, null, DO_NOT_ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                    DO_NOT_ALLOW_CANCEL, DO_NOT_ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);
        } catch (CancelException | QuitException | SkipException ignored) {
            response = "";
        }
        return response;
    }

    /**
     * @inheritDoc
     */
    @Override
    public String getResponseString(String prompt, boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException {
        return getResponseString(prompt, null, DO_NOT_ALLOW_NONE, SHOW_CANCEL_QUIT_SKIP, isCancelAllowed,
                isQuitAllowed, isSkipAllowed, null);
    }

    /**
     * Master method that handles all string input scenarios with prompt, default value, and full option support.
     * This is the core implementation that all other getResponseString methods delegate to.
     *
     * @param prompt             The prompt to display to the user (can be empty)
     * @param defaultValue       The default value to show and return if user hits enter (can be null)
     * @param allowNone          If true, allows empty input (user just hits enter)
     * @param showCancelQuitSkip If true, displays the cancel/quit/skip options in the prompt
     * @param isCancelAllowed    If true, allows the user to cancel by entering 'C'
     * @param isQuitAllowed      If true, allows the user to quit by entering 'Q'
     * @param isSkipAllowed      If true, allows the user to skip by entering 'S'
     * @param helpCallback       Optional callback function to provide help text when user enters '?'
     * @return The string entered by the user
     * @throws CancelException If the user cancels the operation
     * @throws QuitException   If the user quits the operation
     * @throws SkipException   If the user skips the operation
     */
    @Override
    public String getResponseString(String prompt, String defaultValue, boolean allowNone, boolean showCancelQuitSkip,
                boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed, Supplier<String> helpCallback)
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
            if (helpCallback != null && response.equals("?")) {
                say(wrapText(helpCallback.get()));
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
            if (isCancelAllowed && response.equals("C")) {
                throw new CancelException("User asked to cancel this operation.");
            }
            if (isQuitAllowed && response.equals("Q")) {
                throw new QuitException("User asked to abort processing.");
            }
            if (isSkipAllowed && response.equals("S")) {
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
        return getResponseString(prompt, null, allowNone, false, isCancelAllowed, isQuitAllowed, isSkipAllowed, null);
    }

    /**
     * @inheritDoc
     */
    @Override
    public String getResponseStringMenuSelection(String prompt, boolean allowNone, boolean isCancelAllowed, boolean isQuitAllowed,
                                                 boolean isSkipAllowed, Supplier<String> helpCallback) throws CancelException, QuitException, SkipException {
        return getResponseString(prompt, null, allowNone, false, isCancelAllowed, isQuitAllowed, isSkipAllowed, helpCallback);
    }

    /**
     * {@inheritDoc}
     */
    public double getResponseDouble(String prompt) {
        try {
            return getResponseDouble(prompt, null, DO_NOT_SHOW_CANCEL_QUIT_SKIP, DO_NOT_ALLOW_NONE,
                    DO_NOT_ALLOW_CANCEL, DO_NOT_ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);
        } catch (CancelException | QuitException | SkipException ignored) {
            // This should not happen since we specified that cancel, quit and skip are not allowed.
            return (double) 0.0;
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public Double getResponseDouble(String prompt, boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException {
        return getResponseDouble(prompt, null, SHOW_CANCEL_QUIT_SKIP, DO_NOT_ALLOW_NONE, isCancelAllowed,
                isQuitAllowed, isSkipAllowed, null);
    }

    /**
     * @inheritDoc
     */
    @Override
    public Double getResponseDouble(String prompt, Double defaultValue, boolean showCancelQuitSkip, boolean allowNone,
                boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed, Supplier<String> helpCallback)
            throws CancelException, QuitException, SkipException {

        // Input loop
        String defaultValueStr = (defaultValue != null) ? defaultValue.toString() : null;
        while (true) {
            String response = getResponseString(prompt, defaultValueStr, allowNone, showCancelQuitSkip, isCancelAllowed,
                    isQuitAllowed, isSkipAllowed, helpCallback);

            // Try to parse as double
            try {
                return Double.parseDouble(response);
            } catch (NumberFormatException e) {
                ask("Invalid number format. Please enter a valid number:  ");
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public double getResponseCurrency(String prompt) {
        try {
            return getResponseCurrency(prompt, null, DO_NOT_SHOW_CANCEL_QUIT_SKIP, DO_NOT_ALLOW_NONE,
                    DO_NOT_ALLOW_CANCEL, DO_NOT_ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);
        } catch (CancelException | QuitException | SkipException ignored) {
            return (double) 0.0;
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public Double getResponseCurrency(String prompt, boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException {
        return getResponseCurrency(prompt, null, SHOW_CANCEL_QUIT_SKIP, DO_NOT_ALLOW_NONE, isCancelAllowed,
                isQuitAllowed, isSkipAllowed, null);
    }

    /**
     * @inheritDoc
     */
    @Override
    public Double getResponseCurrency(String prompt, Double defaultValue, boolean showCancelQuitSkip, boolean allowNone,
                  boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed, Supplier<String> helpCallback)
            throws CancelException, QuitException, SkipException {

        // Until we get a valid currency value, keep asking:
        while (true) {
            try {
                // Try to parse as currency (must have at most 2 decimal places)
                double value = getResponseDouble(prompt, defaultValue, showCancelQuitSkip, allowNone, isCancelAllowed,
                        isQuitAllowed, isSkipAllowed, helpCallback);

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
    public Integer getResponseInt(String prompt)
            throws CancelException, QuitException, SkipException {
        return getResponseInt(prompt, null, DO_NOT_ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP, DO_NOT_ALLOW_CANCEL,
                DO_NOT_ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);
    }

    /**
     * @inheritDoc
     */
    @Override
    public Integer getResponseInt(String prompt, boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException {
        return getResponseInt(prompt, null, DO_NOT_ALLOW_NONE, SHOW_CANCEL_QUIT_SKIP, isCancelAllowed,
                isQuitAllowed, isSkipAllowed, null);
    }

    /**
     * @inheritDoc
     */
    @Override
    public Integer getResponseInt(String prompt, Integer defaultValue, boolean allowNone, boolean showCancelQuitSkip,
                  boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed, Supplier<String> helpCallback)
            throws CancelException, QuitException, SkipException
    {
        // Input loop
        String defaultValueStr = (defaultValue != null) ? defaultValue.toString() : null;
        while (true) {
            String response = getResponseString(prompt, defaultValueStr, allowNone, showCancelQuitSkip,
                    isCancelAllowed, isQuitAllowed, isSkipAllowed, helpCallback);

            // Try to parse as integer
            try {
                int value = Integer.parseInt(response);
                return value;
            } catch (NumberFormatException e) {
                ask("Invalid number format. Please enter a valid integer:  ");
            }
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public Integer getResponseNatural(String prompt)
            throws CancelException, QuitException, SkipException
    {
        return getResponseNatural(prompt, null, DO_NOT_ALLOW_NONE, SHOW_CANCEL_QUIT_SKIP, DO_NOT_ALLOW_CANCEL,
                DO_NOT_ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);
    }

    /**
     * @inheritDoc
     */
    @Override
    public Integer getResponseNatural(String prompt,
                                      boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException
    {
        return getResponseNatural(prompt, null, DO_NOT_ALLOW_NONE, SHOW_CANCEL_QUIT_SKIP, isCancelAllowed, isQuitAllowed,
                isSkipAllowed, null);
    }

    /**
     * @inheritDoc
     */
    @Override
    public Integer getResponseNatural(String prompt, Integer defaultValue, boolean allowNone, boolean showCancelQuitSkip,
                  boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed, Supplier<String> helpCallback)
            throws CancelException, QuitException, SkipException {
        Integer result;
        while (true) {
            result = getResponseInt(prompt, defaultValue, allowNone, showCancelQuitSkip, isCancelAllowed, isQuitAllowed,
                    isSkipAllowed, helpCallback);
            if (result != null && result < 0) {
                say("Negative values aren't allowed. Please enter a non-negative integer.");
            } else {
                return result;
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
            prompt.append(" (or ");
            List<String> options = new ArrayList<>();
            if (isCancelAllowed) options.add("'C' to cancel");
            if (isQuitAllowed) options.add("'Q' to quit");
            if (isSkipAllowed) options.add("'S' to skip");
            prompt.append(String.join(", ", options));
            prompt.append(")");
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
    public String selectFromMenu(String prompt, List<String> options, boolean allowNone, boolean showCancelQuitSkip,
        boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed) throws CancelException, QuitException,
            SkipException
    {
        // Delegate to the selectFromFirstLetterList method:
        return selectFromFirstLetterList(prompt, options, allowNone, showCancelQuitSkip, isCancelAllowed, isQuitAllowed,
                isSkipAllowed);
    }

    /**
     * Presents a list of items with letter options and returns the user's selection.
     */
    protected String selectFromFirstLetterList(String prompt, List<String> options) throws QuitException {
        return selectFromFirstLetterList(prompt, options, DO_NOT_ALLOW_NONE);
    }

    protected String selectFromFirstLetterList(String prompt, List<String> options, boolean allowNone) {
        try {
            return selectFromFirstLetterList(prompt, new ArrayList<>(), DO_NOT_ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                    DO_NOT_ALLOW_CANCEL, DO_NOT_ALLOW_QUIT, DO_NOT_ALLOW_SKIP);
        } catch (CancelException | QuitException | SkipException e) {
            return "";
        }
    }

    /**
     * Presents a list of items with unique letter options and returns the user's selection letter. Uses findUniqueLetter
     * to assign a unique letter to each option.
     */
    protected String selectFromFirstLetterList(String prompt, List<String> options, boolean allowNone,
               boolean showCancelQuitSkip, boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException
    {
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
        StringBuilder promptBuilder = new StringBuilder("Enter your choice ");

        // Get response
        while (true) {
            String response = getResponseString(promptBuilder.toString(), null, allowNone, showCancelQuitSkip,
                    isCancelAllowed, isQuitAllowed, isSkipAllowed, null).toLowerCase();

            if(response.isEmpty() && allowNone) {
                return null; // User selected None
            }

            // Check if it's a valid menu option
            for (String option : menuLetters) {
                if (response.equals(option)) {
                    return option;
                }
            }

            // Build error prompt
            StringBuilder errorPrompt = new StringBuilder("Invalid choice. Please enter one of (");
            errorPrompt.append(menuOptionList);
            errorPrompt.append(")");
            say(errorPrompt.toString());
        }
    }

    /**
     * Select from a numbered list, returning the selected index or null if a string was entered.
     */
    protected Integer selectFromNumberedList(String prompt, List<String> items, boolean allowNone,
                                             boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException {

        NumberOrStringResponse response = selectFromNumberedListOrString(prompt, items, allowNone, DO_NOT_ALLOW_CREATE,
                isCancelAllowed, isQuitAllowed, isSkipAllowed);
        if (response != null && response.isNumber()) {
            return response.getSelectedIndex();
        } else {
            return null; // Indicate that a string was entered instead of a number
        }
    }

    /**
     * Select from a numbered list using an enum with a default value. Supports help via '?' followed by a number
     * (e.g., '? 3' for help on option 3).
     */
    protected <T extends Enum<T>> T selectFromNumberedList(String prompt, T defaultValue, Class<T> enumType) {
        try {
            return selectFromNumberedList(prompt,defaultValue, enumType, true, DO_NOT_ALLOW_CANCEL, DO_NOT_ALLOW_QUIT,
                    DO_NOT_ALLOW_SKIP);
        } catch (CancelException | QuitException | SkipException ignored) {
            return defaultValue;
        }
    }

    /**
     * Select from a numbered list using an enum with a default value.
     * Supports help via '?' followed by a number (e.g., '? 3' for help on option 3).
     */
    protected <T extends Enum<T>> T selectFromNumberedList(String prompt, T defaultValue, Class<T> enumType,
                   boolean showCancelQuitSkip, boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException {
        T[] values = enumType.getEnumConstants();
        List<String> options = new ArrayList<>();
        for (T value : values) {
            options.add(Utility.formatEnumName(value.toString()));
        }

        int defaultIndex = defaultValue != null ? defaultValue.ordinal() : -1;

        sayH3(prompt);
        int i = 1;
        for (String option : options) {
            say("\t" + i++ + " - " + option);
        }

        // Build the special options prompt
        StringBuilder specialOptions = new StringBuilder();

        if (showCancelQuitSkip) {
            specialOptions.append(getCancelSkipOrQuitPrompt(isCancelAllowed, isQuitAllowed, isSkipAllowed));
        }

        String optionPrompt = "Enter your choice";
        while (true) {
            String response = getResponseString(optionPrompt, (defaultIndex != -1) ? String.valueOf(defaultIndex + 1) : null,
                    DO_NOT_ALLOW_NONE, showCancelQuitSkip, isCancelAllowed, isQuitAllowed, isSkipAllowed,
                    () -> "Enter '?' followed by a number (e.g., '? 3') for help on a specific option.");

            if (response.isEmpty() && defaultValue != null) {
                return defaultValue;
            }

            // Check for help request: '?' followed by optional whitespace and a number
            if (response.matches("^\\?\\s*\\d+$")) {
                try {
                    // Extract the number after '?' and optional whitespace
                    String numberPart = response.replaceFirst("^\\?\\s*", "");
                    int helpIndex = Integer.parseInt(numberPart);

                    if (helpIndex >= 1 && helpIndex <= values.length) {
                        T enumValue = values[helpIndex - 1];
                        String enumClassName = enumType.getSimpleName().toLowerCase();
                        String enumValueName = enumValue.name().toLowerCase();
                        String helpKey = enumClassName + "." + enumValueName;

                        // Load help text from properties file
                        try (InputStream input = getClass().getClassLoader()
                                .getResourceAsStream("help-text.properties")) {
                            if (input != null) {
                                Properties helpProperties = new Properties();
                                helpProperties.load(input);
                                String helpText = helpProperties.getProperty(helpKey);

                                if (helpText != null && !helpText.trim().isEmpty()) {
                                    ask("\nHelp for " + Utility.formatEnumName(enumValue.toString()) + ":  ");
                                    say(wrapText(helpText));
                                } else {
                                    say("No help available for " + enumValue.toString() + " (key: " + helpKey + ").");
                                }
                            } else {
                                say("Help text file not found.");
                            }
                        } catch (IOException e) {
                            say("Error loading help text: " + e.getMessage());
                        }
                    } else {
                        say("Please enter a help number between 1 and " + values.length + " (e.g., '? " + Math.min(3, values.length) + "').");
                    }
                    continue; // Stay in the loop for another selection
                } catch (NumberFormatException e) {
                    say("Invalid help format. Use '?' followed by a number (e.g., '? 3').");
                    continue;
                }
            }

            try {
                int selection = Integer.parseInt(response);
                if (selection >= 1 && selection <= values.length) {
                    return values[selection - 1];
                } else {
                    say("Please enter a number between 1 and " + values.length + ".");
                }
            } catch (NumberFormatException e) {
                say("Invalid input. Please enter a number, or '?' followed by a number for help.");
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends IndependentEntityInt> T selectByNameFromList(
            String prompt,
            List<T> list,
            boolean allowNone)
            throws SQLException, EntityException {

        // Call the full selectByNameFromList method specifying no cancel, quit or skip:
        try {
            return selectByNameFromList(prompt, list, allowNone, false, false, false);
        } catch (CancelException | SkipException | QuitException ignored) {
            return null;
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public <T extends IndependentEntityInt> T selectByNameFromList(
            String prompt,
            List<T> list,
            boolean allowNone,
            boolean isCancelAllowed,
            boolean isQuitAllowed,
            boolean isSkipAllowed)
            throws SQLException, EntityException, CancelException, QuitException, SkipException {

        // Call the new method with null as the default value
        return selectByNameFromList(prompt, list, null, allowNone, true, isCancelAllowed, isQuitAllowed, isSkipAllowed, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends IndependentEntityInt> T selectByNameFromList(
            String prompt,
            List<T> list,
            T defaultValue,
            boolean allowNone,
            boolean showCancelQuitSkip,
            boolean isCancelAllowed,
            boolean isQuitAllowed,
            boolean isSkipAllowed,
            Supplier<String> helpCallback)
            throws EntityException, CancelException, QuitException, SkipException {

        // Prepare a list of names from the entity list
        List<String> names = new ArrayList<>();
        for (T entity : list) {
            names.add(entity.getName());
        }

        // Find the default index if a default value is provided:
        Integer defaultIndex = null;
        if (defaultValue != null) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).getId().equals(defaultValue.getId())) {
                    defaultIndex = i;
                    break;
                }
            }
            if (defaultIndex == null) {
                throw new IllegalArgumentException("The provided default value was not found in the list.");
            }
        }


        // Call selectFromNumberedList to get the selected index
        NumberOrStringResponse response = selectFromNumberedListOrString(prompt, names, defaultIndex, allowNone,
                DO_NOT_ALLOW_CREATE, showCancelQuitSkip, isCancelAllowed, isQuitAllowed, isSkipAllowed, helpCallback);

        // If null or -1 was returned (none selected), return null
        if (response == null) {
            return null;
        }

        // Return the corresponding item from the original entity list
        return list.get(response.getSelectedIndex());
    }

    /**
     * @inheritDoc
     */
    @Override
    public NumberOrStringResponse selectFromListOrString(String prompt, List<String> items, boolean allowNone) {
        try {
            return selectFromListOrString(prompt, items, allowNone, false, false, false, false);
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
        return selectFromNumberedListOrString(prompt, items, allowNone, allowCreate, isCancelAllowed, isQuitAllowed,
                isSkipAllowed);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Calendar getStartDate() throws QuitException {
        return parseCalendarDate("Please enter the start date", null);
    }

    /**
     * @inheritDoc
     */
    @Override
    public String parseDollarAmount(String prompt, double defaultAmount) {
        try {
            return parseDollarAmount(prompt, defaultAmount, DO_NOT_ALLOW_CANCEL, DO_NOT_ALLOW_QUIT, DO_NOT_ALLOW_SKIP);
        } catch (CancelException | QuitException | SkipException e) {
            return "";
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public String parseDollarAmount(String prompt, double defaultAmount, boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException {
        String cancelSkipOrQuitPrompt = getCancelSkipOrQuitPrompt(isCancelAllowed, isQuitAllowed, isSkipAllowed);
        say(prompt + ", or just press enter to accept the amount " +
                Utility.formatDollarAmount(Math.abs(defaultAmount)) + cancelSkipOrQuitPrompt + ":  ");

        while (true) {
            String newAmount = in.nextLine();

            // Check for special commands first
            if (isCancelAllowed && newAmount.equals("C")) {
                throw new CancelException("User asked to cancel this operation.");
            }
            if (isQuitAllowed && newAmount.equals("Q")) {
                throw new QuitException("User asked to abort processing.");
            }
            if (isSkipAllowed && newAmount.equals("S")) {
                throw new SkipException("User asked to skip this item.");
            }

            // Handle empty input (use default)
            if (newAmount.isEmpty()) {
                return Double.toString(defaultAmount);
            }

            // Try to parse the amount
            try {
                double parsedAmount = getResponseDouble(newAmount);
                return String.valueOf(parsedAmount);
            } catch (Exception e) {
                say("Invalid amount. Please re-enter" + cancelSkipOrQuitPrompt + ":  ");
            }
        }
    }

    // Parse a date in mm/dd/yy format:
    public String parseStringDate(String prompt, Calendar defaultDate) {
        ask(prompt);
        if (defaultDate == null) {
            say(" (mm/dd/yy)");
        } else {
            say(" (mm/dd/yy) or just hit enter to accept the date " + Utility.calendarDateToStringDate(defaultDate));
        }
        String line = getLine();
        boolean done = false;
        while (!done) {
            try {
                if (defaultDate != null && line.isEmpty()) {
                    line = Utility.calendarDateToStringDate(defaultDate);
                    done = true;
                } else {
                    if (Utility.stringDateDashToCalendarDate(line) != null) {
                        done = true;
                    } else {
                        say("Invalid date format.  Please re-enter:");
                        line = getLine();
                    }
                }
            } catch (ParseException e) {
                say("Invalid date format.  Please re-enter:");
                line = getLine();
            }
        }
        return line;
    }

    /**
     * This method checks if a file exists on the file with the name matching the passed in filename.  If the file is
     * not found, then it will ask the user if they want to try again allowing them to create, find, etc. the file.
     *
     * @param fileType A description of the file to be used when interacting with the user if not found.
     * @param fileName The name of the file to check for existence.
     * @return True if the file exists.  Otherwise, false.
     */
    public boolean existsFileWithRetry(String fileType, String fileName) throws QuitException {

        boolean found = false;
        boolean done = false;
        while (!done) {
            try {
                Path path = Paths.get(fileName);
                if (Files.exists(path) && !Files.isDirectory(path) && Files.size(path) > 0) {
                    done = true;
                    found = true;
                } else {
                    say("\n" + fileType + " file " + fileName + " does not exist or is empty.");
                    if (!getYesOrNo("Do you want to try again?")) {
                        done = true;
                    }
                }
            } catch (Exception e) {
                say("\n" + "Exception occurred trying to access " + fileType + " file " + fileName);
                say("\n" + "Exception was:  " + e);
                if (!askRetryContinueQuit()) {
                    done = true;
                }
            }
        }
        return found;
    }

    /**
     * {@inheritDoc}
     */
    public boolean askRetryContinueQuit() throws QuitException {

        // Until the user makes a valid selection:
        boolean choice;

        // Ask the use if they would like to retry the operation, continue without retrying, or quit:
        say();
        String prompt = "What would you like to do:  retry, continue without retrying, or quit?";
        String option = selectFromFirstLetterList(prompt, new ArrayList<>(List.of("retry", "continue", "quit")));

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

            default:
                choice = false;
                break;
        }
        return choice;
    }

    /**
     * @inheritDoc
     */
    @Override
    public Integer selectByPositionFromList(String prompt, List<String> items, boolean allowNone) {
        try {
            return selectByPositionFromList(prompt, items, allowNone, DO_NOT_ALLOW_CANCEL, DO_NOT_ALLOW_QUIT, DO_NOT_ALLOW_SKIP);
        } catch (CancelException | QuitException | SkipException ignored) {
            return -1;
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public Integer selectByPositionFromList(
            String prompt,
            List<String> items,
            boolean allowNone,
            boolean isCancelAllowed,
            boolean isQuitAllowed,
            boolean isSkipAllowed
    ) throws CancelException, QuitException, SkipException {
        return selectFromNumberedList(prompt, items, allowNone, isCancelAllowed,
                isQuitAllowed, isSkipAllowed);
    }

    /**
     * @inheritDoc
     */
    @Override
    public <T extends Enum<T>> T selectByPositionFromList(String prompt, T defaultValue, Class<T> enumType) {
        return selectFromNumberedList(prompt, defaultValue, enumType);
    }

    /**
     * @inheritDoc
     */
    @Override
    public <T extends Enum<T>> T selectByPositionFromList(String prompt, T defaultValue, Class<T> enumType,
                      boolean showCancelQuitSkip, boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException {
        return selectFromNumberedList(prompt, defaultValue, enumType, showCancelQuitSkip, isCancelAllowed, isQuitAllowed,
                isSkipAllowed);
    }

    /**
     * Helper method for selectFromListOrString methods.
     */
    public NumberOrStringResponse selectFromNumberedListOrString(String prompt, List<String> items, boolean allowNone, boolean allowCreate,
                                                                 boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException {
        return selectFromNumberedListOrString(prompt, items, null, allowNone, allowCreate, SHOW_CANCEL_QUIT_SKIP, isCancelAllowed, isQuitAllowed, isSkipAllowed, null);
    }

    /**
     * Helper method for selectFromListOrString methods.
     */
    public NumberOrStringResponse selectFromNumberedListOrString(
            String prompt,
            List<String> items,
            Integer defaultItemIndex,
            boolean allowNone,
            boolean allowCreate,
            boolean showCancelQuitSkipPrompt,
            boolean isCancelAllowed,
            boolean isQuitAllowed,
            boolean isSkipAllowed,
            Supplier<String> helpSupplier)
            throws CancelException, QuitException, SkipException {

        sayH3(prompt);

        String optionPrompt = "Enter your choice";

        if (allowNone) {
            optionPrompt += ", 0 for none";
        }

        if (allowCreate) {
            // Check if this is a search/selection context vs. a create new entity context
            if (prompt.toLowerCase().contains("select") || prompt.toLowerCase().contains("search")) {
                optionPrompt += ", or enter a new search string";
            } else {
                optionPrompt += ", or enter a new value";
            }
        }

        // Display the list of items:
        for (int i = 0; i < items.size(); i++) {
            say("  " + (i + 1) + " - " + items.get(i));
        }

        while (true) {

            // Convert defaultItemIndex to 1-based for display (getResponseString will add the brackets)
            String defaultItemIndexStr = (defaultItemIndex != null) ? String.valueOf(defaultItemIndex + 1) : null;
            String response = getResponseString(optionPrompt, defaultItemIndexStr, allowNone,
                    showCancelQuitSkipPrompt, isCancelAllowed, isQuitAllowed, isSkipAllowed, helpSupplier);

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
     * @inheritDoc
     */
    @Override
    public void beginImportItem(Transaction transaction) {
        // Nothing to do for this type of command line resolver.
    }

    /**
     * @inheritDoc
     */
    @Override
    public String[] getAndParseCsvLine(String prompt, int numberOfRequiredValues, boolean allowNullEntry,
                                       boolean allowSingleValue)
            throws CancelException, QuitException, SkipException
    {
        while (true) {
            String input = getResponseString(prompt, null, allowNullEntry, SHOW_CANCEL_QUIT_SKIP,
                    DO_NOT_ALLOW_CANCEL, DO_NOT_ALLOW_QUIT, DO_NOT_ALLOW_SKIP,
                    () -> {
                        StringBuilder help = new StringBuilder();
                        help.append("Enter ").append(numberOfRequiredValues)
                            .append(" comma-separated value").append(numberOfRequiredValues > 1 ? "s" : "")
                            .append(".\n");
                        help.append("Example: value1, value2");
                        if (numberOfRequiredValues > 2) {
                            help.append(", value3");
                            if (numberOfRequiredValues > 3) {
                                help.append(", ...");
                            }
                        }
                        if (allowSingleValue) {
                            help.append("\nYou may also enter a single value without commas.");
                        }
                        if (allowNullEntry) {
                            help.append("\nPress Enter without typing anything to skip this entry.");
                        }
                        return help.toString();
                    });
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
    public User getUser(String prompt, List<User> users, boolean allowNull) throws SQLException, EntityException {
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
    public User getUser(String prompt, List<User> users, boolean allowNull, boolean isCancelAllowed,
                        boolean isQuitAllowed, boolean isSkipAllowed)
            throws SQLException, EntityException, CancelException, QuitException, SkipException {
        if (users.isEmpty()) {
            say("No users available.");
            return null;
        }

        List<String> userNames = new ArrayList<>();
        for (User user : users) {
            userNames.add(user.getName() + " (" + user.getEmail() + ")");
        }

        Integer selection = selectByPositionFromList(prompt, userNames, allowNull, isCancelAllowed, isQuitAllowed, isSkipAllowed);
        if (selection == null) {
            return null;
        }

        return users.get(selection);
    }

    /**
     * @inheritDoc
     */
    @Override
    public <T extends IndependentEntityInt> EntityOrStringResult<T> selectByNameFromListOrString(
            String prompt, List<T> list, boolean allowNone, boolean allowCreate) throws SQLException, EntityException {
        try {
            return selectByNameFromListOrString(prompt, list, entity -> {
                try {
                    return entity.getName();
                } catch (EntityException e) {
                    throw new RuntimeException(e);
                }
            }, allowNone, allowCreate, DO_NOT_ALLOW_CANCEL, DO_NOT_ALLOW_QUIT, DO_NOT_ALLOW_SKIP);
        } catch (CancelException | QuitException | SkipException ignored) {
            // Should not happen since we disabled these options.
        }
        return new EntityOrStringResult<>((T) null);
    }

    /**
     * @inheritDoc
     */
    // File: `src/main/java/com/hixon/financialApp/view/cmdLine/ViewCmdline.java`
    @Override
    public <T extends IndependentEntityInt> EntityOrStringResult<T> selectByNameFromListOrString(
            String prompt, List<T> list, Function<T, String> getDisplayString, boolean allowNone, boolean allowString,
            boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed)
            throws EntityException, CancelException, QuitException, SkipException {

        // If no items available handle create or report none
        if (list.isEmpty()) {
            if (allowString) {
                String newName = getResponseString("Enter name for new item", null, allowNone, false,
                        isCancelAllowed, isQuitAllowed, isSkipAllowed, null);
                return new EntityOrStringResult<>(newName);
            } else {
                say("No items available.");
                return new EntityOrStringResult<>((T) null);
            }
        }

        // Build display names list (may throw EntityException)
        List<String> itemNames = new ArrayList<>();
        for (T item : list) {
            itemNames.add(getDisplayString.apply(item));
        }

        // Delegate to the generic numbered-or-string selector
        NumberOrStringResponse response = selectFromNumberedListOrString(prompt, itemNames, allowNone, allowString,
                isCancelAllowed, isQuitAllowed, isSkipAllowed);

        if (response == null) {
            return new EntityOrStringResult<>((T) null);
        }

        if (response.isNumber()) {
            int idx = response.getSelectedIndex();
            if (idx == -1) {
                return new EntityOrStringResult<>((T) null);
            }
            return new EntityOrStringResult<>(list.get(idx));
        } else {
            // When a string was entered (create), return that string as the result
            return new EntityOrStringResult<>(response.getSearchString());
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
            String dateInput = "";
            try {
                dateInput = getResponseString(prompt, defaultDateStr, ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                        DO_NOT_ALLOW_CANCEL, DO_NOT_ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);
            }
            catch(CancelException | QuitException | SkipException ignored) {
                // We shouldn't get here because we specified no cancel, quit or skip.
            }

            if ((dateInput == null || dateInput.isEmpty()) && defaultDate != null) {
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
} // End class ViewCmdline.
