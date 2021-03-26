package com.hixon.financialApp.view.text;

import com.hixon.financialApp.model.entity.Entity;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.view.base.AbstractRegisterView;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

public class RegisterView extends AbstractRegisterView {

    public RegisterView(Register register) {
        super(register);
    }

    @Override
    protected NewTransactionSummaryReport getNewTransactionSummaryReport(Register register, List<Entity> items, File reportFile)
            throws FileNotFoundException {
        return new NewTransactionSummaryReport(register, items, reportFile);
    }

}
