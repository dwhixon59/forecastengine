package com.hixon.financialApp.model.forecast;

import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.entity.IndependentEntity;
import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.model.register.TransactionSplit;
import com.hixon.financialApp.view.base.TransactionResolverInt;
import com.hixon.financialApp.view.base.UserResponse;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import static com.hixon.financialApp.model.entity.EntityInt.SaveMethod.*;
import static com.hixon.financialApp.model.forecast.ForecastTransactionSplit.SplitDisposition.*;

/**
 * This the class that represents a single transaction in the forecast.
 */
public class ForecastTransaction extends IndependentEntity {

   /*
    * Fields:
    */
   // Amount of the transaction in case of an override:
   private double remainingAmount = 0;

   // The date that this transaction is expected to occur, or is due:
   private Calendar plannedDate;

   // The id of the forecast item that this transaction is an instance of:
   private UUID idForecastItem = null;

   // Indicates if this is the first occurrence of this forecast item in the forecast:
   private boolean firstOccurrence = false;

   // Running runningBalance of the forecast:
   private double runningBalance = 0;

   // A reference to the forecast item that this transaction is an occurrence of;
   private ForecastItem forecastItem = null;

   // A pointer to the next transaction on the same date:
   private ForecastTransaction nextTransaction = null;

   // A pointer to the next transaction in the list of significant transactions:
   private ForecastTransaction nextSignificantEvent = null;

   // The select query for forecast transactions for a forecast item:
   public static final String transactionsForItemQuery = "select a.idForecastTransaction as 'id', a.remainingAmount, " +
           "a.plannedDate, a.firstOccurrence, a.ForecastItem_idForecastItem as 'idForecastItem' from " +
           "forecastdatabase.Forecast_Transaction a inner join ForecastDatabase.Forecast_Item b on " +
           "a.ForecastItem_idForecastItem = b.idForecastItem where a.remainingAmount > 0";

   // The select query:
   public static final String selectColumns = " bin_to_uuid(ft.idForecastTransaction) as 'ft.idForecastTransaction', " +
           "ft.remainingAmount as 'ft.remainingAmount', ft.plannedDate as ' ft.plannedDate', ft.firstOccurrence as " +
           "'ft.firstOccurrence', bin_to_uuid(ft.ForecastItem_idForecastItem) as 'ft.idForecastItem' ";
   public static String getSelectColumns() {
      return selectColumns;
   }
   public static final String selectQuery = "select" + selectColumns + "from forecastdatabase.Forecast_Transaction ft" ;

   // The insert query:
   public static final String insertQuery = "insert into ForecastDatabase.Forecast_Transaction (idForecastTransaction, " +
           "remainingAmount, plannedDate, firstOccurrence, ForecastItem_idForecastItem) values (";

   @Override
   public String getInsertQuery() {
      return insertQuery + "uuid_to_bin('" + id + "'), " + remainingAmount + ", " +
              Utility.calendarDateToSqlDateString(plannedDate) + ", " + firstOccurrence + ", uuid_to_bin('" +
              idForecastItem + "'))";
   }

   // The insert on duplicate update query:
   @Override
   public String getInsertOnDuplicateUpdateQuery() {
      String query = getInsertQuery() + "on duplicate key update "+ getupdateClause();
      return query;
   }

   // The update query:
   public static final String updateQuery = "update ForecastDatabase.Forecast_Transaction set ";

   public String getupdateClause() {
      return  "remainingAmount = " + remainingAmount + ", plannedDate = " +
              Utility.calendarDateToSqlDateString(plannedDate) + ", firstOccurrence = " + firstOccurrence +
              " where idForecastTransaction = uuid_to_bin('" + id + "')";
   }

   @Override
   public String getUpdateByIdQuery() {
      return updateQuery + getupdateClause();
   }

   // The delete query:
   public static final String deleteQuery = "delete from ForecastDatabase.Forecast_Transaction ";

   public static String getDeleteQuery() {return deleteQuery;}

