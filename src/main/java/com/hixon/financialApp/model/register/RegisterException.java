package com.hixon.financialApp.model.register;

public class RegisterException extends Throwable {
    public RegisterException(String s) {
        super(s);
    }

    public RegisterException(String s, Exception e) {
        super(s, e);
    }
}
