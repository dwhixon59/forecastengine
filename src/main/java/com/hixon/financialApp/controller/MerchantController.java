package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.entity.MatchQuery;
import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.model.merchant.MerchantPayee;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.view.base.ViewInt;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hixon.financialApp.view.base.ViewInt.*;

/**
 * Controller for managing merchants and their associated payees.
 * Provides a search-based interface for finding, creating, updating, and deleting merchants.
 */
@Getter
@Setter
public class MerchantController {

    private SessionController sessionController;
    private ViewInt view;
    private NotificationServiceInt notificationService;

    /**
     * Session-scoped cache mapping payee strings to their confirmed merchants.
     * Populated when the user confirms "Use merchant X for payee Y?" or when a new
     * payee association is saved. Prevents repeated prompts for the same payee within
     * a single import session (E8).
     */
    private final Map<String, Merchant> confirmedPayeeCache = new HashMap<>();

    /**
     * Constructor for MerchantController.
     *
     * @param sessionController The session controller for accessing register, budget, and forecast information
     */
    public MerchantController(SessionController sessionController) {
        this.sessionController = sessionController;
        this.view = sessionController.getView();
        this.notificationService = sessionController.getNotificationService();
    }

    /**
     * Assign a merchant to a transaction based on the payee string.
     * This method will:
     * 1. Check if a merchant already exists for the given payee
     * 2. If not, prompt the user to select or create a merchant
     * 3. Associate the payee with the selected/created merchant
     *
     * @param merchantPayeeString The merchant payee string from the transaction
     * @param transactionPayee The transaction payee
     * @param amount The transaction amount
     * @return The assigned Merchant, or null if cancelled/skipped
     * @throws Exception if any error occurs
     */
    public Merchant assignMerchant(String merchantPayeeString, String transactionPayee, double amount)
            throws Exception {
        return assignMerchant(merchantPayeeString, transactionPayee, amount, false);
    }

