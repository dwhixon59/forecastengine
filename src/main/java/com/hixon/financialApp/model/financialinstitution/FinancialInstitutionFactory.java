package com.hixon.financialApp.model.financialinstitution;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.view.base.ViewInt;

/**
 * Factory for creating FinancialInstitution instances based on the register's institution type.
 *
 * <p>This factory centralizes the creation of financial institution objects, determining
 * the appropriate implementation based on the register's financial institution type.
 *
 * <p><strong>Usage:</strong>
 * <pre>{@code
 * FinancialInstitutionInt institution = FinancialInstitutionFactory.create(
 *     register, budget, forecast, view, notificationService
 * );
 * }</pre>
 *
 * <p><strong>Supported Institutions:</strong>
 * <ul>
 *   <li>Wells Fargo Bank - CSV format</li>
 *   <li>Barclays Bank - QFX format (when register type matches)</li>
 * </ul>
 *
 * @see FinancialInstitutionInt
 * @see WellsFargoBank
 * @see BarclaysBank
 */
public class FinancialInstitutionFactory {

    /**
     * Creates a FinancialInstitution instance based on the register's institution type.
     *
     * <p>The factory inspects the register to determine which financial institution
     * implementation to create. For now, it defaults to Wells Fargo Bank.
     *
     * <p><strong>Future Enhancement:</strong> The Register class should have a field
     * indicating the institution type (e.g., "WELLS_FARGO", "BARCLAYS", "CHASE").
     *
     * @param register the register for this financial institution
     * @param budget the budget for transaction categorization
     * @param forecast the forecast for planning
     * @param view the view interface for user interaction
     * @param notificationService the notification service
     * @return a FinancialInstitutionInt implementation appropriate for the register
     * @throws Exception if the institution cannot be created
     * @throws IllegalArgumentException if register is null
     */
    public static FinancialInstitutionInt create(
            Register register,
            Budget budget,
            Forecast forecast,
            ViewInt view,
            NotificationServiceInt notificationService) throws Exception {

        if (register == null) {
            throw new IllegalArgumentException("Register cannot be null");
        }

        // TODO: Get institution type from register
        // For now, determine by register name or default to Wells Fargo
        String registerName = register.getName().toLowerCase();

        // Check if this is a Barclays account
        if (registerName.contains("barclays") || registerName.contains("aviator")) {
            // For Barclays, we need a QFX filename
            // This would typically come from the register or be prompted from the user
            // For now, throw an exception indicating QFX file needed
            throw new UnsupportedOperationException(
                "Barclays Bank requires QFX file import. " +
                "Please use BarclaysBank constructor directly with QFX filename."
            );
        }

        // Default to Wells Fargo for all other registers
        return new WellsFargoBank(register, budget, forecast, view, notificationService);
    }

    /**
     * Creates a FinancialInstitution instance for a specific institution type.
     *
     * @param institutionType the type of institution ("WELLS_FARGO", "BARCLAYS", etc.)
     * @param register the register for this financial institution
     * @param budget the budget for transaction categorization
     * @param forecast the forecast for planning
     * @param view the view interface for user interaction
     * @param notificationService the notification service
     * @return a FinancialInstitutionInt implementation
     * @throws Exception if the institution cannot be created
     * @throws IllegalArgumentException if institution type is unknown
     */
    public static FinancialInstitutionInt create(
            String institutionType,
            Register register,
            Budget budget,
            Forecast forecast,
            ViewInt view,
            NotificationServiceInt notificationService) throws Exception {

        if (institutionType == null || institutionType.trim().isEmpty()) {
            throw new IllegalArgumentException("Institution type cannot be null or empty");
        }

        return switch (institutionType.toUpperCase()) {
            case "WELLS_FARGO", "WELLSFARGO" ->
                new WellsFargoBank(register, budget, forecast, view, notificationService);

            default ->
                throw new IllegalArgumentException(
                    "Unknown institution type: " + institutionType +
                    ". Supported types: WELLS_FARGO"
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