   @Override
   public String getDeleteByIdQuery() {
      return deleteQuery + "where idForecastTransaction = uuid_to_bin('" + id + "')";
   }

   // The entity name:
   @Override
   public String getPrintableEntityTypeName() {
      return "forecast transaction";
   }


   /*
    * Getters and setters:
    */
   public Calendar getPlannedDate() {
      return plannedDate;
   }

   public void setPlannedDate(Calendar plannedDate) {
      this.plannedDate = plannedDate;
      setDirty(true);
   }

   public double getRemainingAmount() {
      return remainingAmount;
   }

   public void setRemainingAmount(double remainingAmount) {
      this.remainingAmount = remainingAmount;
      setDirty(true);
   }

   public double getRunningBalance() {
      return runningBalance;
   }

   public void setRunningBalance(double runningBalance) {
      this.runningBalance = runningBalance;
      setDirty(true);
   }

   public UUID getIdForecastItem() {
      return idForecastItem;
   }

   public void setIdForecastItem(UUID idForecastItem) {
      this.idForecastItem = idForecastItem;
   }

   public ForecastItem getForecastItem() throws EntityException, SQLException, ForecastException, BudgetException {
      if (forecastItem == null) {
         forecastItem = ForecastItem.getById(idForecastItem);
      }
      return forecastItem;
   }

   public void setForecastItem(ForecastItem forecastItem) {
      this.forecastItem = forecastItem;
   }

   public ForecastTransaction getNextTransaction() {
      return nextTransaction;
   }

   public void setNextTransaction(ForecastTransaction nextTransaction) {
      this.nextTransaction = nextTransaction;
      setDirty(true);
   }

   public ForecastTransaction getNextSignificantEvent() {
      return nextSignificantEvent;
   }

   public void setNextSignificantEvent(ForecastTransaction forecastTransaction) {
      nextSignificantEvent = forecastTransaction;
      setDirty(true);
   }

   public boolean isFirstOccurrence() {
      return firstOccurrence;
   }

   public void setFirstOccurrence(boolean firstOccurrence) {
      this.firstOccurrence = firstOccurrence;
   }

   /*
    * Constructors:
    */
   public ForecastTransaction(ForecastItem item, Calendar nextDate, boolean firstOccurrence) throws Exception {
      super(true);
      if (item == null || nextDate == null) throw new Exception("ForecastItem seeds cannot be null.");
      remainingAmount = item.getAmount();
      plannedDate = (Calendar) nextDate.clone();
      this.firstOccurrence = firstOccurrence;
      idForecastItem = item.getId();
      forecastItem = item;
      System.out.println(this.toString());
   }

   public ForecastTransaction(ResultSet rs) throws SQLException {
      super(false);
      this.id = UUID.fromString(rs.getString("ft.idForecastTransaction"));
      this.remainingAmount = rs.getDouble("ft.remainingAmount");
      this.plannedDate = Utility.SqlDateToCalendarDate(rs.getDate("ft.plannedDate"));
      this.firstOccurrence = rs.getBoolean("ft.firstOccurrence");
      this.idForecastItem = UUID.fromString(rs.getString("ft.idForecastItem"));
   }

   public ForecastTransaction(ForecastTransaction forecastTransaction) throws Exception, EntityException, BudgetException {
      super(true);
      if (forecastTransaction == null) throw new Exception("Forecast transaction to copy cannot be null.");
      this.remainingAmount = forecastTransaction.getRemainingAmount();
      this.plannedDate = (Calendar) forecastTransaction.getPlannedDate().clone();
      this.firstOccurrence = forecastTransaction.isFirstOccurrence();
      this.idForecastItem = forecastTransaction.getIdForecastItem();
      forecastItem = forecastTransaction.getForecastItem();
   }

   /*
    *  Load and save methods:
    */