    /**
     * Assigns or selects a merchant for a transaction.
     * First tries to find an existing merchant by payee string.
     * If found, confirms with user based on requireConfirmation or merchant's askAlways flag.
     * If not found or user declines, prompts user to search/create merchant.
     *
     * This method will:
     * 1. Check if a merchant already exists for the given payee
     * 2. Ask for confirmation if requireConfirmation is true or merchant has askAlways flag
     * 3. If not found or declined, prompt the user to select or create a merchant
     * 4. Associate the payee with the selected/created merchant
     *
     * @param merchantPayeeString The merchant payee string from the transaction
     * @param transactionPayee The transaction payee
     * @param amount The transaction amount
     * @param requireConfirmation If true, always ask for confirmation even if merchant.askAlways is false
     * @return The assigned Merchant, or null if cancelled/skipped
     * @throws Exception if any error occurs
     */
    public Merchant assignMerchant(String merchantPayeeString, String transactionPayee, double amount,
                                    boolean requireConfirmation)
            throws Exception {

        // E8: Check the session cache before hitting the database.
        // If the user already confirmed this payee → merchant mapping in this session,
        // return the cached merchant immediately without asking again.
        if (confirmedPayeeCache.containsKey(merchantPayeeString)) {
            return confirmedPayeeCache.get(merchantPayeeString);
        }

        // First, try to find an existing merchant by the payee string
        Merchant merchant = Merchant.getByPayee(merchantPayeeString);

        if (merchant != null) {
            // Merchant found - check if we should ask before using it.
            // Enhancement 4: Never ask for confirmation when the payee is a transfer payee
            // (e.g., "Transfer to Danni's Spending Account from Bill Pay Danni"). The merchant
            // name is derived directly from the resolved register name, so confirmation is redundant.
            // Also skip when the merchant's name matches a known register (e.g., payee "TO" resolved
            // to "Joint Savings Account" — the merchant IS the register, confirmation adds no value).
            boolean isTransferPayee = merchantPayeeString != null
                    && (merchantPayeeString.toLowerCase().startsWith("transfer to ")
                        || merchantPayeeString.toLowerCase().startsWith("transfer from "));
            if (!isTransferPayee && merchant.getName() != null) {
                try {
                    isTransferPayee = Register.getByName(merchant.getName()) != null;
                } catch (Exception ignored) { /* don't let a DB error break merchant assignment */ }
            }
            boolean shouldAsk = !isTransferPayee && (requireConfirmation || merchant.isAskAlways());

            if (shouldAsk) {
                String confirm = view.getResponseString(
                        "Use merchant '" + merchant.getName() + "' for payee '" + merchantPayeeString + "'? (y/n):",
                        "y", ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                        ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);

                if (confirm.equalsIgnoreCase("y")) {
                    // E8: Cache the confirmed payee→merchant mapping for the rest of this session.
                    confirmedPayeeCache.put(merchantPayeeString, merchant);
                } else {
                    merchant = null;  // User declined, need to select a different merchant
                }
            }
        }

        // If no merchant found or user declined the suggested merchant, prompt for selection/creation
        if (merchant == null) {
            view.say();
            view.say("No merchant found for payee: " + merchantPayeeString);
            view.say("Transaction payee: " + transactionPayee);
            view.say("Amount: $" + String.format("%.2f", amount));

            // Ask user to search for or create a merchant
            merchant = selectMerchant(ALLOW_CREATE);

            if (merchant == null) {
                // User cancelled
                throw new CancelException("User cancelled merchant assignment");
            }

            // Check if this is a newly created or modified merchant
            if (merchant.isDirty()) {
                // This merchant needs to be saved to the database first
                merchant.save();
            }

            // Add the payee to this merchant if not already associated.
            // Use exact match on payee string — a "contains" check would incorrectly treat
            // "ZELLE FROM X ON" as already covering "ZELLE FROM X", preventing the new-format
            // payee from being saved and causing repeated prompts in the same session.
            List<MerchantPayee> existingPayees = merchant.getPayees();
            boolean payeeExists = false;
            for (MerchantPayee existingPayee : existingPayees) {
                if (merchantPayeeString.equals(existingPayee.getPayee())) {
                    payeeExists = true;
                    break;
                }
            }

            if (!payeeExists) {
                // Before creating the new association, delete any existing association with a different merchant
                // The payee field has a UNIQUE constraint, so we must delete the old one first
                MerchantPayee.deleteByName(merchantPayeeString);

                MerchantPayee newPayee = new MerchantPayee(merchantPayeeString, merchant.getId());
                newPayee.save();
                view.say("Associated payee '" + merchantPayeeString + "' with merchant '" + merchant.getName() + "'");
            }

            // E8: Cache this payee→merchant mapping for the rest of this session.
            confirmedPayeeCache.put(merchantPayeeString, merchant);
        }

        return merchant;
    }

