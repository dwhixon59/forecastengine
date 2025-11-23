package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.utility.FinancialAppException;
import com.hixon.financialApp.view.base.ViewInt;

/**
 * CSV (Comma-Separated Values) import strategy implementation.
 *
 * <p>This strategy handles importing transactions and budget items from CSV files.
 * It supports custom CSV formats defined by financial institution implementations.</p>
 *
 * <p>CSV is the most common format for bank transaction exports, though each
 * institution may use slightly different column orders and naming conventions.
 * The {@link com.hixon.financialApp.model.financialinstitution.FinancialInstitutionInt}
 * interface handles these variations.</p>
 *
 * <h3>Supported File Types:</h3>
 * <ul>
 *   <li>.csv - Standard comma-separated values</li>
 *   <li>.tsv - Tab-separated values</li>
 *   <li>.txt - Plain text with delimiters</li>
 * </ul>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Handles CSV parsing with Apache Commons CSV</li>
 *   <li>Supports custom headers per financial institution</li>
 *   <li>Creates unique import record IDs to prevent duplicates</li>
 *   <li>Handles both cleared and provisional transactions</li>
 *   <li>Merges provisional transactions with existing ones</li>
 * </ul>
 *
 * @author David Hixon
 * @version 1.0
 * @since 2025-11-23
 */
public class CsvImportStrategy implements ImportStrategy {

    @Override
    public String getStrategyName() {
        return "CSV";
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[]{"csv", "tsv", "txt"};
    }

    @Override
    public boolean canParse(String filename) {
        if (filename == null) {
            return false;
        }
        String lower = filename.toLowerCase();
        for (String ext : getSupportedExtensions()) {
            if (lower.endsWith("." + ext)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean importRegisterTransactions(
            String filename,
            Register register,
            Budget budget,
            Forecast forecast,
            ViewInt view,
            NotificationServiceInt notificationService) throws FinancialAppException {

        try {
            // Delegate to ImportController's existing CSV import logic
            // This will be refactored in a future iteration to move the actual
            // parsing logic here while keeping orchestration in ImportController
            // Note: We pass null for FinancialInstitutionInt since Register doesn't provide it
            // The actual CSV methods will get it from the register's stored data
            ImportController controller = new ImportController(
                    register, null, budget, forecast, view, notificationService);
            return controller.importCsvRegisterTransactionFile(filename);
        } catch (Exception e) {
            throw new FinancialAppException("CSV register transaction import failed", e);
        }
    }

    @Override
    public boolean importProvisionalTransactions(
            String filename,
            Register register,
            Budget budget,
            Forecast forecast,
            ViewInt view,
            NotificationServiceInt notificationService) throws FinancialAppException {

        try {
            // Delegate to ImportController's existing CSV import logic
            ImportController controller = new ImportController(
                    register, null, budget, forecast, view, notificationService);
            return controller.importCsvProvisionalTransactionFile(filename);
        } catch (Exception e) {
            throw new FinancialAppException("CSV provisional transaction import failed", e);
        }
    }

    @Override
    public void importBudgetItems(
            String filename,
            Budget budget,
            ViewInt view) throws FinancialAppException {

        try {
            // Delegate to ImportController's existing CSV import logic
            // Note: Budget items import doesn't need register or forecast
            ImportController controller = new ImportController(
                    null, null, budget, null, view, null);
            controller.importCsvBudgetItemFile(filename);
        } catch (Exception e) {
            throw new FinancialAppException("CSV budget item import failed", e);
        }
    }
}

