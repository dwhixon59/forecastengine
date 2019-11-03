package com.hixon.financial.view.register;

import com.hixon.financial.Utility;
import com.hixon.financial.controller.Importer;
import com.hixon.financial.model.EntityException;
import com.hixon.financial.model.EntityInt;
import com.hixon.financial.model.budget.BudgetException;
import com.hixon.financial.model.budget.BudgetItem;
import com.hixon.financial.model.budget.BudgetItemMerchant;
import com.hixon.financial.model.forecast.ForecastTransaction;
import com.hixon.financial.model.forecast.ForecastTransactionSplit;
import com.hixon.financial.model.register.*;
import com.hixon.financial.view.ViewException;

import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Scanner;

import static com.hixon.financial.model.forecast.ForecastTransactionSplit.SplitDisposition.*;

public class TransactionResolverCmdLine implements TransactionResolver {

   /*
    * Fields for TransactionResolverCmdLine:
    */
   private Importer.TerminationCondition terminationCondition;
   private Scanner in;


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
   public TransactionResolverCmdLine() {
      terminationCondition = Importer.TerminationCondition.QUIT;
      in = new Scanner(System.in);
   }


   /*
    * Helper methods for TransactionResolverCmdLine:
    */
   private void say() {
      System.out.println();
   }

   @Override
   public void say(String s) {
      System.out.println(s);
   }

   private void ask(String s) {
      System.out.print(s);
   }

   private boolean getYesOrNo(String question) {
      say(question + " (y/n): ");
      while (true) {
         String line = in.nextLine();
         if (line.equalsIgnoreCase("y")) return true;
         if (line.equalsIgnoreCase("n")) return false;
         ask("\nPlease enter 'y' or 'n': ");
      }
   }

   protected double parseDouble(String doubleString, String errorMessage) {
      double doubleValue = 0;
      while (true) {
         try {
            if (doubleString.length() > 0) doubleValue = Double.parseDouble(doubleString);
            return doubleValue;
         } catch (NumberFormatException nfe) {
            ask(errorMessage + " please re-enter:  ");
            doubleString = in.nextLine();
         }
      }
   }

   protected int parseInt(String intString, String errorMessage) {
      int intValue = 0;
      while (true) {
         try {
            if (intString.length() > 0) intValue = Integer.parseInt(intString);
            return intValue;
         } catch (NumberFormatException nfe) {
            ask(errorMessage + " please re-enter:  ");
            intString = in.nextLine();
         }
      }
   }

   private String parseDollarAmount(String prompt, double defaultAmount) {
      say(prompt + ", or just press enter to accept the amount " +
              Utility.formatDollarAmount(Math.abs(defaultAmount)) + ":  ");
      String newAmount = in.nextLine();
      if (newAmount.length() == 0) {
         newAmount = Double.toString(defaultAmount);
      } else {
         newAmount = String.valueOf(parseDouble(newAmount, "Invalid amount,"));
      }
      return newAmount;
   }

   // Parse a date in mm/dd/yy format:
   private String parseDate(String prompt, Calendar defaultDate) {
      ask(prompt);
      if (defaultDate == null) {
         say(" (mm/dd/yy)");
      } else {
         say(" (mm/dd/yy) or just hit enter to accept the date " + Utility.calendarDateToStringDate(defaultDate));
      }
      String line = in.nextLine();
      boolean done = false;
      while (!done) {
         try {
            if (defaultDate != null && line.length() == 0) {
               line = Utility.calendarDateToStringDate(defaultDate);
            }
            Calendar date = Utility.stringDateDashToCalendarDate(line);
            done = true;
         } catch (ParseException e) {
            say("Invalid date format.  Please re-enter:");
            line = in.nextLine();
         }
      }
      return line;
   }


   /*
    * Main methods for TransactionResolverCmdLine:
    */
   @Override
   public void beginImportItem() {
      // Print a blank line to separate this item from the previous item visually:
      say();
   }