    /**
     * Main method for managing merchants interactively.
     * The workflow is:
     * 1. Search for a merchant (or create new)
     * 2. Choose what to do with it (view, update, delete, manage payees)
     *
     * @throws Exception if any error occurs during management operations
     */
    public void manageMerchants() throws Exception {
        boolean done = false;

        while (!done) {
            try {
                // Ask whether to search for existing or create new
                String choice = view.selectFromMenu("What would you like to do?",
                        List.of("search for existing merchant", "create new merchant"),
                        DO_NOT_ALLOW_NONE, SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);

                if (choice.equals("c")) {
                    // User chose to create a new merchant
                    createNewMerchant();
                    continue;
                }

                // User chose to search - proceed with search
                Merchant selectedMerchant = selectMerchant(ALLOW_CREATE);

                if (selectedMerchant == null) {
                    // User cancelled the search
                    continue;
                }

                // Check if this is a newly created merchant (has no ID yet)
                boolean isNewMerchant = selectedMerchant.getId() == null;

                if (isNewMerchant) {
                    // User chose to create a new merchant from search
                    createNewMerchant();
                    continue;
                }

                // User selected an existing merchant - show action menu
                boolean actionComplete = false;
                while (!actionComplete) {
                    // Display the selected merchant
                    view.say();
                    view.say("Selected merchant:");
                    view.say("  " + selectedMerchant.getDisplayString());

                    // Ask what to do with this merchant
                    String action = view.selectFromMenu("What would you like to do with this merchant?",
                            List.of("view details", "update this merchant", "delete this merchant",
                                    "manage payees", "manage budget items", "search again"),
                            DO_NOT_ALLOW_NONE, SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);

                    switch (action) {
                        case "v":  // view details
                            displayMerchantDetails(selectedMerchant);
                            break;

                        case "u":  // update this merchant
                            updateMerchant(selectedMerchant);
                            break;

                        case "d":  // delete this merchant
                            deleteMerchant(selectedMerchant);
                            actionComplete = true;  // After delete, go back to search
                            break;

                        case "m":  // manage payees
                            managePayees(selectedMerchant);
                            break;

                        case "b":  // manage budget items
                            BudgetItemMerchantController bimController =
                                new BudgetItemMerchantController(sessionController, view, notificationService);
                            bimController.manageBudgetItemMerchants(selectedMerchant);
                            break;

                        case "s":  // search again
                            actionComplete = true;
                            break;

                        case "c":  // cancel
                            actionComplete = true;
                            break;

                        default:
                            throw new InvalidEntryException("Unexpected menu option: " + action);
                    }
                }

            } catch (CancelException e) {
                // User cancelled - return to main data manager menu
                done = true;
            } catch (QuitException e) {
                // User wants to quit
                throw e;
            }
        }
    }

    /**
     * Search for and select a merchant using the SelectionController.
     * Public method for use by other controllers.
     *
     * @param allowCreate Whether to allow creating a new merchant if not found
     * @return The selected Merchant, or null if cancelled
     * @throws Exception if any error occurs
     */
    public Merchant selectMerchantPublic(boolean allowCreate) throws Exception {
        return selectMerchant(allowCreate);
    }

    /**
     * Search for and select a merchant using the SelectionController.
     *
     * @param allowCreate Whether to allow creating a new merchant if not found
     * @return The selected Merchant, or null if cancelled
     * @throws Exception if any error occurs
     */
    private Merchant selectMerchant(boolean allowCreate) throws Exception {
        SelectionController selectionController = new SelectionController(view);
        return selectionController.getByNameFullText(
                null,  // No seed name
                null,  // No scope for merchants (they're global)
                DO_NOT_ALLOW_NONE,
                allowCreate,
                ALLOW_CANCEL,
                ALLOW_QUIT,
                DO_NOT_ALLOW_SKIP,
                Merchant.getPrintableTypeName_static(),
                Merchant::getDisplayString,
                new MatchQuery(Merchant.getSelectQuery() + " WHERE ", "m.name", "m.name"),
                rs -> {
                    try {
                        return new Merchant(rs);
                    } catch (RegisterException e) {
                        throw new RuntimeException(e);
                    }
                },
                (scope, newName) -> {
                    Merchant newMerchant = new Merchant(newName);
                    return newMerchant;
                });
    }

    /**
     * Create a new merchant interactively.
     *
     * @throws Exception if any error occurs
     */
    private void createNewMerchant() throws Exception {
        view.say();
        view.say("──── Create New Merchant ────");

        // Get merchant name
        String name = view.getResponseString("Enter merchant name:",
                ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);

        // Check if merchant already exists
        Merchant existingMerchant = Merchant.getByName(name);
        if (existingMerchant != null) {
            view.say("A merchant with that name already exists.");
            return;
        }

        // Ask if should always ask before using this merchant
        String askAlwaysStr = view.getResponseString("Always ask before using this merchant? (y/n):",
                "y", ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);
        boolean askAlways = askAlwaysStr.equalsIgnoreCase("y");

        // Ask if should assign to a user
        String assignUserStr = view.getResponseString("Assign to a user? (y/n):",
                "n", ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);

        User user = null;
        if (assignUserStr.equalsIgnoreCase("y")) {
            user = selectUser();
        }

        // Create the merchant
        Merchant newMerchant = new Merchant(name);
        newMerchant.setAskAlways(askAlways);
        if (user != null) {
            newMerchant.setIdUser(user.getId());
        }

        // Confirm before saving
        view.say();
        view.say("New merchant:");
        view.say("  " + newMerchant.getDisplayString());

        String confirm = view.getResponseString("Save this merchant? (y/n):",
                "y", ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);

        if (confirm.equalsIgnoreCase("y")) {
            newMerchant.save();
            view.say("Merchant successfully created.");
        } else {
            view.say("Merchant not saved.");
        }
    }

