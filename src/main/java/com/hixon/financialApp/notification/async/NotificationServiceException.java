package com.hixon.financialApp.notification.async;

import com.hixon.financialApp.utility.FinancialAppException;

public class NotificationServiceException extends FinancialAppException {
    public NotificationServiceException(String errorMessage) {
        super(errorMessage);
    }
}
