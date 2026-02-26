package com.hixon.financialApp.controller;

/**
 * Enumeration of possible termination conditions for the import process.
 * These values indicate how a particular operation or transaction processing was terminated.
 */
public enum TerminationCondition {
    /**
     * Send an inquiry notification to a user for assistance
     */
    INQUIRE,
    /**
     * Restart processing of the current transaction
     */
    RESTART,
    /**
     * Successfully found and processed the required data
     */
    FOUND,
    /**
     * User cancelled the current operation
     */
    CANCEL,
    /**
     * User chose to skip the current transaction
     */
    SKIP,
    /**
     * User chose to quit the entire import process
     */
    QUIT
}

