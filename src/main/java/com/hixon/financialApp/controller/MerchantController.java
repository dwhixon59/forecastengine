package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.entity.IndependentEntity;
import com.hixon.financialApp.model.entity.MatchQuery;
import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.model.merchant.MerchantPayee;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.view.ViewException;
import com.hixon.financialApp.view.base.ViewInt;

import java.sql.SQLException;

import static com.hixon.financialApp.controller.ImportController.TerminationCondition.FOUND;
import static com.hixon.financialApp.controller.ImportController.TerminationCondition.QUIT;
import static com.hixon.financialApp.utility.Utility.formatDollarAmount;

public class MerchantController {

    /**
     * Member variables for MerchantResolverCmdLine:
     */
    private ImportController.TerminationCondition terminationCondition;
    private final ViewInt view;
    private final NotificationServiceInt notificationService;


    /*
     * Getters and setters for BudgetController:
     */
    public ImportController.TerminationCondition getTerminationCondition() {
        return terminationCondition;
    }


    /**
     * Constructors for MerchantResolverCmdLine:
     */
    public MerchantController(ViewInt view, NotificationServiceInt notificationService) {

        this.view = view;
        this.notificationService = notificationService;
        terminationCondition = QUIT;
    }


    /**
     * Main methods for MerchantController:
     */
    /**
     * {@inheritDoc}
     */
    public Merchant assignMerchant(String merchantPayeeString, String transactionPayeeString, double transactionAmount)
            throws ViewException, EntityException, RegisterException, SQLException, CancelException, QuitException,
            SkipException {

        Merchant merchant;
        MerchantPayee merchantPayee;
        try {
            // Tell the user we couldn't match a merchant to the payee extracted from the transaction payee:
            view.say("\nFailed to find a merchant for payee \"" + merchantPayeeString +
                    "\" derived from transaction payee:  \n\t" + transactionPayeeString + " for the amount of " +
                    formatDollarAmount(transactionAmount));

            // Do a full text lookup on the merchant payee string to see if anything loosely matches:
            SelectionController selectionController = new SelectionController(view);
            merchant = selectionController.getByNameFullText(
                    merchantPayeeString, // the name to search for
                    null, // there is no scope, search all merchants
                    ViewInt.DO_NOT_ALLOW_NONE, // whether or not "none" is an allowed response
                    ViewInt.ALLOW_CREATE, // whether or not the user can create a new merchant
                    ViewInt.DO_NOT_ALLOW_CANCEL, // is canceling the operation allowed
                    ViewInt.ALLOW_QUIT, // is quitting the operation allowed
                    ViewInt.ALLOW_SKIP, // is skipping the operation allowed
                    Merchant.getType(),
                    Merchant::toString,
                    new MatchQuery(Merchant.getSelectQuery() + " WHERE ", "m.name", "m.name"), // Entity creator from ResultSet
                    rs -> {
                        try {
                            return new Merchant(rs);
                        } catch (RegisterException e) {
                            throw new RuntimeException(e);
                        }
                    }, // Entity creator for a new entity
                    (IndependentEntity scope, String newName) -> new Merchant(newName));

            // If the user created a new merchant:
            if (merchant.isDirty()) {

                // then fill out the fields for the new merchant:
                merchant.setAskAlways(view.getYesOrNo(
                        "Do you always want to approve budget allocations for this merchant?"));
                User user = view.getUser("Which user do you want to associate with this merchant?",
                        User.getAllUsers(), true);
                if (user != null) {
                    merchant.setIdUser(user.getId());
                }
                merchant.save();
            }

            // Add the payee string to the Merchant unless it's a check, because checks don't have payees:
            if (!merchantPayeeString.equalsIgnoreCase("Check")) {

                //  then if the user wants to add the payee to the list of payees for the merchant:
                if (view.getYesOrNo("Do you want to add the payee \"" + merchantPayeeString +
                        "\" to the list of payees for the merchant " + merchant.getName() + "?")) {

                    // then add the payee to the merchant:
                    merchantPayee = merchant.addPayee(merchantPayeeString);
                    merchantPayee.save();
                }
            }

            // Set the termination condition to FOUND and return the merchant:
            terminationCondition = FOUND;
            return merchant;

        }
        catch (CancelException | QuitException e) {
            throw e;
        }
        catch (SkipException se) {

            // Assign the merchant payee to the UNKNOWN merchant for now:
            merchant = Merchant.getByName(Merchant.UNKNOWN);
            merchantPayee = merchant.addPayee(merchantPayeeString);
            merchantPayee.save(EntityInt.SaveMethod.INSERT_ON_DUPLICATE_SKIP);
            throw se;

        }
        catch (Exception e) {
            ViewException ve = new ViewException("Exception occurred trying to assign a merchant for this transaction: " +
                    merchantPayeeString + ".", e);
            throw ve;
        }
    }
}
