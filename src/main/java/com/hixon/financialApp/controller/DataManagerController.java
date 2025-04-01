package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.view.base.ViewInt;

import java.sql.SQLException;
import java.text.ParseException;

/*
 * This class enables the user to manage budget items, e.g. add, update, delete, etc.
 */
public class DataManagerController {

    /*
     * Fields of the DataManagerController:
     */
    private Register register;
    private Budget budget;
    private Forecast forecast;
    private ViewInt view;
    private NotificationServiceInt notificationService;
    private BudgetController budgetController;
    
    
    /*
     * Constructors and destructor for the data manager controller:
     */
    /**
     * Create a data manager controller:
     *
     * @param register
     * @param budget
     * @param forecast
     * @param view
     * @param notificationService
     * @return
     */
    DataManagerController(Register register, Budget budget, Forecast forecast, ViewInt view, 
                          NotificationServiceInt notificationService) {
        this.register = register;
        this.budget = budget;
        this.forecast = forecast;
        this.view = view;
        this.notificationService = notificationService;
    }
    
    
    /*
     * Main methods of the data manager controller:
     */
    /**
     * This routine enables the user to select what they want to do wrt managing budget items.
     * 
     * @return
     * @throws BudgetException
     * @throws Exception
     * @throws EntityException
     * @throws RegisterException
     * @throws QuitException
     */
    boolean manageBudgetItems() throws BudgetException, Exception, EntityException,
            RegisterException, QuitException {

        // Setup subsidiary controllers:
        budgetController = new BudgetController(register, budget, forecast, view, notificationService);

        /*
        // Create a list of entities that the user can manage:
        List<String> entities = new ArrayList<>();
        entities.add("Budget Items");
        entities.add("Envelope Items");

        // Show the user a list of entities that they can manage:
        view.selectFromNumberedList();
        String prompt = "What category of data would you like to manage (a-add, d-delete, u-update, q-quit)?";
        String option = view.selectFromFirstLetterList(prompt, "a,d,u,q");
        */

        // Confirm for the user what we are up to:
        view.say("MANAGE BUDGET ITEMS:  ");

        // Until the user tells us that they are finished:
        boolean done = false;
        while (!done) {

            // Find out what the user wants to do:
            view.say();
            String prompt = "What would you like to do (a-add, d-delete, u-update, q-quit)?";
            String option = view.selectFromFirstLetterList(prompt, "a,d,u,q");

            // Invoke a function to execute the user's request:
            switch(option) {
                case "a":
                    // Add a new budget item:
                    addBudgetItem();
                    break;

                case "d":
                    // Delete the budget item:
                    deleteBudgetItem();
                    break;

                case "u":
                    // Update the budget item:
                    updateBudgetItem();
                    break;

                case "q":
                    done = true;
                    break;

                default:
                    throw new InvalidEntryException("selectFromFirstLetterList returned an option that wasn't in the " +
                            "option list.");
            }
        }

        return true;
    }

    /*
     * This routine enables the user to add a new budget item:
     */
    boolean addBudgetItem() throws BudgetException, SQLException, EntityException, RegisterException, ForecastException,
            ParseException, InvalidEntryException {

        // Get the new budget item from the user:
        BudgetItem budgetItem = budgetController.getBudgetItemFromUser();

        // If the budget item is valid:
        if (budgetItem.isValid()) {

            // Insert the new budget item:
            budgetItem.save(EntityInt.SaveMethod.INSERT);

            // The forecast is now out of sync, so let the caller know
            return false;

        } else {
            throw new InvalidEntryException("Budget item entered by user is invalid.");
        }
    }

    /*
     * This routine enables the user to update a budget item:
     */
    private boolean updateBudgetItem() throws BudgetException, SQLException, EntityException, ParseException,
            InvalidEntryException {

        // Get the budget item from the user:
        BudgetItem budgetItem = budgetController.getBudgetItemFromUser();

        // If the budget item is valid:
        if (budgetItem.isValid()) {

            // Update the budget item:
            budgetItem.update();

            // The forecast is now out of sync, so let the caller know
            return false;

        } else {
            throw new InvalidEntryException("Budget item entered by user is invalid.");
        }
    }

    /*
     * This routine enables the user to delete a budget item:
     */
    private boolean deleteBudgetItem() throws BudgetException, SQLException, EntityException, ParseException,
            RegisterException, InvalidEntryException {

        // Get the budget item from the user:
        BudgetItem budgetItem = budgetController.getBudgetItemFromUser();

        // If the budget item is valid:
        if (budgetItem.isValid()) {

            // Delete the budget item:
            budgetItem.delete();

            // The forecast is now out of sync, so let the caller know
            return false;

        } else {
            throw new InvalidEntryException("Budget item entered by user is invalid.");
        }
    }
}
