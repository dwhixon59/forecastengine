package com.hixon.financial.view.register;

import com.hixon.financial.Utility;
import com.hixon.financial.controller.Importer;
import com.hixon.financial.model.EntityException;
import com.hixon.financial.model.budget.BudgetException;
import com.hixon.financial.model.budget.BudgetItem;
import com.hixon.financial.model.budget.BudgetItemMerchant;
import com.hixon.financial.model.register.*;
import com.hixon.financial.view.ViewException;

import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class TransactionResolverCmdLine implements TransactionResolver {

   /*
    * Fields for TransactionResolverCmdLine:
    */
   private Importer.TerminationCondition terminationCondition;


   /*
    * Getters and setters for TransactionResolverCmdLine:
    */
   @Override
   public Importer.TerminationCondition getTerminationCondition() {
      return terminationCondition;
   }


   /*
    * Constructors for TransactionResolverCmdLine:
    */


   /*
    * Helper methods for TransactionResolverCmdLine:
    */
   private boolean getYesOrNo(String question) {
      Scanner in = new Scanner(System.in);
      System.out.print(question + " (y/n): ");
      while (true) {
         String line = in.nextLine();
         if (line.equalsIgnoreCase("y")) return true;
         if (line.equalsIgnoreCase("n")) return false;
         System.out.print("\nPlease enter 'y' or 'n': ");
      }
   }

   protected double parseDouble(String doubleString, String errorMessage) {
      double doubleValue = 0;
      boolean done = false;
      Scanner in = new Scanner(System.in);
      while (true) {
         try {
            if (doubleString.length() > 0) doubleValue = Double.parseDouble(doubleString);
            return doubleValue;
         } catch (NumberFormatException nfe) {
            System.out.print(errorMessage + " please re-enter:  ");
            doubleString = in.nextLine();
         }
      }
   }

   protected int parseInt(String intString, String errorMessage) {
      int intValue = 0;
      boolean done = false;
      Scanner in = new Scanner(System.in);
      while (true) {
         try {
            if (intString.length() > 0) intValue = Integer.parseInt(intString);
            return intValue;
         } catch (NumberFormatException nfe) {
            System.out.print(errorMessage + " please re-enter:  ");
            intString = in.nextLine();
         }
      }
   }


   /*
    * Main methods for TransactionResolverCmdLine:
    */
   @Override
   public void beginImportItem() {
      // Print a blank line to separate this item from the previous item visually:
      System.out.println();
   }

   // Find or create a merchant for a transaction:
   @Override
   public Merchant assignMerchant(String merchantPayeeString) throws ViewException, RegisterException, EntityException {

      try {
         System.out.println("Failed to find a merchant for payee " + merchantPayeeString);
         boolean stop = false;
         boolean found = false;
         Merchant merchant = null;
         MerchantPayee merchantPayee = null;
         while (!stop) {
            System.out.print("Enter the merchant name: ");
            Scanner in = new Scanner(System.in);
            String line = in.nextLine();
            switch (line) {
               case "":
                  continue;

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
                     if (!merchantPayeeString.equalsIgnoreCase("Check")) {
                        merchantPayee = merchant.addPayee(merchantPayeeString);
                        merchantPayee.save();
                     }
                     stop = true;
                     terminationCondition = Importer.TerminationCondition.FOUND;
                     break;
                  } else {

                     // Merchant not found.  Create it if that's what the user wants:
                     System.out.print("Merchant doesn't exist.  Create it (y/n): ");
                     String yesOrNo = in.nextLine();

                     // If the user wants to create a new merchant with that name:
                     if (yesOrNo.equalsIgnoreCase("y")) {
                        merchant = Merchant.loadFromCSV(line);

                        System.out.println("Do you always want to approve budget allocations for this merchant?");
                        yesOrNo = in.nextLine();
                        merchant.setAskAlways((yesOrNo.equalsIgnoreCase("y")));

                        // Checks don't have payees:
                        if (!merchantPayeeString.equalsIgnoreCase("Check")) {
                           merchant.addPayee(merchantPayeeString);
                        }
                        merchant.save();
                        found = true;
                        stop = true;
                        terminationCondition = Importer.TerminationCondition.FOUND;
                     } else {
                        found = false;
                        stop = false;
                     }
                  }
            }
         }
         return merchant;

      } catch (Exception e) {
         ViewException ve = new ViewException("Exception occured trying to assign a merchant for this transaction: " +
                 merchantPayeeString + ".");
         ve.initCause(e);
         throw ve;
      }
   }

   // The account number was not in the payee string, so ask the user for help:
   @Override
   public String resolveUnmatchedAccount(String payee) throws RegisterException {
      String accountNumber = null;
      System.out.println("There is no account number in the following transaction payee: " + payee + ".");
      System.out.println("Enter the last four digits of the account number to assign this transaction to:  ");
      List<Register> registers = Register.getListOf();
      for (Register register : registers
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

   // Assign budget items to a new list of budget items:
   public List<BudgetItemMerchant> assignBudgetItems(Merchant merchant)
           throws BudgetException, ParseException, SQLException, ViewException, EntityException, RegisterException {

      List<BudgetItemMerchant> budgetItems = new ArrayList<BudgetItemMerchant>();
      budgetItems = assignBudgetItems(merchant, budgetItems);
      return budgetItems;
   }

   // Assign new budget items to an existing list of budget items:
   public List<BudgetItemMerchant> assignBudgetItems(Merchant merchant, List<BudgetItemMerchant> budgetItems)
           throws BudgetException, ParseException, SQLException, ViewException, EntityException, RegisterException {

      try {
         System.out.println("Failed to find any budget items for merchant " + merchant.getName());
         boolean done = false;
         boolean found = false;
         String yesOrNo = "n";
         while (!done) {
            System.out.print("Enter a budget item payee, and optionally, a fixed amount and fixed percentage" +
                    ": ");
            Scanner in = new Scanner(System.in);
            String line = in.nextLine();
            switch (line) {
               case "":
                  continue;

               case "reset":
                  System.out.println("Nothing to reset at this time.");
                  continue;

               case "restart":
                  System.out.println("The import process cannot be restarted.");
                  continue;

               case "quit":
                  done = true;
                  terminationCondition = Importer.TerminationCondition.QUIT;
                  break;

               default:
                  String[] tokens = line.split(",");
                  double amount = 0;
                  int percentage = 0;
                  BudgetItem budgetItem = BudgetItem.getByPayee(tokens[0]);

                  // If the budget item doesn't exist, then create it:
                  if (budgetItem == null) {
                     if (getYesOrNo("Specified budget item not found.  Create as a new budget item")) {
                        // read in a new budget item for this:
                        System.out.println("Enter the budget item in this order: category, payee, period type, amount, " +
                                "start date, number of payments, end date, item type, how paid, budget name:");
                        budgetItem = BudgetItem.loadFromCSV(in.nextLine());
                        budgetItem.save();
                     } else {
                        continue;
                     }
                  }

                  // Associate the budget item with the merchant:
                  if (tokens.length > 1) amount = parseDouble(tokens[1], "Invalid amount");
                  if (tokens.length > 2) percentage = parseInt(tokens[2], "Invalid percentage");
                  BudgetItemMerchant budgetItemMerchant = merchant.addBudgetItem(budgetItem, amount, percentage);
                  budgetItems.add(budgetItemMerchant);
                  terminationCondition = Importer.TerminationCondition.FOUND;
                  break;

            } // End switch on entered budget item.

            // Ask the user if they are done:
            done = !getYesOrNo("Assign another category to merchant " + merchant.getName());

         } // End while there are budget items to enter.

         return budgetItems;

      } catch (Exception e) {
         ViewException ve = new ViewException("Exception occured trying to import this transaction: " +
                 merchant + ".");
         ve.initCause(e);
         throw ve;
      }
   }


   @Override
   public void assignAmountsToBudgetItems(Transaction transaction, Merchant merchant, List<BudgetItemMerchant>
           budgetItemMerchants) throws EntityException, RegisterException, SQLException, ViewException, BudgetException, ParseException {

      // If we need to ask the user to enter the splits:
      List<TransactionSplit> transactionSplits = new ArrayList<TransactionSplit>();
      if (
              merchant.isAskAlways() || // If this is a merchant that the user wants to be asked about everytime, or
                      (
                              budgetItemMerchants.size() > 1 && // there is more then one budget item and
                                      // they are not fixed amounts:
                                      (budgetItemMerchants.get(0).getAmount() != 0 ||
                                              budgetItemMerchants.get(0).getPercentage() != 0)
                      )
      ) {
         // Ask the user to enter the splits:
         getSplits(transaction, merchant, budgetItemMerchants);
      } else {
         // Track the total of the splits so that we can ensure they splits balance in the end:
         double transactionAmount = transaction.getAmount();

         // Iterate over the splits one at a time assigning amounts to each one:
         TransactionSplit transactionSplit = null;
         for (BudgetItemMerchant budgetItemMerchant : budgetItemMerchants
         ) {

            // If this split is for a fixed amount:
            if (budgetItemMerchant.getAmount() > 0) {
               transactionSplit = new TransactionSplit(budgetItemMerchant.getAmount(),
                       budgetItemMerchant.getIdBudgetItem(), transaction.getId());
               transactionAmount = transactionAmount - budgetItemMerchant.getAmount();
            }
            // else if this split if for a fixed percentage of the transaction amount:
            else {
               if (budgetItemMerchant.getPercentage() > 0) {
                  transactionSplit = new TransactionSplit((budgetItemMerchant.getPercentage() /
                          100) * transaction.getAmount(), budgetItemMerchant.getIdBudgetItem(), transaction.getId());
                  transactionAmount = transactionAmount - (budgetItemMerchant.getPercentage() /
                          100) * transaction.getAmount();
               }
               // else there is only one budget item, so allocate the whole transaction amount to it:
               else {
                  transactionSplit = new TransactionSplit(transaction.getAmount(),
                          budgetItemMerchant.getIdBudgetItem(), transaction.getId());
                  transactionAmount = transactionAmount - transaction.getAmount();
               }
            }
            System.out.println("Assigned $" + Math.abs(transactionSplit.getAmount()) + " of it to the budget category " +
                    BudgetItem.getPayeeById(transactionSplit.getIdBudgetItem()));
         }
         if (transactionAmount != 0) {
            System.out.println("Automatic splits don't add up to the transaction amount, please enter them manually.");
            TransactionSplit.deleteSplitsForTransaction(transaction.getId());
            getSplits(transaction, merchant, budgetItemMerchants);
         }
      }

   }

   // Interact with the user to confirm or override the budget item amounts and then create splits for them.  Allow the
   // user to and add new budget items as well:
   public void getSplits(Transaction transaction, Merchant merchant, List<BudgetItemMerchant> budgetItems)
           throws ParseException, ViewException, EntityException, SQLException, BudgetException, RegisterException {

      // If there are assigned budget items:
      String[] overrideAmounts = null;
      if (budgetItems.size() > 0) {

         // Show them to the user:
         showAssignedBudgetItems(budgetItems, transaction.getAmount());

         // Ask the user if they want to add splits:
         while (getYesOrNo("Would you like to add more budget items to this merchant?")) {
            List<BudgetItemMerchant> newBudgetItems = assignBudgetItems(merchant);
            for (BudgetItemMerchant budgetItem : newBudgetItems
            ) {
               budgetItems.add(budgetItem);
            }
            showAssignedBudgetItems(budgetItems, transaction.getAmount());
         }

         // Then if there is more than one budget item to allocate to:
         if (budgetItems.size() > 1) {
            // Then ask the user to confirm or override the amounts:
            if (budgetItems.get(0).getAmount() > 0 || budgetItems.get(0).getPercentage() > 0) {
               overrideAmounts = getAndParseCsvLine("Enter the split amounts, or just return to accept displayed amounts:",
                       budgetItems.size(), true, "+");
            } else {
               overrideAmounts = getAndParseCsvLine("Enter the split amounts:",
                       budgetItems.size(), false, "+");
            }
         }
      }

      // Create the splits:
      if (overrideAmounts != null) {
         boolean useOverrides = overrideAmounts.length > 0;
         List<TransactionSplit> splits = new ArrayList<TransactionSplit>();
         switch (overrideAmounts[0]) {

            case ("+++"):  // Assign the entire transaction amount to the third budget item:
               splits.add(new TransactionSplit(budgetItems.get(2), transaction.getAmount()));
               break;

            case ("++"):  // Assign the entire transaction amount to the second budget item:
               splits.add(new TransactionSplit(budgetItems.get(1), transaction.getAmount()));
               break;

            case ("+"):  // Assign the entire transaction amount to the first budget item:
               splits.add(new TransactionSplit(budgetItems.get(0), transaction.getAmount()));
               break;

            default:
               for (int i = 0; i < budgetItems.size(); i++) {
                  // If the splits are not based on percentages, then use amounts:
                  if (budgetItems.get(i).getPercentage() == 0)
                     splits.add(new TransactionSplit(budgetItems.get(i), (useOverrides) ?
                             Double.parseDouble(overrideAmounts[i]) :
                             budgetItems.get(i).getAmount())
                     );
                  else  // use the percentages:
                     splits.add(new TransactionSplit(budgetItems.get(i), (useOverrides) ?
                             (Integer.parseInt(overrideAmounts[i]) / 100) * transaction.getAmount() :
                             (budgetItems.get(i).getPercentage() / 100) * transaction.getAmount())
                     );
               }
         }
      }
   }

   // Print a prompt, get a response, parse it based on commas and return it in a string array:
   private String[] getAndParseCsvLine(String prompt, int numberOfRequiredValues, boolean allowNullEntry, String specialChar) {
      String[] tokens = null;
      boolean done = false;
      while (!done) {
         System.out.print(prompt);
         Scanner in = new Scanner(System.in);
         String line = in.nextLine();
         tokens = line.split(",");
         if (specialChar.length() > 0 && line.length() > 0 && specialChar.getBytes()[0] == line.getBytes()[0]) {
            done = true;
            continue;
         }
         if (tokens.length < numberOfRequiredValues || tokens.length > numberOfRequiredValues) {
            System.out.print("Wrong number of values entered.  Please enter " + numberOfRequiredValues + " value(s).");
         } else {
            done = true;
         }
      }
      return tokens;
   }

   // Show a list of the assigned budget items for a transaction, and the amount of the transaction:
   private void showAssignedBudgetItems(List<BudgetItemMerchant> budgetItems, double amount) {

      System.out.println("The assigned budget items for this merchant are:");
      for (BudgetItemMerchant budgetItem : budgetItems
      ) {
         String lineEnd = "";
         if (budgetItem.getAmount() > 0) {
            lineEnd = ", " + Utility.formatDollarAmount(budgetItem.getBudgetItem().getAmount()) + ", 0";
         } else {
            if (budgetItem.getPercentage() > 0) {
               lineEnd = ", 0, " + budgetItem.getPercentage() + "%";
            }
         }
         System.out.println("   " + budgetItem.getBudgetItem().getPayee() + lineEnd);
      }
   }
}