    /**
     * Update an existing merchant.
     *
     * @param merchant The merchant to update
     * @throws Exception if any error occurs
     */
    private void updateMerchant(Merchant merchant) throws Exception {
        view.say();
        view.say("──── Update Merchant ────");

        boolean done = false;
        while (!done) {
            // Show current values
            view.say();
            view.say("Current values:");
            view.say("  Name: " + merchant.getName());
            view.say("  Ask Always: " + (merchant.isAskAlways() ? "yes" : "no"));
            try {
                if (merchant.getIdUser() != null) {
                    User user = User.getById(merchant.getIdUser());
                    view.say("  User: " + user.getFirstName() + " " + user.getLastName());
                } else {
                    view.say("  User: none");
                }
            } catch (Exception e) {
                view.say("  User: error retrieving user");
            }

            // Ask what to update
            String choice = view.selectFromMenu("What would you like to update?",
                    List.of("name", "ask always setting", "assigned user", "Save changes"),
                    DO_NOT_ALLOW_NONE, SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);

            switch (choice) {
                case "n":  // name
                    String newName = view.getResponseString("Enter new merchant name:",
                            merchant.getName(), ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                            ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);
                    merchant.setName(newName);
                    break;

                case "a":  // ask always
                    String askAlwaysStr = view.getResponseString("Always ask before using this merchant? (y/n):",
                            merchant.isAskAlways() ? "y" : "n", ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                            ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);
                    merchant.setAskAlways(askAlwaysStr.equalsIgnoreCase("y"));
                    break;

                case "u":  // assigned user
                    String changeUserStr = view.getResponseString("Assign to a user? (y/n, or 'remove' to remove assignment):",
                            ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);
                    if (changeUserStr.equalsIgnoreCase("remove")) {
                        merchant.setIdUser(null);
                    } else if (changeUserStr.equalsIgnoreCase("y")) {
                        User user = selectUser();
                        if (user != null) {
                            merchant.setIdUser(user.getId());
                        }
                    }
                    break;

                case "s":  // Save changes
                    if (merchant.isDirty()) {
                        merchant.save();
                        view.say("Merchant successfully updated.");
                    }
                    done = true;
                    break;

                case "c":  // cancel
                    view.say("Update cancelled.");
                    done = true;
                    break;

                default:
                    throw new InvalidEntryException("Unexpected menu option: " + choice);
            }
        }
    }

    /**
     * Delete a merchant after confirmation.
     *
     * @param merchant The merchant to delete
     * @throws Exception if any error occurs
     */
    private void deleteMerchant(Merchant merchant) throws Exception {
        view.say();
        view.say("You are about to delete:");
        view.say("  " + merchant.getDisplayString());

        view.say();
        view.say("WARNING: Deleting this merchant will also delete all associated payees.");
        view.say("This action cannot be undone.");

        String confirm = view.getResponseString("Are you sure you want to delete this merchant? (yes/no):",
                "no", ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);

        if (confirm.equalsIgnoreCase("yes")) {
            Merchant.deleteByName(merchant.getName());
            view.say("Merchant successfully deleted.");
        } else {
            view.say("Delete cancelled.");
        }
    }

