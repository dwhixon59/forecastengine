package com.hixon.financialApp.view.text;

import com.hixon.financialApp.model.entity.Entity;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.view.ViewException;
import com.hixon.financialApp.view.base.AbstractRegisterView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.util.Calendar;
import java.util.List;

public class TextRegisterView extends AbstractRegisterView {

    @Override
    public boolean renderTransactionReport(Calendar startDate) throws FileNotFoundException, UnsupportedEncodingException, ViewException {
        return false;
    }

    @Override
    protected NewTransactionSummaryReport getNewTransactionSummaryReport(User user, List<Entity> items, File file) {
        return new NewTransactionSummaryReport(register, user, items, file);
    }

}
