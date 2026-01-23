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
 * institution.importRegisterTrxFile(); // Reads file from register's trxImportFileName
 * }</pre>
 *
 * <p><strong>Supported Institutions:</strong>
 * <ul>
 *   <li>Wells Fargo Bank - CSV format</li>
 *   <li>Barclays Bank - QFX format</li>
 *   <li>Generic Bank - Manual entry or basic import</li>
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
     *   <li>"Barclays Bank" (or "Barclays") - Creates BarclaysBank for QFX import</li>
     *   <li>"Bank" (or "Generic Bank", "Generic") - Creates GenericBank for manual entry</li>
     * </ul>
     *
     * <p><strong>Note:</strong> After creating the institution, call {@code importRegisterTrxFile()}
     * to import transactions from the file specified in the register's {@code trxImportFileName} field.
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
                new WellsFargoBank(sessionController);

            case "barclays bank", "barclays" ->
                new BarclaysBank(sessionController);

            case "bank", "generic bank", "generic" ->
                new GenericBank(sessionController);

            default ->
                throw new IllegalArgumentException(
                    "Unknown financial institution: '" + institutionName + "'. " +
                    "Supported institutions: 'Wells Fargo Bank', 'Barclays Bank', 'Bank' (generic). " +
                    "Please update the register's financialInstitution field."
                );
        };
    }
}

