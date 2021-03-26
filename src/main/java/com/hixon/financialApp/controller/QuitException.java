package com.hixon.financialApp.controller;

import com.hixon.financialApp.utility.FinancialAppException;

public class QuitException extends FinancialAppException {
    public QuitException(String s) {
        super(s);
    }
}
