package com.hixon.financialApp.view.base;

import com.hixon.financialApp.view.ViewException;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.util.Calendar;

public interface RegisterViewInt extends ViewInt {

    public boolean renderTransactionReport(Calendar startDate) throws FileNotFoundException, UnsupportedEncodingException, ViewException;

}
