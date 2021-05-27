package com.hixon.financialApp.controller;

/**
 * This exception is thrown when the user requests to skip the item currently being processed.
 */
public class SkipException extends Exception {
    public SkipException(String s) {
        super(s);
    }
}
