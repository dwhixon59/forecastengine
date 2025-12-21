package com.hixon.financialApp.model.financialinstitution;

import com.hixon.financialApp.controller.SessionController;
import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.view.base.ViewInt;

/**
 * Factory for creating FinancialInstitution instances based on the register's institution type.
 *
 * <p>This factory centralizes the creation of financial institution objects, determining
 * the appropriate implementation based on the register's {@code financialInstitution} field.
 *
 * <p><strong>Usage:</strong>
 * <pre>{@code
 * FinancialInstitutionInt institution = FinancialInstitutionFactory.create(sessionController);
 * }</pre>
 *
 * <p><strong>Supported Institutions:</strong>
 * <ul>
 *   <li>Wells Fargo Bank - CSV format (auto-created from register field)</li>
 *   <li>Barclays Bank - QFX format (requires explicit createBarclays() call with filename)</li>
 *   <li>Generic Bank - Manual entry or basic import (auto-created from register field)</li>
 * </ul>
 *
 * @see FinancialInstitutionInt
 * @see WellsFargoBank
 * @see BarclaysBank
 * @see GenericBank
 */
public class FinancialInstitutionFactory {

    /**
     * Creates a FinancialInstitution instance based on the register's institution type.
     * Uses the SessionController to extract all necessary dependencies.
     *
     * <p>The factory reads the {@code financialInstitution} field from the register
     * to determine which implementation to create.
     *
     * <p><strong>Supported Institution Names:</strong>
     * <ul>
     *   <li>"Wells Fargo Bank" (or "WellsFargo", "Wells Fargo") - Creates WellsFargoBank for CSV import</li>
     *   <li>"Barclays Bank" (or "Barclays") - Throws exception, use createBarclays() with QFX file</li>
     *   <li>"Bank" (or "Generic Bank", "Generic") - Creates GenericBank for manual entry</li>
     * </ul>
     *
     * @param sessionController the session controller containing register, budget, forecast, view, and notificationService
     * @return a FinancialInstitutionInt implementation appropriate for the register
     * @throws Exception if the institution cannot be created
     * @throws IllegalArgumentException if sessionController is null, register is null, or institution name is unknown
     */
    public static FinancialInstitutionInt create(SessionController sessionController) throws Exception {

        if (sessionController == null) {
            throw new IllegalArgumentException("SessionController cannot be null");
        }

        Register register = sessionController.getRegister();
        if (register == null) {
            throw new IllegalArgumentException("Register cannot be null");
        }

        // Extract dependencies from SessionController
        Budget budget = sessionController.getBudget();
        Forecast forecast = sessionController.getForecast();
        ViewInt view = sessionController.getView();
        NotificationServiceInt notificationService = sessionController.getNotificationService();

        // Get institution type from register
        String institutionName = register.getFinancialInstitution();

        if (institutionName == null || institutionName.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "Register does not have a financial institution specified. " +
                "Please set the financialInstitution field on the register."
            );
        }

        // Create the appropriate financial institution based on the name
        return switch (institutionName.toLowerCase().trim()) {
            case "wells fargo bank", "wellsfargo", "wells fargo" ->
                new WellsFargoBank(register, budget, forecast, view, notificationService);

            case "barclays bank", "barclays" ->
                throw new UnsupportedOperationException(
                    "Barclays Bank requires QFX file import. " +
                    "Use FinancialInstitutionFactory.createBarclays() with QFX filename."
                );

            case "bank", "generic bank", "generic" ->
                new GenericBank(register, budget, forecast, view, notificationService);

            default ->
                throw new IllegalArgumentException(
                    "Unknown financial institution: '" + institutionName + "'. " +
                    "Supported institutions: 'Wells Fargo Bank', 'Barclays Bank', 'Bank' (generic). " +
                    "Please update the register's financialInstitution field."
                );
        };
    }

    /**
     * Creates a Barclays Bank institution with a QFX filename.
     *
     * @param qfxFilename the QFX file to import
     * @param register the register for this financial institution
     * @param budget the budget for transaction categorization
     * @param forecast the forecast for planning
     * @param view the view interface for user interaction
     * @param notificationService the notification service
     * @return a BarclaysBank instance
     * @throws Exception if the institution cannot be created
     */
    public static FinancialInstitutionInt createBarclays(
            String qfxFilename,
            Register register,
            Budget budget,
            Forecast forecast,
            ViewInt view,
            NotificationServiceInt notificationService) throws Exception {

        return new BarclaysBank(qfxFilename, register, budget, forecast, view, notificationService);
    }
}