    /**
     * Display detailed information about a merchant.
     *
     * @param merchant The merchant to display
     * @throws Exception if any error occurs
     */
    private void displayMerchantDetails(Merchant merchant) throws Exception {
        view.say();
        view.say("Merchant Details:");
        view.say("──────────────────────────────────────");
        view.say("Name: " + merchant.getName());
        view.say("Ask Always: " + (merchant.isAskAlways() ? "yes" : "no"));

        try {
            if (merchant.getIdUser() != null) {
                User user = User.getById(merchant.getIdUser());
                view.say("User: " + user.getFirstName() + " " + user.getLastName());
            } else {
                view.say("User: none");
            }
        } catch (Exception e) {
            view.say("User: error retrieving user");
        }

        // Display payees
        List<MerchantPayee> payees = merchant.getPayees();
        if (payees.isEmpty()) {
            view.say("Payees: none");
        } else {
            view.say("Payees:");
            for (MerchantPayee payee : payees) {
                view.say("  • " + payee.toString());
            }
        }
        view.say("──────────────────────────────────────");
    }

    /**
     * Manage payees for a merchant (list, add, update, delete).
     *
     * @param merchant The merchant whose payees to manage
     * @throws Exception if any error occurs
     */
    private void managePayees(Merchant merchant) throws Exception {
        boolean done = false;

        while (!done) {
            try {
                view.say();
                view.say("──── Manage Payees for " + merchant.getName() + " ────");

                // Ask whether to search for existing or create new
                String choice = view.selectFromMenu("What would you like to do?",
                        List.of("select existing payee", "add new payee"),
                        DO_NOT_ALLOW_NONE, SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);

                if (choice.equals("a")) {
                    // User chose to add a new payee
                    addPayee(merchant);
                    continue;
                }

                // User chose to select an existing payee
                List<MerchantPayee> payees = merchant.getPayees();

                if (payees.isEmpty()) {
                    view.say("No payees currently associated with this merchant.");
                    view.say("Please add a payee first.");
                    continue;
                }

                // Display numbered list and get selection
                view.say();
                List<String> payeeDisplayList = new ArrayList<>();
                for (MerchantPayee payee : payees) {
                    payeeDisplayList.add(payee.getPayee());
                }

                Integer selectedIndex = view.selectByPositionFromList(
                        "Select a payee:",
                        payeeDisplayList,
                        DO_NOT_ALLOW_NONE,
                        ALLOW_CANCEL,
                        ALLOW_QUIT,
                        DO_NOT_ALLOW_SKIP);

                // Get the selected payee based on the index (already 0-based)
                MerchantPayee selectedPayee = null;
                if (selectedIndex != null && selectedIndex >= 0 && selectedIndex < payees.size()) {
                    selectedPayee = payees.get(selectedIndex);
                }

                if (selectedPayee == null) {
                    view.say("Invalid selection.");
                    continue;
                }

                // Show action menu for the selected payee
                boolean payeeActionComplete = false;
                while (!payeeActionComplete) {
                    view.say();
                    view.say("Selected payee: " + selectedPayee.getPayee());

                    String action = view.selectFromMenu("What would you like to do with this payee?",
                            List.of("update payee name", "delete this payee", "select another payee"),
                            DO_NOT_ALLOW_NONE, SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);

                    switch (action) {
                        case "u":  // update payee name
                            updatePayee(merchant, selectedPayee);
                            payeeActionComplete = true;  // After update, go back to payee selection
                            break;

                        case "d":  // delete this payee
                            deletePayee(merchant, selectedPayee);
                            payeeActionComplete = true;  // After delete, go back to payee selection
                            break;

                        case "s":  // select another payee
                            payeeActionComplete = true;
                            break;

                        case "c":  // cancel
                            payeeActionComplete = true;
                            break;

                        default:
                            throw new InvalidEntryException("Unexpected menu option: " + action);
                    }
                }

            } catch (CancelException e) {
                // User cancelled - return to merchant management
                done = true;
            }
        }
    }