   /*
    *  Main methods:
    */
   public static ForecastTransactionIterator getForecastTransactionsStartingOn(Forecast forecast,
                                                                               Calendar startDate) throws EntityException, ForecastException, SQLException, BudgetException {
      ForecastTransactionIterator forecastTransactions;
      if (forecast.isDirty()) {
         forecastTransactions = new ForecastTransactionMemoryIterator(forecast, startDate);
      } else {
         forecastTransactions = new ForecastTransactionAndItemDatabaseIterator(forecast, startDate);
      }
      return forecastTransactions;
   }

   private static ForecastTransactionIterator getForecastTransactionsForBudgetItem(UUID idBudgetItem, UUID idForecast)
           throws EntityException {

         String selectQuery = "select bin_to_uuid(ft.idForecastTransaction) as 'ft.idForecastTransaction', ft.remainingAmount, " +
                 "ft.plannedDate, firstOccurrence, bin_to_uuid(ft.ForecastItem_idForecastItem) as 'ft.idForecastItem', " +
                 "fi.category, fi.payee, fi.amount as 'plannedAmount' from ForecastDatabase.Forecast_Transaction ft inner " +
                 "join ForecastDatabase.Forecast_Item fi on ft.ForecastItem_idForecastItem = fi.idForecastItem inner join " +
                 "ForecastDatabase.Budget_Item bi on fi.BudgetItem_idBudgetItem = bi.idBudgetItem where " +
                 "ft.remainingAmount <> 0 and bi.idBudgetItem = uuid_to_bin('" + idBudgetItem + "') and fi.Forecast_idForecast" +
                 " = uuid_to_bin('" + idForecast + "') order by ft.plannedDate asc ";

         ResultSet rs = EntityInt.getRS(selectQuery, "Database error occurred attempting to " +
                 "get a list of Forecast Transactions by date.");

         return new ForecastTransactionDatabaseIterator(rs);

      }

   private static ForecastTransactionIterator getForecastTransactionsForForecastItem(UUID idForecastItem)
           throws EntityException {
      String selectQuery = "select bin_to_uuid(ft.idForecastTransaction) as idForecastTransaction, ft.remainingAmount, " +
              "plannedDate, firstOccurrence, bin_to_uuid(ft.ForecastItem_idForecastItem) as 'idForecastItem', " +
              "from ForecastDatabase.Forecast_Transaction ft where ft.remainingAmount <> 0 and ForecastItem_idForecastItem =" +
              " uuid_to_bin('" + idForecastItem + "')  order by ft.plannedDate asc ";

      ResultSet rs = EntityInt.getRS(selectQuery, "Database error occurred attempting to " +
              "get a list of Forecast Transactions by date.");

      return new ForecastTransactionDatabaseIterator(rs);
   }



   // Update the dates of all forecast transactions for a particular forecast item:
   private static boolean updateAllDates(ForecastTransaction forecastTransaction, Calendar newDate) throws EntityException,
           Exception, BudgetException {

      // Was the update successful?:
      boolean result;

      // If the user wants to move the date to before previous occurrences that would cause a problem:
      if (newDate.compareTo(forecastTransaction.getForecastItem().getPreviousDateOfOccurrence(
              forecastTransaction.getPlannedDate())) > 0) {
         result = false;
      } else {
         // Iterate over all the non-zero forecast transactions associated with this forecast item and update their dates.
         // Don't touch the ones that have already been fully reconciled.  To do this get a list forecast transactions
         // beginning with the earliest non-zero amount occurrence of a forecast transaction in the forecast for the
         // budget item associated with the split.  This should start with the existing forecast transaction:
         ForecastItem forecastItem = forecastTransaction.getForecastItem();
         forecastItem.setNextDate((Calendar) newDate.clone());
         ForecastTransactionIterator it =
                 ForecastTransaction.getForecastTransactionsForForecastItem(forecastTransaction.getIdForecastItem());
         ForecastTransaction forecastTransactionOccurrence = it.getNext();
         while (forecastTransactionOccurrence != null) {
            forecastTransactionOccurrence.setPlannedDate((Calendar) newDate.clone());
            newDate = forecastItem.getNextDateOfOccurrence();
            forecastTransactionOccurrence = it.getNext();
         }
         result = true;
      }
      return result;
   }


