package com.hixon.financialApp.test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test to verify category pattern handles spaces correctly
 */
public class TestCategoryPattern {

    public static void main(String[] args) {
        // The pattern from BudgetSearchQualifierProcessor
        Pattern CATEGORY_PATTERN = Pattern.compile("\\bcategory:([^\\n]+?)(?=\\s+(?:budget:|category:|n:|b:|e:|l:|s:)|$)");

        System.out.println("===== Testing Category Pattern with Spaces =====\n");

        // Test cases
        String[] testCases = {
            "category:Food and Beverage groceries",
            "groceries category:Food and Beverage",
            "category:Food and Beverage budget:all",
            "budget:all category:Food and Beverage groceries",
            "n:payment category:Food and Beverage budget:all",
            "category:Automotive",
            "category:Food and Beverage",
            "category:Housing n:mortgage"
        };

        for (String testCase : testCases) {
            System.out.println("Input: \"" + testCase + "\"");

            Matcher matcher = CATEGORY_PATTERN.matcher(testCase);
            if (matcher.find()) {
                String captured = matcher.group(1).trim();
                System.out.println("  ✓ Captured: \"" + captured + "\"");

                // Show what's left after removal
                String remaining = matcher.replaceAll("").trim().replaceAll("\\s+", " ");
                System.out.println("  → Remaining: \"" + remaining + "\"");
            } else {
                System.out.println("  ✗ No match");
            }
            System.out.println();
        }

        System.out.println("===== Pattern Explanation =====");
        System.out.println("\\bcategory:           - Word boundary + literal 'category:'");
        System.out.println("([^\\n]+?)            - Capture group: any char except newline (non-greedy)");
        System.out.println("(?=                  - Positive lookahead (doesn't consume):");
        System.out.println("  \\s+(?:             - One or more spaces followed by:");
        System.out.println("    budget:|         - 'budget:' OR");
        System.out.println("    category:|       - 'category:' OR");
        System.out.println("    n:|b:|e:|l:|s:   - Any search mode prefix");
        System.out.println("  )|$)               - OR end of string");
        System.out.println();
        System.out.println("This stops capturing when it sees a space before another qualifier,");
        System.out.println("allowing category names with spaces like 'Food and Beverage'.");
    }
}

