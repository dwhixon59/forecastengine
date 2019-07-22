package com.hixon.financial.controller;

import com.hixon.financial.FinancialException;

public class QuitException extends FinancialException {
    public QuitException(String s) {
        super(s);
    }
}