   /*
    *  Helper methods:
    */
   // Determine whether a date falls within the period this forecast transaction is effective:
   enum Timing {PRIOR_TO, WITHIN, AFTER}

   public Timing fallsWithinWindow(Calendar date) throws ForecastException, EntityException, BudgetException, SQLException {
      Timing timing = null;
      int comparison = date.compareTo(plannedDate);

      switch (getForecastItem().getHowOccurs()) {
         case COLLECTION:
         case PERIODIC:
         case VARIABLE_PERIODIC:
            // period is from the date of the forecast transaction to the day before the next occurrence
            // If the transaction date is earlier than the forecast transaction planned date:
            if (comparison < 0) {
               timing = Timing.PRIOR_TO;
            } else {
               // Compute the next date of occurrence of the related forecast item:
               getForecastItem().setNextDate((Calendar) plannedDate.clone());
               Calendar nextDate = forecastItem.getNextDateOfOccurrence();

               // If the transaction date is prior to the next forecast transaction planned date:
               if (nextDate != null && date.compareTo(nextDate) < 0) {
                  timing = Timing.WITHIN;
               } else {
                  timing = Timing.AFTER;
               }
            }
            break;

         case ENVELOPE:  // The period is from the date of the forecast transaction to the day after the previous occurrence
            // If the date passed in is before the date of this forecast transaction:
            if (comparison < 0) {
               // Then if it is after the date of the previous forecast transaction:
               if (date.compareTo(getForecastItem().getPreviousDateOfOccurrence(plannedDate)) > 0) {
                  // then it is within the period of this forecast transaction.
                  timing = Timing.WITHIN;
               } else { // else it is prior to the period of this forecast transaction.
                  timing = Timing.PRIOR_TO;
               }
            } else { // the date passed in is either equal to or after the date of this forecast transaction:
               // If date passed in is the same as the date of this forecast item:
               if (comparison == 0) {
                  // Then by definition it is within the period of this item:
                  timing = Timing.WITHIN;
               } else { // The date passed in is after the date of the forecast transaction:
                  // so then by definition the timing of the passed in date is after the period:
                  timing = Timing.AFTER;
               }
            }
            break;

         case UNPLANNED:
            // There is no planned date, so a transaction always "falls within" the window for one of these.
            timing = Timing.WITHIN;
            break;

         default:
            throw new ForecastException("Unknown 'howOccurs' method:  " + getForecastItem().getHowOccurs());
      }

      return timing;
   }

   @Override
   public String toString() {
      String s;
      try {
         s = "Forecast Transaction:  Planned Date = " + Utility.calendarDateToStringDate(this.getPlannedDate()) +
                 ", Category = " + this.getForecastItem().getCategory() +
                 ", Payee =  " + this.getForecastItem().getPayee() + ", Budgeted Amount = " +
                 Utility.formatDollarAmount(forecastItem.getAmount()) + ", Remaining Amount = " +
                 Utility.formatDollarAmount(remainingAmount) + ", first occurrence = " + firstOccurrence +
                 ", Forecast transaction - ID = " + this.getId().toString() + ", Next significant event = " +
                 this.getNextSignificantEvent();
      } catch (Exception | EntityException | BudgetException e) {
         s = "Unable to print out the forecast transaction.";
      }
      return s;
   }


