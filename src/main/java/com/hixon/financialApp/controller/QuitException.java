package com.hixon.financialApp.controller;

import com.hixon.financialApp.utility.FinancialException;

public class QuitException extends FinancialException {
    public QuitException(String s) {
        super(s);
    }
}
