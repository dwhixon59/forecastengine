package com.hixon.financialApp.view.base;

import com.hixon.financialApp.model.entity.Entity;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.view.text.TextReport;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

public class RegisterReport extends TextReport {

    /*
     * Fields:
     */
    protected Register register = null;

    /*
     * Constructors:
     */

    public RegisterReport(Register register, List<Entity> items, File reportFile) throws FileNotFoundException {
        super(items, reportFile);
        this.register = register;
    }
}