   /*
    *  Main methods:
    */
   // Reconcile a register transaction with a forecast transaction:
   public static void reconcile(Forecast forecast, Transaction transaction, List<TransactionSplit> splits,
                                TransactionResolverInt resolver) throws Exception, RegisterException, EntityException, BudgetException {

      // For each split assigned to this transaction:
      ForecastTransactionSplit forecastTransactionSplit;
      for (TransactionSplit split : splits
      ) {
         // If it hasn't already been reconciled:
         if (ForecastTransactionSplit.getForecastTransactionSplitsCount(split, forecast) == 0) {

            // Find the forecast transaction in the list that this split applies to:
            ForecastTransaction forecastTransaction = getMatchingForecastTransaction(forecast, transaction, resolver,
                    split);

            // if we weren't able to match the split to a forecast transaction.
            if (forecastTransaction == null) {
               // Create a forecast transaction and forecast item for it so we have something to link the forecast
               // transaction split to:
               ForecastItem forecastItem = new ForecastItem(forecast, split.getBudgetItem());
               forecastItem.setAmount(1);
               forecastItem.save(INSERT);
               forecastTransaction = new ForecastTransaction(forecastItem, split.getTransaction().getDate(), true);
               forecastTransaction.setRemainingAmount(1);
               forecastTransaction.save(INSERT);
               split.setDisposition(IGNORE);
            }

            // if this split is part of the forecast:
            if (split.getDisposition() != DISPUTE && split.getDisposition() != IGNORE) {

               // Then deduct the amount of the split:
               forecastTransaction = deductSplitAmount(forecast, transaction, resolver, split, forecastTransaction);

            } // End if we were able to match the split to a forecast transaction.

            // And finally link the split to the forecast transaction for historical purposes:
            forecastTransactionSplit = new ForecastTransactionSplit(forecastTransaction, split);
            forecastTransactionSplit.save(INSERT);

         } // End if it hasn't already reconciled.
         else {
            System.out.println("Already reconciled.  Skipping.");
         } // End if it has already been reconciled.
      } // End for each split assigned to this transaction.
   } // End ForecastTransaction.reconcile().


