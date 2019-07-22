package com.hixon.financial.view.register;

import com.hixon.financial.view.ViewException;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.util.Calendar;

public interface RegisterView {
    public boolean renderFrom(Calendar startDate) throws FileNotFoundException, UnsupportedEncodingException, ViewException;
}
