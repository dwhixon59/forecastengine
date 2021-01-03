package com.hixon.financialApp.view.base;

import com.hixon.financialApp.model.register.Register;

public abstract class AbstractRegisterReport extends AbstractViewReport {

    /*
     * Fields:
     */
    protected Register register = null;

    /*
     * Constructors:
     */

    public AbstractRegisterReport(Register register) {
        this.register = register;
    }
}