    /**
     * Add a new payee to a merchant.
     *
     * @param merchant The merchant to add the payee to
     * @throws Exception if any error occurs
     */
    private void addPayee(Merchant merchant) throws Exception {
        String payeeName = view.getResponseString("Enter payee name:",
                ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);

        // Check if payee already exists for this merchant
        List<MerchantPayee> existingPayees = merchant.getPayees();
        for (MerchantPayee existingPayee : existingPayees) {
            if (existingPayee.getPayee().equalsIgnoreCase(payeeName)) {
                view.say("A payee with that name already exists for this merchant.");
                return;
            }
        }

        // Create and save the payee
        MerchantPayee newPayee = new MerchantPayee(payeeName, merchant.getId());
        newPayee.save();
        view.say("Payee successfully added.");
    }

    /**
     * Update a payee's name.
     *
     * @param merchant The merchant that owns the payee
     * @param payee The payee to update
     * @throws Exception if any error occurs
     */
    private void updatePayee(Merchant merchant, MerchantPayee payee) throws Exception {
        view.say();
        view.say("Current payee name: " + payee.getPayee());

        String newPayeeName = view.getResponseString("Enter new payee name:",
                payee.getPayee(), ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);

        // Check if the new name is different
        if (newPayeeName.equals(payee.getPayee())) {
            view.say("No changes made.");
            return;
        }

        // Check if a payee with the new name already exists for this merchant
        List<MerchantPayee> existingPayees = merchant.getPayees();
        for (MerchantPayee existingPayee : existingPayees) {
            if (existingPayee.getPayee().equalsIgnoreCase(newPayeeName) &&
                !existingPayee.getPayee().equals(payee.getPayee())) {
                view.say("A payee with that name already exists for this merchant.");
                return;
            }
        }

        // Delete the old payee and create a new one (since payee is part of composite key)
        MerchantPayee.deleteByMerchantAndPayee(merchant, payee.getPayee());
        MerchantPayee newPayee = new MerchantPayee(newPayeeName, merchant.getId());
        newPayee.save();
        view.say("Payee successfully updated.");
    }

    /**
     * Delete a payee from a merchant.
     *
     * @param merchant The merchant to delete the payee from
     * @param payee The payee to delete
     * @throws Exception if any error occurs
     */
    private void deletePayee(Merchant merchant, MerchantPayee payee) throws Exception {
        view.say();
        view.say("You are about to delete payee: " + payee.getPayee());

        String confirm;
        while (true) {
            confirm = view.getResponseString("Are you sure you want to delete this payee? (yes/no):",
                    "no", ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                    ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);

            if (confirm.equalsIgnoreCase("yes") || confirm.equalsIgnoreCase("no")) {
                break;
            }
            view.say("Please enter 'yes' or 'no'.");
        }

        if (confirm.equalsIgnoreCase("yes")) {
            MerchantPayee.deleteByMerchantAndPayee(merchant, payee.getPayee());
            view.say("Payee successfully deleted.");
        } else {
            view.say("Delete cancelled.");
        }
    }


    /**
     * Select a user from all available users.
     *
     * @return The selected User, or null if cancelled
     * @throws Exception if any error occurs
     */
    private User selectUser() throws Exception {
        List<User> allUsers = User.getAllUsers();

        if (allUsers.isEmpty()) {
            view.say("No users found in the database.");
            return null;
        }

        SelectionController selectionController = new SelectionController(view);
        return selectionController.getByNameFullText(
                null,  // No seed name
                null,  // No scope for users (they're global)
                ALLOW_NONE,
                DO_NOT_ALLOW_CREATE,
                ALLOW_CANCEL,
                ALLOW_QUIT,
                DO_NOT_ALLOW_SKIP,
                User.getPrintableTypeName_static(),
                user -> user.getFirstName() + " " + user.getLastName() + " (" + user.getUserName() + ")",
                new MatchQuery(User.getSelectQuery() + " WHERE ", "u.userName",
                        "u.firstName, u.lastName, u.userName"),
                rs -> {
                    try {
                        return new User(rs);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                (scope, newName) -> null);  // Don't allow creating users from this interface
    }
}

