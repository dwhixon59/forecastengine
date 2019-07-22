package com.hixon.financial.view.register;

import com.hixon.financial.controller.Importer;
import com.hixon.financial.model.EntityException;
import com.hixon.financial.model.budget.BudgetException;
import com.hixon.financial.model.budget.BudgetItem;
import com.hixon.financial.model.register.*;
import com.hixon.financial.view.ViewException;

import java.sql.SQLException;
import java.text.ParseException;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TransactionResolverCmdLine implements TransactionResolver {

    private Importer.TerminationCondition terminationCondition;


    @Override
    public Importer.TerminationCondition getTerminationCondition() {
        return terminationCondition;
    }

    @Override
    public BudgetItem resolveUnmatchedBudgetItem(Transaction transaction)
            throws BudgetException, ParseException, SQLException, ViewException {

        try {
            System.out.println("Failed to find a match to transaction " + transaction.getPayee());
            boolean stop = false; boolean found = false;
            BudgetItem budgetItem = new BudgetItem();
            while (!stop) {
                System.out.print("Enter the budget item payee, or 'new' to create a new one: ");
                Scanner in = new Scanner(System.in);
                String line = in.nextLine();
                switch (line) {
                    case "":
                        continue;

                    case "new":
                        // read in a new budget item for this:
                        System.out.println("Enter the budget item in this order: category, payee, period type, amount, " +
                                "start date, number of payments, end date, item type, how paid, search string, budget name:");
                        budgetItem.loadFromCSV(in.nextLine());
                        budgetItem.save();
                        found = true;
                        stop = true;
                        terminationCondition = Importer.TerminationCondition.FOUND;
                        break;

                    case "reset":
                        System.out.println("Nothing to reset at this time.");
                        continue;

                    case "restart":
                        System.out.println("The import process cannot be restarted.");
                        continue;

                    case "quit":
                        stop = true;
                        terminationCondition = Importer.TerminationCondition.QUIT;
                        break;

                    default:
                        found = budgetItem.loadFromPayee(line);
                        if (found) {
                            System.out.println("Old search string: " + budgetItem.getSearchString() + ", Enter the new search string or 'keep' to keep:");
                            line = in.nextLine();
                            if (!line.equalsIgnoreCase("keep")) {
                                Pattern pattern = Pattern.compile(line, Pattern.CASE_INSENSITIVE);
                                Matcher matcher = pattern.matcher(transaction.getPayee());
                                if (!matcher.find()) {
                                    System.out.println("New pattern doesn't work on this payee.  Use anyway?");
                                    line = in.nextLine();
                                    if (line.equalsIgnoreCase("n") || line.equalsIgnoreCase("no")) {
                                        found = false;
                                        stop = false;
                                        continue;
                                    }
                                }
                                budgetItem.setSearchString(line);
                                budgetItem.update();
                            }
                            stop = true;
                            terminationCondition = Importer.TerminationCondition.FOUND;
                            break;
                        } else {
                            System.out.println("Budget item not found.  Try again.");
                            stop = false;
                        }
                }
            }
            return (found) ? budgetItem : null;

        } catch (Exception e) {
            ViewException ve = new ViewException("Exception occured trying to import this transaction: " +
                    transaction + ".");
            ve.initCause(e);
            throw ve;
        }
    }

    // Find or create a merchant for a transaction:
    @Override
    public Merchant resolveUnmatchedMerchant(String merchantPayeeString) throws ViewException, RegisterException, EntityException {

        try {
            System.out.println("Failed to find a merchant for payee " + merchantPayeeString);
            boolean stop = false; boolean found = false;
            Merchant merchant = null;
            MerchantPayee merchantPayee = null;
            while (!stop) {
                System.out.print("Enter the merchant name, or 'new' to create a new one: ");
                Scanner in = new Scanner(System.in);
                String line = in.nextLine();
                switch (line) {
                    case "":
                        continue;

                    case "new":
                        // Read in the data for a new merchant from the user:
                        System.out.print("Enter the new merchant's name: ");
                        String merchantName = in.nextLine();
                        if (Merchant.getByName(merchantName) == null) {
                            merchant = Merchant.loadFromCSV(merchantName);
                            merchant.addPayee(merchantPayeeString);
                            merchant.save();
                            found = true;
                            stop = true;
                            terminationCondition = Importer.TerminationCondition.FOUND;
                        }
                        else {
                            System.out.println("Merchant already exists.  Choose a different name.");
                            found = false;
                            stop = false;                        }
                        break;

                    case "reset":
                        System.out.println("Nothing to reset at this time.");
                        continue;

                    case "restart":
                        System.out.println("The import process cannot be restarted.");
                        continue;

                    case "quit":
                        stop = true;
                        terminationCondition = Importer.TerminationCondition.QUIT;
                        break;

                    default:
                        merchant = merchant.getByNameLike(line);
                        if (merchant != null) {
                            merchantPayee = merchant.addPayee(merchantPayeeString);
                            merchantPayee.save();
                            stop = true;
                            terminationCondition = Importer.TerminationCondition.FOUND;
                            break;
                        } else {
                            System.out.println("Merchant not found.  Try again.");
                            stop = false;
                        }
                }
            }
            return merchant;

        } catch (Exception e) {
            ViewException ve = new ViewException("Exception occured trying to a merchant for this transaction: " +
                    merchantPayeeString + ".");
            ve.initCause(e);
            throw ve;
        }
    }

    // The account number was not in the payee string, so ask the user for help:
    @Override
    public String resolveUnmatchedAccount(String payee) throws RegisterException {
        String accountNumber = null;
        System.out.println("There is no account number was in the following transaction payee: " + payee + ".");
        System.out.println("Enter the last four digits of the account number to assign this transaction to:  ");
        List<Register> registers = Register.getListOf();
        for (Register register: registers
             ) {
            System.out.println(register.getRegisterName() + ", " + register.getAccountType() + ", " +
                    register.getAccountNumber());
        }
        Scanner in = new Scanner(System.in);
        String lastFourDigits = in.nextLine();
        boolean stop = false;
        while (!stop) {
            for (Register register : registers
            ) {
                if (lastFourDigits.equalsIgnoreCase(register.getAccountNumber().substring(
                        register.getAccountNumber().length() - 4))) {
                    accountNumber = register.getAccountNumber();
                    stop = true;
                }
            }
            if (!stop) {
                System.out.print("Not in the list.  Re-enter the account number:");
                in.nextLine();
            }
        }
        return accountNumber;
    }
}
