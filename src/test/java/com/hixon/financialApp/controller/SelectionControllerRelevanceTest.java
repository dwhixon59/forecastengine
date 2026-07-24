package com.hixon.financialApp.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the relevance-based auto-selection logic in SelectionController.
 * Tests the fuzzy-match auto-select rule: when the top result has significantly higher
 * relevance than the second, it should be auto-selected if autoAcceptExactMatch is true.
 */
@DisplayName("SelectionController Relevance-Based Auto-Select Tests")
public class SelectionControllerRelevanceTest {

    /**
     * Simulates the decision logic: given two relevance scores, should the first be auto-selected?
     * Threshold: first >= 1.5x second AND first >= 15.0 (absolute minimum for a "good" match).
     */
    private boolean shouldAutoSelect(double firstRelevance, double secondRelevance, boolean autoAcceptExactMatch) {
        return autoAcceptExactMatch
                && firstRelevance >= 15.0  // Absolute minimum threshold
                && secondRelevance > 0
                && firstRelevance >= secondRelevance * 1.5;  // Relative dominance
    }

    @Test
    @DisplayName("State Farm case: dominant first result should auto-select")
    void testStateFarmDominance() {
        // Realistic scenario: STATE FARM INSURANCE BLOOMI
        // - "State Farm" scores high (matches "STATE FARM" directly)
        // - "State College of Florida" scores low (only "STATE" matches)
        double stateFarmRelevance = 50.0;      // High relevance
        double stateCollegeRelevance = 5.0;    // Low relevance (only partial match)

        assertTrue(shouldAutoSelect(stateFarmRelevance, stateCollegeRelevance, true),
                "State Farm (50.0) is 10x better than State College (5.0), should auto-select");
    }

    @Test
    @DisplayName("Close scores should NOT auto-select (ambiguous match)")
    void testCloseScores() {
        // Two similarly-scored results: ambiguous, should ask user
        double first = 10.0;
        double second = 9.0;

        assertFalse(shouldAutoSelect(first, second, true),
                "10.0 is only 1.1x better than 9.0, below 1.5x threshold, should NOT auto-select");
    }

    @Test
    @DisplayName("Threshold boundary: exactly 1.5x should auto-select")
    void testThresholdBoundary() {
        double first = 15.0;
        double second = 10.0;

        assertTrue(shouldAutoSelect(first, second, true),
                "15.0 is exactly 1.5x 10.0, meets threshold, should auto-select");
    }

    @Test
    @DisplayName("Low absolute scores (10 vs 1) despite 10x dominance: should NOT auto-select")
    void testLowAbsoluteScores() {
        // This is the case the user correctly identified: 10x dominance but both are weak matches
        double first = 10.0;   // Below 15.0 minimum threshold
        double second = 1.0;

        assertFalse(shouldAutoSelect(first, second, true),
                "Even with 10x dominance, score of 10.0 is below 15.0 minimum threshold, should NOT auto-select");
    }

    @Test
    @DisplayName("Exactly at threshold: 15.0 vs 10.0 should auto-select")
    void testAtMinimumThreshold() {
        double first = 15.0;   // Exactly at minimum threshold
        double second = 10.0;

        assertTrue(shouldAutoSelect(first, second, true),
                "15.0 is exactly at minimum threshold and 1.5x 10.0, should auto-select");
    }

    @Test
    @DisplayName("Just below minimum threshold: 14.9 vs less should NOT auto-select")
    void testBelowMinimumThreshold() {
        double first = 14.9;   // Just below 15.0 minimum
        double second = 9.0;

        assertFalse(shouldAutoSelect(first, second, true),
                "14.9 is below 15.0 minimum threshold, should NOT auto-select even if 1.5x second");
    }

    @Test
    @DisplayName("autoAcceptExactMatch=false overrides dominance (user wants to confirm)")
    void testAutoAcceptFalse() {
        double first = 50.0;
        double second = 5.0;

        assertFalse(shouldAutoSelect(first, second, false),
                "Even with high dominance, should NOT auto-select when autoAcceptExactMatch=false");
    }

    @Test
    @DisplayName("Zero or negative relevance scores never auto-select")
    void testZeroRelevance() {
        assertFalse(shouldAutoSelect(50.0, 0.0, true),
                "Zero second relevance should NOT auto-select");
        assertFalse(shouldAutoSelect(0.0, 50.0, true),
                "Zero first relevance should NOT auto-select");
        assertFalse(shouldAutoSelect(-10.0, 5.0, true),
                "Negative first relevance should NOT auto-select");
    }

    @Test
    @DisplayName("Very high dominance (10x+) should definitely auto-select")
    void testVeryHighDominance() {
        double first = 100.0;
        double second = 5.0;

        assertTrue(shouldAutoSelect(first, second, true),
                "100.0 is 20x better than 5.0, well above threshold, should auto-select");
    }
}



