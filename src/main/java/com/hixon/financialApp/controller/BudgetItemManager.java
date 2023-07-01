package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.utility.Utility;

import java.sql.SQLException;
import java.text.ParseException;

/*
 * This class enables the user to manage budget items, e.g. add, update, delete, etc.
 */
public class BudgetItemManager {

    /*
     * This routine enables the user to select what they want to do wrt managing budget items:
     */
    boolean manageBudgetItems(Forecast forecast) throws BudgetException, Exception, EntityException,
            RegisterException, QuitException {

        // Confirm for the user what we are up to:
        Utility.getResolver().say("MANAGE BUDGET ITEMS:  ");


        // Until the user tells us that they are finished:
        boolean done = false;
        while (!done) {

            // Find out what the user wants to do:
            Utility.getResolver().say();
            String prompt = "What would you like to do (a-add, d-delete, u-update, q-quit)?";
            String option = Utility.getResolver().selectFromFirstLetterList(prompt, "a,d,u,q");

            // Invoke a function to execute the user's request:
            switch(option) {
                case "a":
                    addBudgetItem();
                    break;

                case "d":
                    Utility.getResolver().say("Delete option not implemented yet.  Choose a different one.");
                    break;

                case "u":
                    Utility.getResolver().say("Update option not implemented yet.  Choose a different one.");
                    break;

                case "q":
                    done = true;
                    break;

                default:
                    throw new InvalidEntryException("selectFromFirstLetterList returned an option that wasn't in the " +
                            "option list.");
            }
        }

        if (Utility.getResolver().getYesOrNo("Would you like to update the forecast?")){
            forecast.updateForecast();
        };

        return true;
    }

    /*
     * This routine enables the user to add a new budget item:
     */
    boolean addBudgetItem() throws BudgetException, SQLException, EntityException, RegisterException, ForecastException,
            ParseException, InvalidEntryException {

        // Get the new budget item from the user:
        BudgetItem budgetItem = Utility.getResolver().getBudgetItemFromUser();

        // If the budget item is valid:
        if (budgetItem.isValid()) {

            // Insert the new budget item:
            budgetItem.save(EntityInt.SaveMethod.INSERT);
            return true;

        } else {
            throw new InvalidEntryException("Budget item entered by user is invalid.");
        }
    }

}
