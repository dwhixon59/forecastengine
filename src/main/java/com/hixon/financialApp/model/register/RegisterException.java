package com.hixon.financialApp.model.register;

import com.hixon.financialApp.utility.FinancialAppException;

public class RegisterException extends FinancialAppException {
    public RegisterException(String s) {
        super(s);
    }

    public RegisterException(String s, Exception e) {
        super(s, e);
    }
}