   public static ForecastTransaction getMatchingForecastTransaction(Forecast forecast, Transaction transaction,
                                                                    TransactionResolverInt resolver, TransactionSplit split) throws EntityException, Exception, BudgetException,
           RegisterException {

      // Get a list forecast transactions beginning with the earliest non-zero amount occurrence of a forecast
      // transaction in the forecast for the budget item associated with the split:
      ForecastTransactionIterator it =
              ForecastTransaction.getForecastTransactionsForBudgetItem(split.getIdBudgetItem(), forecast.getId());

      // Find the forecast transaction in the list that this split applies to.  Roll up any old forecast transactions
      // encountered in the process:
      ForecastTransaction forecastTransaction = it.getNext();
      if (forecastTransaction != null) {

         Timing timing = forecastTransaction.fallsWithinWindow(transaction.getDate());
         switch (timing) {

            case PRIOR_TO:  // The split occurs before the period of this forecast transaction:

               switch (split.getBudgetItem().getHowOccurs()) {

                  case COLLECTION: // This split is an instance of overspending.
                     // If the transaction split occurred prior to the first occurrence of forecast item in the
                     // forecast, then it doesn't apply because collection forecast items always occur prior to any
                     // associated splits:
                     if (forecastTransaction.isFirstOccurrence()) {
                        split.setDisposition(IGNORE);
                        resolver.say("Split occurs before the first occurrence of the budget item " +
                                split.getBudgetItem().getPayee() + " in the forecast.  Ignoring it.");
                     } else {
                        resolver.say("The amount that was allocated for this budget item in the current period (" +
                                Utility.formatDollarAmount(split.getBudgetItem().getAmount()) + ") is exhausted.");
                        split.setDisposition(resolver.assignOverageAmount(split.getAmount()));
                     }
                     switch (split.getDisposition()) {
                        case ADJUST:
                           split.getBudgetItem().setAmount(split.getBudgetItem().getAmount() + split.getAmount());
                           split.getBudgetItem().save(INSERT_ON_DUPLICATE_UPDATE);
                           forecast.setInSync(false);
                           forecast.save(UPDATE);
                           break;

                        case DISPUTE:
                           transaction.setIsImproper(true);
                           transaction.save(UPDATE);
                           transaction.getRegister().addSignificantEvent(transaction);
                           break;

                        case IGNORE:
                        case ROLL_FORWARD:
                           break;
                     }
                     break;

                  case ENVELOPE: // We are before the effective start of this forecast item so nothing to reconcile to.
                     resolver.say("Split occurred before the forecast item became effective.  Ignoring.");
                     split.setDisposition(IGNORE);
                     break;

                  case PERIODIC: // The transaction was paid early?
                  case VARIABLE_PERIODIC:

                     // Determine if the actual date a forecast transaction occurred is "on or about" the planned date:
                     int variance = Utility.daysBeteween(forecastTransaction.getPlannedDate(), transaction.getDate());
                     if (!split.getBudgetItem().withinNormalDateVariance(variance)) {

                        // Ask the user to determine if the split is an occurrence of the forecast transaction:
                        UserResponse resp = resolver.assignSplitDateToForecastTransaction(split, forecastTransaction);
                        split.setDisposition(resp.getDisposition());
                        switch (split.getDisposition()) {

                           case ADJUST: // Change the seed date for the budget item:
                              split.getBudgetItem().setStartDate(Utility.stringDateDashToCalendarDate(
                                      resp.getResponse()));
                              split.getBudgetItem().save(UPDATE);
                              forecastTransaction.setPlannedDate(Utility.stringDateDashToCalendarDate(
                                      resp.getResponse()));
                              // TODO: ForecastTransaction.updateAllDates(forecastTransaction, Utility.stringDateDashToCalendarDate(resp.getResponse()));
                              forecast.setInSync(false);
                              forecast.save(UPDATE);
                              break;

                           case ASSIGN: // Assign the split to the forecast transaction:
                              break;

                           case IGNORE:
                              break;

                           case DISPUTE:
                              transaction.setIsImproper(true);
                              transaction.save(INSERT_ON_DUPLICATE_UPDATE);
                              transaction.getRegister().addSignificantEvent(transaction);
                              break;
                        }
                     }
                     break;

                  default:
                     throw new ForecastException("Invalid item howOccurs:  " + split.getBudgetItem().getHowOccurs()
                             + ".");
               }
               break;

            case WITHIN:  // Found the applicable forecast transaction.
               split.setDisposition(ASSIGN);
               break;

            case AFTER:  // There is money left from a prior period for the budgeted item:

               switch (split.getBudgetItem().getHowOccurs()) {

                  case COLLECTION: // This split is an instance of underspending.  Roll the money forward:
                     while (forecastTransaction.fallsWithinWindow(transaction.getDate()) == Timing.AFTER) {
                        double remainingAmount = forecastTransaction.getRemainingAmount();
                        forecastTransaction.setRemainingAmount(0);
                        forecastTransaction.save(UPDATE);
                        forecastTransaction = it.getNext();
                        if (forecastTransaction != null) {
                           forecastTransaction.setRemainingAmount(forecastTransaction.getRemainingAmount() +
                                   remainingAmount);
                        } else break;
                     }
                     split.setDisposition(ASSIGN);
                     break;

                  case ENVELOPE:  // Once the date for an envelope contribution passes, remove it:

                     // Roll up the expired items into the current item and mark them expired:
                     do {
                        // The budget item contains the running balance, so add this forecast transaction to it:
                        split.getBudgetItem().setRunningBalance(split.getBudgetItem().getRunningBalance() +
                                forecastTransaction.getRemainingAmount());
                        split.getBudgetItem().save(UPDATE);

                        // and zero out the forecast transaction:
                        forecastTransaction.setRemainingAmount(0);
                        forecastTransaction.save(UPDATE);
                        forecastTransaction = it.getNext();
                     } while (forecastTransaction != null &&
                             forecastTransaction.fallsWithinWindow(transaction.getDate()) == Timing.AFTER);
                     split.setDisposition(ASSIGN);
                     break;

                  case PERIODIC: // The transaction was paid late?
                  case VARIABLE_PERIODIC:
                  case UNPLANNED:

                     // Determine if the actual date a forecast transaction occurred is "on or about" the planned date:
                     int variance = Utility.daysBeteween(transaction.getDate(), forecastTransaction.getPlannedDate());
                     if (!split.getBudgetItem().withinNormalDateVariance(variance)) {

                        // Ask the user to determine if the split is an occurrence of the forecast transaction:
                        UserResponse resp = resolver.assignSplitAmountToForecastTransaction(split, forecastTransaction);
                        split.setDisposition(resp.getDisposition());
                        switch (split.getDisposition()) {

                           case ADJUST: // Change the seed date for the budget item:
                              split.getBudgetItem().setStartDate(Utility.stringDateDashToCalendarDate(
                                      resp.getResponse()));
                              split.getBudgetItem().save(UPDATE);

                              forecastTransaction.getForecastItem().setNextDate(Utility.stringDateDashToCalendarDate(
                                      resp.getResponse()));
                              forecastTransaction.getForecastItem().save(UPDATE);

                              forecastTransaction.setPlannedDate(Utility.stringDateDashToCalendarDate(
                                      resp.getResponse()));
                              forecastTransaction.save(UPDATE);

                              forecast.setInSync(false);
                              forecast.save(UPDATE);
                              break;

                           case ASSIGN: // Assign the split to the forecast transaction:
                              break;

                           case IGNORE:
                              break;

                           case DISPUTE:
                              transaction.setIsImproper(true);
                              transaction.save(INSERT_ON_DUPLICATE_UPDATE);
                              transaction.getRegister().addSignificantEvent(transaction);
                              break;
                        }
                     }
                     break;
               }
               break;
         }
      }
      return forecastTransaction;
   }


