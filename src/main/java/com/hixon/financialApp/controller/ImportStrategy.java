package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.utility.FinancialAppException;
import com.hixon.financialApp.view.base.ViewInt;

/**
 * Interface for importing financial data from various file formats.
 *
 * <p>This interface defines a strategy pattern for importing transactions and budget items
 * from different file formats (CSV, OFX, QFX, QIF, etc.). Each implementation handles
 * the specifics of parsing its format while the ImportController orchestrates the
 * multi-phase import process.</p>
 *
 * <p>Implementations should focus on:</p>
 * <ul>
 *   <li>File format parsing and validation</li>
 *   <li>Creating Transaction and BudgetItem objects from parsed data</li>
 *   <li>Format-specific error handling</li>
 * </ul>
 *
 * <p>The ImportController handles:</p>
 * <ul>
 *   <li>Merchant identification and assignment</li>
 *   <li>Budget item assignment</li>
 *   <li>Forecast reconciliation</li>
 *   <li>Register balance updates</li>
 *   <li>User interaction for ambiguous cases</li>
 * </ul>
 *
 * @see ImportController
 * @see CsvImportStrategy
 * @see QfxImportStrategy
 * @see OfxImportStrategy
 * @see QifImportStrategy
 *
 * @author David Hixon
 * @version 1.0
 * @since 2025-11-23
 */
public interface ImportStrategy {

    /**
     * Gets the name of this import strategy for display purposes.
     *
     * @return The strategy name (e.g., "CSV", "QFX", "OFX", "QIF")
     */
    String getStrategyName();

    /**
     * Gets the file extensions supported by this strategy.
     *
     * @return Array of file extensions without dots (e.g., ["csv", "tsv"])
     */
    String[] getSupportedExtensions();

    /**
     * Imports cleared (posted) transactions from a file into the register.
     *
     * <p>This method should parse the file format and return control to ImportController
     * for the multi-phase import process (merchant ID, budget assignment, reconciliation).</p>
     *
     * @param filename The full path to the file containing cleared transactions
     * @param register The register to import transactions into
     * @param budget The budget for categorizing transactions
     * @param forecast The forecast for reconciliation
     * @param view The view interface for user interactions
     * @param notificationService Service for sending notifications to users
     * @return true if the forecast is in sync after import, false otherwise
     * @throws FinancialAppException If any error occurs during import
     */
    boolean importRegisterTransactions(
            String filename,
            Register register,
            Budget budget,
            Forecast forecast,
            ViewInt view,
            NotificationServiceInt notificationService) throws FinancialAppException;

    /**
     * Imports provisional (pending) transactions from a file into the register.
     *
     * <p>Provisional transactions are authorized but not yet posted. This method
     * should handle merging with existing provisional transactions and detecting
     * transactions that have been withdrawn.</p>
     *
     * @param filename The full path to the file containing provisional transactions
     * @param register The register to import transactions into
     * @param budget The budget for categorizing transactions
     * @param forecast The forecast for reconciliation
     * @param view The view interface for user interactions
     * @param notificationService Service for sending notifications to users
     * @return true if the forecast is in sync after import, false otherwise
     * @throws FinancialAppException If any error occurs during import
     */
    boolean importProvisionalTransactions(
            String filename,
            Register register,
            Budget budget,
            Forecast forecast,
            ViewInt view,
            NotificationServiceInt notificationService) throws FinancialAppException;

    /**
     * Imports budget items from a file into the budget.
     *
     * <p>This method should parse budget item data and create BudgetItem objects.
     * It should handle both new items and updates to existing items.</p>
     *
     * @param filename The full path to the file containing budget items
     * @param budget The budget to import items into
     * @param view The view interface for user interactions
     * @throws FinancialAppException If any error occurs during import
     */
    void importBudgetItems(
            String filename,
            Budget budget,
            ViewInt view) throws FinancialAppException;

    /**
     * Validates that a file can be parsed by this strategy.
     *
     * <p>This method should perform a quick validation without fully parsing
     * the file. It can check file extension, header format, or magic bytes.</p>
     *
     * @param filename The file to validate
     * @return true if this strategy can parse the file, false otherwise
     */
    boolean canParse(String filename);
}