   // Find or create a merchant for a transaction:
   @Override
   public Merchant assignMerchant(String merchantPayeeString, String transactionPayeeString) throws ViewException, RegisterException, EntityException {

      try {
         say("Failed to find a merchant for payee \"" + merchantPayeeString + "\" derived from transaction payee:  "
                 + transactionPayeeString);
         boolean stop = false;
         Merchant merchant = null;
         MerchantPayee merchantPayee;
         while (!stop) {
            ask("Enter the merchant name: ");
            String line = in.nextLine();
            switch (line) {
               case "":
                  continue;

               case "reset":
                  say("Nothing to reset at this time.");
                  continue;

               case "restart":
                  say("The import process cannot be restarted.");
                  continue;

               case "quit":
                  stop = true;
                  terminationCondition = Importer.TerminationCondition.QUIT;
                  break;

               default:
                  merchant = Merchant.getByNameLike(line);
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
                     ask("Merchant doesn't exist.  Create it (y/n): ");
                     String yesOrNo = in.nextLine();

                     // If the user wants to create a new merchant with that name:
                     if (yesOrNo.equalsIgnoreCase("y")) {
                        merchant = Merchant.loadFromCSV(line);

                        say("Do you always want to approve budget allocations for this merchant?");
                        yesOrNo = in.nextLine();
                        merchant.setAskAlways((yesOrNo.equalsIgnoreCase("y")));

                        // Checks don't have payees:
                        if (!merchantPayeeString.equalsIgnoreCase("Check")) {
                           merchant.addPayee(merchantPayeeString);
                        }
                        merchant.save();
                        stop = true;
                        terminationCondition = Importer.TerminationCondition.FOUND;
                     } else {
                        stop = false;
                     }
                  }
            }
         }
         return merchant;

      } catch (Exception e) {
         ViewException ve = new ViewException("Exception occurred trying to assign a merchant for this transaction: " +
                 merchantPayeeString + ".");
         ve.initCause(e);
         throw ve;
      }
   }

   // The account number was not in the payee string, so ask the user for help:
   @Override
   public String resolveUnmatchedAccount(String payee) throws RegisterException {
      String accountNumber = null;
      say("There is no account number in the following transaction payee: " + payee + ".");
      say("Enter the last four digits of the account number to assign this transaction to:  ");
      List<Register> registers = Register.getListOf();
      for (Register register : registers
      ) {
         say(register.getRegisterName() + ", " + register.getAccountType() + ", " +
                 register.getAccountNumber());
      }
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
            ask("Not in the list.  Re-enter the account number:");
            in.nextLine();
         }
      }
      return accountNumber;
   }

   // Assign budget items to a new list of budget items:
   public List<BudgetItemMerchant> assignBudgetItems(Merchant merchant)
           throws BudgetException, ViewException, EntityException, RegisterException {

      say("Failed to find any budget items for merchant " + merchant.getName());
      List<BudgetItemMerchant> budgetItems = new ArrayList<>();
      assignMoreBudgetItems(merchant, budgetItems);
      return budgetItems;
   }

   // Assign new budget items to an existing list of budget items:
   public void assignMoreBudgetItems(Merchant merchant, List<BudgetItemMerchant> budgetItems)
           throws BudgetException, ViewException, EntityException, RegisterException {

      try {
         boolean done = false;
         while (!done) {
            ask("Enter a budget item payee, and optionally, a fixed amount and fixed percentage" +
                    ": ");
            String line = in.nextLine();
            switch (line) {
               case "":
                  continue;

               case "reset":
                  say("Nothing to reset at this time.");
                  continue;

               case "restart":
                  say("The import process cannot be restarted.");
                  continue;

               case "quit":
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
                        say("Enter the budget item in this order: category, payee, period type, amount, " +
                                "running balance, start date, number of payments, end date, item type, how important, " +
                                "how occurs, how paid, budget name:");
                        budgetItem = BudgetItem.loadFromUserCSV(in.nextLine());
                        budgetItem.save(EntityInt.SaveMethod.INSERT);
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

      } catch (Exception e) {
         ViewException ve = new ViewException("Exception occurred trying to import this transaction: " +
                 merchant + ".");
         ve.initCause(e);
         throw ve;
      }
   }


   @Override
   public List<TransactionSplit> assignAmountsToBudgetItems(Transaction transaction, Merchant merchant, List<BudgetItemMerchant>
           budgetItemMerchants) throws EntityException, RegisterException, ViewException, BudgetException {

      // If we need to ask the user to enter the splits:
      List<TransactionSplit> splits = new ArrayList<>();
      if (
              merchant.isAskAlways() || // If this is a merchant that the user wants to be asked about every time,
                      (
                              (budgetItemMerchants.size() > 1) && // or there is more then one budget item and
                                      // they are not fixed amounts:
                                      ((budgetItemMerchants.get(0).getAmount() == 0) &&
                                              (budgetItemMerchants.get(0).getPercentage() == 0))
                      )
      ) {
         // Ask the user to enter the splits:
         getSplits(transaction, splits, merchant, budgetItemMerchants);
      } else {
         // Track the total of the splits so that we can ensure they splits balance in the end:
         double transactionAmount = transaction.getAmount();

         // Iterate over the splits one at a time assigning amounts to each one:
         TransactionSplit transactionSplit;
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
            splits.add(transactionSplit);
            say("Assigned $" + Math.abs(transactionSplit.getAmount()) + " of it to the budget category " +
                    BudgetItem.getPayeeById(transactionSplit.getIdBudgetItem()));
         }
         if (transactionAmount != 0) {
            say("Automatic splits don't add up to the transaction amount, please enter them manually.");
            TransactionSplit.deleteSplitsForTransaction(transaction.getId());
            getSplits(transaction, splits, merchant, budgetItemMerchants);
         }
      }
      return splits;
   }


   /*
    * getSplits()
    *
    * Interact with the user to confirm or override the budget item amounts and then create splits for them.  Allow the
    * user to and add new budget items and create splits for them as well.
    */
   public void getSplits(Transaction transaction, List<TransactionSplit> splits, Merchant merchant,
                         List<BudgetItemMerchant> budgetItemsForMerchant)
           throws ViewException, EntityException, BudgetException, RegisterException {

      // There should be at least one budget item.  If there isn't then throw an error:
      if (budgetItemsForMerchant.size() == 0) {
         throw new ViewException("Must be at least one budget item assigned to a transaction to be able to get the " +
                 "splits for  it.");
      }

      // Show the assigned budget items to the user:
      showAssignedBudgetItems(budgetItemsForMerchant, transaction.getAmount());

      // Allow the user to add budget items to this list if they want to:
      while (getYesOrNo("Would you like to add more budget items to this merchant?")) {
         assignMoreBudgetItems(merchant, budgetItemsForMerchant);
         showAssignedBudgetItems(budgetItemsForMerchant, transaction.getAmount());
      }

      // Figure out the amounts of the splits, e.g. how much of the transaction amount to allocate to each of the budget items:
      // If there is more than one budget item to allocate to:
      String[] amounts;
      if (budgetItemsForMerchant.size() > 1) {
         // then if the amounts are pre-established in the budget item:
         if (budgetItemsForMerchant.get(0).getAmount() > 0 || budgetItemsForMerchant.get(0).getPercentage() > 0) {

            // Then ask the user to confirm or override the amounts:
            amounts = getAndParseCsvLine("Enter the split amounts, or just return to accept displayed amounts:",
                    budgetItemsForMerchant.size(), true, "+");

         } else { // the amounts are not pre-established, so ask the user to enter them:
            amounts = getAndParseCsvLine("Enter the split amounts:",
                    budgetItemsForMerchant.size(), false, "+");
         }
      } else { // since there is only one possible budget item to allocate the transaction amount to:

         // then allocate the entire amount of the transaction to the budget item:
         amounts = new String[1];
         amounts[0] = Double.toString(-transaction.getAmount());
      }

      // Create the splits:
      switch (amounts[0]) {

         case ("+++"):  // Assign the entire transaction amount to the third budget item:
            splits.add(new TransactionSplit(transaction.getAmount(), budgetItemsForMerchant.get(2), transaction));
            break;

         case ("++"):  // Assign the entire transaction amount to the second budget item:
            splits.add(new TransactionSplit(transaction.getAmount(), budgetItemsForMerchant.get(1), transaction));
            break;

         case ("+"):  // Assign the entire transaction amount to the first budget item:
            splits.add(new TransactionSplit(transaction.getAmount(), budgetItemsForMerchant.get(0), transaction));
            break;

         case ("+4"):  // Assign the entire transaction amount to the first budget item:
            splits.add(new TransactionSplit(transaction.getAmount(), budgetItemsForMerchant.get(3), transaction));
            break;

         case ("+5"):  // Assign the entire transaction amount to the first budget item:
            splits.add(new TransactionSplit(transaction.getAmount(), budgetItemsForMerchant.get(4), transaction));
            break;

         case ("+6"):  // Assign the entire transaction amount to the first budget item:
            splits.add(new TransactionSplit(transaction.getAmount(), budgetItemsForMerchant.get(5), transaction));
            break;

         case ("+7"):  // Assign the entire transaction amount to the first budget item:
            splits.add(new TransactionSplit(transaction.getAmount(), budgetItemsForMerchant.get(6), transaction));
            break;

         case ("+8"):  // Assign the entire transaction amount to the first budget item:
            splits.add(new TransactionSplit(transaction.getAmount(), budgetItemsForMerchant.get(7), transaction));
            break;

         case ("+9"):  // Assign the entire transaction amount to the first budget item:
            splits.add(new TransactionSplit(transaction.getAmount(), budgetItemsForMerchant.get(8), transaction));
            break;

         case ("+10"):  // Assign the entire transaction amount to the first budget item:
            splits.add(new TransactionSplit(transaction.getAmount(), budgetItemsForMerchant.get(9), transaction));
            break;

         default:
            // if the user didn't enter any overrides:
            boolean useEnteredAmounts = amounts.length != 1 || amounts[0].length() != 0;
            for (int i = 0; i < budgetItemsForMerchant.size(); i++) {

               double enteredAmount = (useEnteredAmounts) ? parseDouble(amounts[i], "Must be a dollar amount.") : 0;

               // Don't create a split if the user entered zero for this budget item:
               if (!useEnteredAmounts || enteredAmount != 0) {

                  // If the splits are not based on percentages, then use amounts:
                  if (budgetItemsForMerchant.get(i).getPercentage() == 0) {
                     splits.add(new TransactionSplit((useEnteredAmounts) ? -enteredAmount :
                             budgetItemsForMerchant.get(i).getAmount(), budgetItemsForMerchant.get(i), transaction)
                     );
                  } else  // use the percentages:
                  {
                     splits.add(new TransactionSplit((useEnteredAmounts) ?
                             (Integer.parseInt(amounts[i]) / 100) * transaction.getAmount() :
                             (budgetItemsForMerchant.get(i).getPercentage() / 100) * transaction.getAmount(),
                             budgetItemsForMerchant.get(i), transaction)
                     );
                  }
               }
            }
      }
   }

   // Ask the user if the want to regenerate the forecast:
   @Override
   public boolean askRegenerateForecast() {
      return getYesOrNo("Changes were made to budget items and the forecast is out of sync now.  Do you want " +
              "to regenerate the long term forecast?");
   }

   // Print a prompt, get a response, parse it based on commas and return it in a string array:
   private String[] getAndParseCsvLine(String prompt, int numberOfRequiredValues, boolean allowNullEntry, String specialChar) {
      String[] tokens = null;
      boolean done = false;
      while (!done) {
         ask(prompt);
         String line = in.nextLine();
         tokens = line.split(",");
         // if the user just hit enter and that's allowed:
         if (line.length() == 0 && allowNullEntry) {
            // then return an empty array:
            done = true;
            continue;
         }
         if (specialChar != null && specialChar.length() > 0 && line.length() > 0 &&
                 specialChar.getBytes()[0] == line.getBytes()[0]) {
            done = true;
            continue;
         }
         if (tokens.length < numberOfRequiredValues || tokens.length > numberOfRequiredValues) {
            ask("Wrong number of values entered.  Please enter " + numberOfRequiredValues + " value(s).");
         } else {
            done = true;
         }
      }
      return tokens;
   }

   // Show a list of the assigned budget items for a transaction, and the amount of the transaction:
   private void showAssignedBudgetItems(List<BudgetItemMerchant> budgetItems, double amount) {

      say("The transaction amount is " + Utility.formatDollarAmount(amount));
      say("The assigned budget items and amounts (if specified) for this merchant are:");
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
         say("   " + budgetItem.getBudgetItem().getPayee() + lineEnd);
      }
   }

   // What to do if the split amount exceeds the budgeted amount:
   @Override
   public ForecastTransactionSplit.SplitDisposition assignOverageAmount(double amount) {
      ForecastTransactionSplit.SplitDisposition disposition = null;

      say("You exceeded the budgeted amount for this budget item by " +
              Utility.formatDollarAmount(amount) + ".  What would you like to do (a-adjust,  d-dispute, i-ignore, " +
              "r-roll)?  ");

      boolean done = false;
      while (!done) {
         done = true;
         String line = in.nextLine();
         switch (line) {
            case "a":
               disposition = ADJUST;
               break;

            case "d":
               disposition = DISPUTE;
               break;

            case "i":
               disposition = IGNORE;
               break;

            case "r":
               disposition = ROLL_FORWARD;
               break;

            default:
               say("Please enter a, d, i or r.");
               done = false;
         }
      }
      return disposition;
   }

   // What to do if we're not sure which forecast transaction to assign a split to:
   @Override
   public UserResponse assignSplitAmountToForecastTransaction(TransactionSplit split, ForecastTransaction forecastTransaction) {
      UserResponse response = new UserResponse();

      say("Is this split an instance of this forecast transaction?  What would you like to do (a-adjust,"
              + " s-assign, d-dispute, i-ignore)?");
      say(split.toString());
      say(forecastTransaction.toString());

      boolean done = false;
      while (!done) {
         done = true;
         String line = in.nextLine();
         switch (line) {
            case "a":
               response.setDisposition(ADJUST);
               response.setResponse(parseDollarAmount("Enter the new amount", split.getAmount()));
               break;

            case "s":
               response.setDisposition(ASSIGN);
               break;

            case "d":
               response.setDisposition(DISPUTE);
               break;

            case "i":
               response.setDisposition(IGNORE);
               break;

            default:
               say("Please enter a, s, d, or i.");
               done = false;
         }
      }
      return response;
   }

   // What to do if we're not sure which forecast transaction to assign a split to:
   @Override
   public UserResponse assignSplitDateToForecastTransaction(TransactionSplit split, ForecastTransaction forecastTransaction)
           throws EntityException, SQLException {
      UserResponse response = new UserResponse();

      say("Is this split an instance of this forecast transaction?  What would you like to do (a-adjust,"
              + " s-assign, d-dispute, i-ignore)?");
      say(split.toString());
      say(forecastTransaction.toString());

      boolean done = false;
      while (!done) {
         done = true;
         String line = in.nextLine();
         switch (line) {
            case "a":
               response.setDisposition(ADJUST);
               response.setResponse(parseDate("Enter the new date", split.getTransaction().getDate()));
               break;

            case "s":
               response.setDisposition(ASSIGN);
               break;

            case "d":
               response.setDisposition(DISPUTE);
               break;

            case "i":
               response.setDisposition(IGNORE);
               break;

            default:
               say("Please enter a, s, d, or i.");
               done = false;
         }
      }
      return response;
   }

   // What to do if there is a discrepancy between the planned and actual amounts of a transaction.
   @Override
   public UserResponse transactionAmountDiscrepancy(Transaction transaction, TransactionSplit split,
                                                    ForecastTransaction forecastTransaction) {
      UserResponse response = new UserResponse();

      say("There is a discrepancy between the planned and actual amounts.  The planned amount is " +
              Utility.formatDollarAmount(-forecastTransaction.getRemainingAmount()));
      say("What would you like to do (a-adjust, s-assign, d-dispute, i-ignore)?  ");

      boolean done = false;
      while (!done) {
         done = true;
         String line = in.nextLine();
         switch (line) {
            case "a":
               response.setDisposition(ADJUST);
               response.setResponse(parseDollarAmount("Enter the new amount", split.getAmount()));
               break;

            case "s":
               response.setDisposition(ASSIGN);
               break;

            case "d":
               response.setDisposition(DISPUTE);
               break;

            case "i":
               response.setDisposition(IGNORE);
               break;

            default:
               say("Please enter a, s, or i.");
               done = false;
         }
      }
      return response;
   }

} // End class TransactionResolverCmdLine.