   // Deduct or zero the amount of the split from the forecast transaction or budget item as appropriate:
   public static ForecastTransaction deductSplitAmount(Forecast forecast, Transaction transaction,
                                                       TransactionResolverInt resolver, TransactionSplit split, ForecastTransaction forecastTransaction)
           throws EntityException, BudgetException, Exception, RegisterException {

      UserResponse resp;
      double remainingAmount = 0;
      // If the amount is substantially different than the assigned budget items:
      if (!split.getBudgetItem().withinNormalAmountVariance(forecastTransaction.getRemainingAmount() - split.getAmount())) {

         // Ask the user to determine if the split is an occurrence of the forecast transaction:
         resp = resolver.transactionAmountDiscrepancy(transaction, split, forecastTransaction);
         split.setDisposition(resp.getDisposition());
         switch (split.getDisposition()) {

            case ADJUST: // Change the amount of the budget item:
               split.getBudgetItem().setAmount(Double.parseDouble(resp.getResponse()));
               split.getBudgetItem().save(UPDATE);

               forecastTransaction.getForecastItem().setAmount(Double.parseDouble(resp.getResponse()));
               forecastTransaction.getForecastItem().save(UPDATE);

               forecastTransaction.setRemainingAmount(Double.parseDouble(resp.getResponse()));
               forecastTransaction.save(UPDATE);

               forecast.setInSync(false);
               forecast.save(UPDATE);
               break;

            case ASSIGN: // The actual amount is ok this one time.
               break;

            case DISPUTE:
               transaction.setIsImproper(true);
               transaction.save(INSERT_ON_DUPLICATE_UPDATE);
               transaction.getRegister().addSignificantEvent(transaction);
               remainingAmount = forecastTransaction.getRemainingAmount();
               break;

            case IGNORE:
               break;
         }
      }

      // Deduct the actual amount from the planned amount:
      if (split.getDisposition() != DISPUTE && split.getDisposition() != IGNORE) {
         switch (split.getBudgetItem().getHowOccurs()) {

            case COLLECTION:

               // If the user has overspent on this item, e.g. the amount of the split is greater than the remaining
               // amount in the current period of the budgeted amount per period for this budget item:
               if (split.getAmount() < forecastTransaction.getRemainingAmount()) {

                  // Ask the user what they would like to do:
                  resolver.say("The amount that was allocated for this budget item in the current period (" +
                          Utility.formatDollarAmount(split.getBudgetItem().getAmount()) + ") is exhausted.");
                  split.setDisposition(resolver.assignOverageAmount(split.getAmount()));

                  // Execute the user's request:
                  switch (split.getDisposition()) {

                     case ADJUST:  // The user would like to increase the budgeted amount to cover the overage:
                        split.getBudgetItem().setAmount(split.getBudgetItem().getAmount() + split.getAmount());
                        split.getBudgetItem().save(INSERT_ON_DUPLICATE_UPDATE);
                        forecast.setInSync(false);
                        forecast.save(UPDATE);
                        break;

                     case DISPUTE:  // The user believes that the register transaction is in error and would like to
                                    // dispute it and not reconcile the split:
                        transaction.setIsImproper(true);
                        transaction.save(UPDATE);
                        transaction.getRegister().addSignificantEvent(transaction);
                        break;

                     case IGNORE:  // This is a one time overage.  Ignore the overage and do not reconcile the split.
                                   // However, the amount spent exceeds the amount budgeted for this period, so zero
                                   // out the remaining amount for this budget item in the current period:
                        forecastTransaction.setRemainingAmount(0);
                        forecastTransaction.save(UPDATE);
                        break;

                     case ROLL_FORWARD:
                        remainingAmount = split.getAmount() - forecastTransaction.getRemainingAmount();
                        forecastTransaction.setRemainingAmount(0);
                        forecastTransaction.save(UPDATE);
                        ForecastTransactionIterator it = ForecastTransaction.getForecastTransactionsForBudgetItem(
                                split.getIdBudgetItem(), forecast.getId());
                        ForecastTransaction nextForecastTransaction = it.getNext();
                        if (nextForecastTransaction != null) {
                           forecastTransaction = nextForecastTransaction;
                           split.setAmount(remainingAmount);
                        }
                        break;
                  }
               }

               // If the user hasn't cancelled reconciliation of this split:
               if (split.getDisposition() != DISPUTE && split.getDisposition() != IGNORE) {

                  // Calculate the remaining amount in the forecast transaction:
                  forecastTransaction.setRemainingAmount(forecastTransaction.getRemainingAmount() - split.getAmount());
                  forecastTransaction.save(UPDATE);

                  // Save off the remaining amount so that we can inform the user later:
                  remainingAmount = forecastTransaction.getRemainingAmount();

                  // Create an overspent significant event for the import summary:
                  if (forecastTransaction.getRemainingAmount() > 0) {
                     forecast.addSignificantEvent(forecastTransaction);
                  }
               }
               break;

            case ENVELOPE:
               split.getBudgetItem().setRunningBalance(split.getBudgetItem().getRunningBalance() +
                       split.getAmount());
               split.getBudgetItem().save(UPDATE);
               remainingAmount = split.getBudgetItem().getRunningBalance();
               if (split.getBudgetItem().getRunningBalance() < 0) {
                  forecast.addSignificantEvent(forecastTransaction);
               }
               break;

            case PERIODIC:
            case VARIABLE_PERIODIC:
            case UNPLANNED:
               forecastTransaction.setRemainingAmount(0);
               forecastTransaction.save(UPDATE);
               break;
         }

         resolver.say("Deduct split amount of " + Utility.formatDollarAmount(split.getAmount()) + " from forecast " +
                 "transaction.  Date planned = " + Utility.calendarDateToStringDate(forecastTransaction.getPlannedDate()) +
                 ", Date authorized = " + Utility.calendarDateToStringDate(transaction.getAuthorizationDate()) +
                 ", Date posted = " + Utility.calendarDateToStringDate(transaction.getPostDate()) + ", Category = " +
                 forecastTransaction.getForecastItem().getCategory() + ", Payee = " +
                 forecastTransaction.getForecastItem().getPayee() + ", Budgeted Amount = " +
                 Utility.formatDollarAmount(forecastTransaction.getForecastItem().getAmount()) + ", Remaining amount = " +
                 Utility.formatDollarAmount(remainingAmount) + ".");

      } // End if the transaction isn't disputed or ignored.

      return forecastTransaction;
   }
} // End class ForecastTransaction.
