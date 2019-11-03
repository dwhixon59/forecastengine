package com.hixon.financial.model.budget;

import com.hixon.financial.Utility;
import com.hixon.financial.model.EntityException;
import com.hixon.financial.model.EntityInt;
import com.hixon.financial.model.IndependentEntity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;

import static com.hixon.financial.model.budget.Item.HowOccurs.*;
import static com.hixon.financial.model.budget.Item.HowPaid.*;
import static com.hixon.financial.model.budget.Item.ItemType.CREDIT_CARD;
import static com.hixon.financial.model.budget.Item.ItemType.*;
import static com.hixon.financial.model.budget.Item.PeriodType.*;

// This class represents an expense item.  It is used in budgets and forecasts.
public abstract class Item extends IndependentEntity {
   protected static final SimpleDateFormat sdfMDY = new SimpleDateFormat("M/dd/yyyy", Locale.ENGLISH);
   protected String category = null;
   protected String payee = null;
   protected PeriodType period;
   // Expected amount for this budget item:
   protected double amount = 0;
   // The running balance if this item is an envelope:
   protected double runningBalance = 0;
   // The first date that this item is expected to occur as a Transaction:
   protected Calendar startDate = new GregorianCalendar();
  // If this item is a a fixed number of payments (like an installment loan) this is the number of payments:
   protected int numberOfPayments = 0;
   // Last date that this ForecastItem can occur as a Transaction:
   protected Calendar endDate = null;
   protected ItemType itemType;
   protected HowImportant howImportant;
   protected HowOccurs howOccurs;
   protected HowPaid howPaid;

   public Item(boolean createId) {
      super(createId);
   }

   // How frequently this forecast item is expected to occur:
   public enum PeriodType {
      ON_DEMAND, DAILY, WEEKLY, BIWEEKLY, SEMIMONTHLY, SCHOOLYEARSEMIMONTHLY, MONTHLY, SIXWEEKS, BIMONTHLY, QUARTERLY, SEMIANNUALLY,
      ANNUALLY
   }

   // Type of expense:
   public enum ItemType {
      CELEBRATION, // "C"
      CREDIT_CARD, // "CC"
      EXPENSE, // "E"
      FEES, // "F"
      GIFT, // "G"
      INCOME, // "IN"
      INSTALLMENT_LOAN, // "IL"
      INSURANCE, // "I"
      INVESTMENTS, // "INV"
      MAINTENANCE, // "M"
      MAJOR_EXPENSE, // "ME"
      MEDICAL, // "MED"
      RENT, // "R"
      REVOLVING_CREDIT, // "RC"
      SAVINGS, // "SAV"
      SUBSCRIPTIONS // "S"
   }

   // How important is this expense:
   public enum HowImportant {
      DISCRETIONARY, // "D"
      DISCRETIONARY_ESSENTIAL, // Discretionary but essential (some control over the amount) "DE"
      ESSENTIAL, // "E"
      VARIABLE_ESSENTIAL // "VE" - Essential, but varies (like the electric bill)
   }

   // How do the occurrences of this item happen relative to the budget period for the item:
   public enum HowOccurs {
      COLLECTION, // "C" - More than once a period, like groceries (multiple trips to the store in one period).
      ENVELOPE, // "E" - Less than once a period (like car maintenance, or vacation savings).
      PERIODIC, // "P" - Once each period, on or about a particular day or date.
      UNPLANNED, // "U" - There is no budget period for this item. It happens randomly and it's not in the forecast.
      VARIABLE_PERIODIC // "VP" - Same as periodic, but the amount varies each period (like the electric bill).
   }

   // How this budget item is expected to be paid:
   public enum HowPaid {
      AUTOMATIC_DEBIT, // "AD"
      AUTOMATIC_TRANSFER, // "AT"
      BILL_PAY, // "BP"
      CASH, // "CS"
      CHECK, // "CK"
      CREDIT_CARD, // "CC"
      DEBIT_CARD, // "DC"
      DIRECT_DEPOSIT, // "DD"
      ONLINE_PAYMENT, // Manual online-payment "OP"
      RECURRING_PAYMENT, // Recurring payment "RP"
      TRANSFER // "TX"
   }


   /*
    *  Getter and setter methods:
    */

   public String getCategory() {
      return category;
   }

   public void setCategory(String category) {
      this.category = category;
      setDirty(true);
   }

   public String getPayee() {
      return payee;
   }

   public void setPayee(String payee) {
      this.payee = payee;
      setDirty(true);
   }

   public PeriodType getPeriod() {
      return period;
   }

   public void setPeriod(PeriodType period) {
      this.period = period;
      setDirty(true);
   }

   public double getAmount() {
      return amount;
   }

   public void setAmount(double amount) {
      this.amount = amount;
      setDirty(true);
   }

   public double getRunningBalance() {
      return runningBalance;
   }

   public void setRunningBalance(double runningBalance) {
      this.runningBalance = runningBalance;
      setDirty(true);
   }

   public Calendar getStartDate() {
      return startDate;
   }

   public void setStartDate(Calendar startDate) {
      this.startDate = startDate;
      setDirty(true);
   }

   public int getNumberOfPayments() {
      return numberOfPayments;
   }

   public void setNumberOfPayments(int numberOfPayments) {
      this.numberOfPayments = numberOfPayments;
      setDirty(true);
   }

   public Calendar getEndDate() {
      return endDate;
   }

   public void setEndDate(Calendar endDate) {
      this.endDate = endDate;
      setDirty(true);
   }

   public ItemType getItemType() {
      return itemType;
   }

   public void setItemType(ItemType itemType) {
      this.itemType = itemType;
      setDirty(true);
   }

   public HowImportant getHowImportant() {
      return howImportant;
   }

   public void setHowImportant(HowImportant howImportant) {
      this.howImportant = howImportant;
      setDirty(true);
   }

   public HowOccurs getHowOccurs() {
      return howOccurs;
   }

   public void setHowOccurs(HowOccurs howOccurs) {
      this.howOccurs = howOccurs;
      setDirty(true);
   }

   public HowPaid getHowPaid() {
      return howPaid;
   }

   public void setHowPaid(HowPaid howPaid) {
      this.howPaid = howPaid;
      setDirty(true);
   }


   /*
    *  Helper methods:
    */
   public static PeriodType parsePeriodType(String dbPeriod) throws BudgetException {
      PeriodType period;
      switch (dbPeriod) {
         case "On-Demand":
            period = ON_DEMAND;
            break;
         case "Daily":
            period = DAILY;
            break;
         case "Weekly":
            period = WEEKLY;
            break;
         case "Bi-Weekly":
            period = BIWEEKLY;
            break;
         case "Semi-Monthly":
            period = SEMIMONTHLY;
            break;
         case "School-Year-Semi-Monthly":
            period = SCHOOLYEARSEMIMONTHLY;
            break;
         case "Monthly":
            period = MONTHLY;
            break;
         case "Six-Weeks":
            period = SIXWEEKS;
            break;
         case "Bi-Monthly":
            period = BIMONTHLY;
            break;
         case "Quarterly":
            period = QUARTERLY;
            break;
         case "Semi-Annually":
            period = SEMIANNUALLY;
            break;
         case "Annually":
            period = ANNUALLY;
            break;
         default:
            throw new BudgetException("Invalid budget item period type:  " + dbPeriod + ".");
      }
      return period;
   }

   public static String generatePeriodType(PeriodType period) throws BudgetException {
      String dbPeriodType;
      switch (period) {
         case ON_DEMAND:
            dbPeriodType = "On-Demand";
            break;
         case DAILY:
            dbPeriodType = "Daily";
            break;
         case WEEKLY:
            dbPeriodType = "Weekly";
            break;
         case BIWEEKLY:
            dbPeriodType = "Bi-Weekly";
            break;
         case SEMIMONTHLY:
            dbPeriodType = "Semi-Monthly";
            break;
         case SCHOOLYEARSEMIMONTHLY:
            dbPeriodType = "School-Year-Semi-Monthly";
            break;
         case MONTHLY:
            dbPeriodType = "Monthly";
            break;
         case SIXWEEKS:
            dbPeriodType = "Six-Weeks";
            break;
         case BIMONTHLY:
            dbPeriodType = "Bi-Monthly";
            break;
         case QUARTERLY:
            dbPeriodType = "Quarterly";
            break;
         case ANNUALLY:
            dbPeriodType = "Annually";
            break;
         case SEMIANNUALLY:
            dbPeriodType = "Semi-Annually";
            break;
         default:
            throw new BudgetException("Invalid budget item period type:  " + period + ".");
      }
      return dbPeriodType;
   }

   public static ItemType parseItemType(String dbtype) throws BudgetException {
      ItemType type;
      switch (dbtype) {
         case "C":
            type = CELEBRATION;
            break;
         case "CC":
            type = CREDIT_CARD;
            break;
         case "E":
            type = EXPENSE;
            break;
         case "F":
            type = FEES;
            break;
         case "G":
            type = GIFT;
            break;
         case "IN":
            type = INCOME;
            break;
         case "IL":
            type = INSTALLMENT_LOAN;
            break;
         case "I":
            type = INSURANCE;
            break;
         case "INV":
            type = INVESTMENTS;
            break;
         case "M":
            type = MAINTENANCE;
            break;
         case "ME":
            type = MAJOR_EXPENSE;
            break;
         case "MED":
            type = MEDICAL;
            break;
         case "R":
            type = RENT;
            break;
         case "RC":
            type = REVOLVING_CREDIT;
            break;
         case "SAV":
            type = SAVINGS;
            break;
         case "S":
            type = SUBSCRIPTIONS;
            break;
         default:
            throw new BudgetException("Invalid item type: " + dbtype + ".");
      }
      return type;
   }

   public static String generateItemType(ItemType type) throws BudgetException {
      String dbtype;
      switch (type) {
         case CELEBRATION:
            dbtype = "C";
            break;
         case CREDIT_CARD:
            dbtype = "CC";
            break;
         case EXPENSE:
            dbtype = "E";
            break;
         case FEES:
            dbtype = "F";
            break;
         case GIFT:
            dbtype = "G";
            break;
         case INCOME:
            dbtype = "IN";
            break;
         case INSTALLMENT_LOAN:
            dbtype = "IL";
            break;
         case INSURANCE:
            dbtype = "I";
            break;
         case INVESTMENTS:
            dbtype = "INV";
            break;
         case MAINTENANCE:
            dbtype = "M";
            break;
         case MAJOR_EXPENSE:
            dbtype = "ME";
            break;
         case MEDICAL:
            dbtype = "MED";
            break;
         case RENT:
            dbtype = "R";
            break;
         case REVOLVING_CREDIT:
            dbtype = "RC";
            break;
         case SAVINGS:
            dbtype = "SAV";
            break;
         case SUBSCRIPTIONS:
            dbtype = "S";
            break;
         default:
            throw new BudgetException("Invalid item type: " + type + ".");
      }
      return dbtype;
   }

   public static HowImportant parseHowImportant(String dbHowImportant) throws BudgetException {
      HowImportant howImportant;
      switch (dbHowImportant) {
         case "D":
            howImportant = HowImportant.DISCRETIONARY;
            break;
         case "DE":
            howImportant = HowImportant.DISCRETIONARY_ESSENTIAL;
            break;
         case "E":
            howImportant = HowImportant.ESSENTIAL;
            break;
         case "VE":
            howImportant = HowImportant.VARIABLE_ESSENTIAL;
            break;
        default:
            throw new BudgetException("Invalid item howImportant:  " + dbHowImportant + ".");
      }
      return howImportant;
   }

   // How important is this expense:
   public static String generateHowImportant(HowImportant howImportant) throws BudgetException {
      String dbHowImportant;
      switch (howImportant) {
         case DISCRETIONARY:
            dbHowImportant = "D";
            break;
         case DISCRETIONARY_ESSENTIAL:
            dbHowImportant = "DE";
            break;
         case ESSENTIAL:
            dbHowImportant = "E";
            break;
         case VARIABLE_ESSENTIAL:
            dbHowImportant = "VE";
            break;
         default:
            throw new BudgetException("Invalid item howPaid:  " + howImportant + ".");
      }
      return dbHowImportant;
   }

   // How do the occurrences of this item happen relative to the budget period for the item:
   public static HowOccurs parseHowOccurs(String dbHowOccurs) throws BudgetException {
      HowOccurs howOccurs;
      switch (dbHowOccurs) {
         case "C":
            howOccurs = COLLECTION;
            break;
         case "E":
            howOccurs = ENVELOPE;
            break;
         case "P":
            howOccurs = PERIODIC;
            break;
         case "U":
            howOccurs = UNPLANNED;
            break;
         case "VP":
            howOccurs = VARIABLE_PERIODIC;
            break;
         default:
            throw new BudgetException("Invalid item howPaid:  " + dbHowOccurs + ".");
      }
      return howOccurs;
   }

   public static String generateHowOccurs(HowOccurs howOccurs) throws BudgetException {
      String dbHowOccurs;
      switch (howOccurs) {
         case COLLECTION:
            dbHowOccurs = "C";
            break;
         case ENVELOPE:
            dbHowOccurs = "E";
            break;
         case PERIODIC:
            dbHowOccurs = "P";
            break;
         case UNPLANNED:
            dbHowOccurs = "U";
            break;
         case VARIABLE_PERIODIC:
            dbHowOccurs = "VP";
            break;
         default:
            throw new BudgetException("Invalid item howPaid:  " + howOccurs + ".");
      }
      return dbHowOccurs;
   }

   public static HowPaid parseHowPaid(String dbHowPaid) throws BudgetException {
      HowPaid howPaid;
      switch (dbHowPaid) {
         case "AD":
            howPaid = AUTOMATIC_DEBIT;
            break;
         case "AT":
            howPaid = AUTOMATIC_TRANSFER;
            break;
         case "BP":
            howPaid = BILL_PAY;
            break;
         case "CS":
            howPaid = CASH;
            break;
         case "CK":
            howPaid = CHECK;
            break;
         case "CC":
            howPaid = HowPaid.CREDIT_CARD;
            break;
         case "DC":
            howPaid = DEBIT_CARD;
            break;
         case "DD":
            howPaid = DIRECT_DEPOSIT;
            break;
         case "OP":
            howPaid = ONLINE_PAYMENT;
            break;
         case "RP":
            howPaid = RECURRING_PAYMENT;
            break;
         case "TX":
            howPaid = TRANSFER;
            break;
         default:
            throw new BudgetException("Invalid item howPaid:  " + dbHowPaid + ".");
      }
      return howPaid;
   }

   public static String generateHowPaid(HowPaid howPaid) throws BudgetException {
      String dbHowPaid;
      switch (howPaid) {
         case AUTOMATIC_DEBIT:
            dbHowPaid = "AD";
            break;
         case AUTOMATIC_TRANSFER:
            dbHowPaid = "AT";
            break;
         case BILL_PAY:
            dbHowPaid = "BP";
            break;
         case CASH:
            dbHowPaid = "CS";
            break;
         case CHECK:
            dbHowPaid = "CK";
            break;
         case CREDIT_CARD:
            dbHowPaid = "CC";
            break;
         case DEBIT_CARD:
            dbHowPaid = "DC";
            break;
         case DIRECT_DEPOSIT:
            dbHowPaid = "DD";
            break;
         case ONLINE_PAYMENT:
            dbHowPaid = "OP";
            break;
         case RECURRING_PAYMENT:
            dbHowPaid = "RP";
            break;
         case TRANSFER:
            dbHowPaid = "TX";
            break;
         default:
            throw new BudgetException("Invalid item howPaid:  " + howPaid + ".");
      }
      return dbHowPaid;
   }

   public static int getItemCount() throws SQLException, EntityException {
      // Find out how many budget items there are:
      ResultSet rs = EntityInt.getSingletonRS("select count(*) from forecastdatabase.budgetItem",
              "Database error attempting to retrieve a list of items in the budget.");
      try {
         rs.next();
         return rs.getInt(1);
      } catch (SQLException e) {
         System.out.println("Database error encountered trying to get the count of budget items.");
         throw e;
      }
   }

   // Determine if a given number of days of variance between the planned and actual dates of occurrence of an item of
   // this type is OK:
   public boolean withinNormalDateVariance(int variance) {
      boolean isOk = false;

      switch (period) {
         case DAILY:
            isOk = false;
            break;
         case WEEKLY:
            isOk = variance > -2 && variance < 2;
            break;
         case BIWEEKLY:
            isOk = variance > -3 && variance < 3;
            break;
         case SEMIMONTHLY:
         case SCHOOLYEARSEMIMONTHLY:
         case MONTHLY:
         case SIXWEEKS:
            isOk = variance > -4 && variance < 4;
            break;
         case BIMONTHLY:
         case QUARTERLY:
         case SEMIANNUALLY:
         case ANNUALLY:
            isOk = variance > -8 && variance < 8;
            break;
         case ON_DEMAND:
            isOk = variance > -8 && variance < 8;
      }
      return isOk;
   }

   // Determine if a given amount of variance between the planned and actual amounts of an occurrence of an item of
   // a particular type is OK:
   public boolean withinNormalAmountVariance(double variance) {
      boolean isOk = false;
      if (variance != 0.0) {
         switch (howOccurs) {
            case COLLECTION:  // Collection and envelope categories have no expectation as the amount of a single
            case ENVELOPE:    // transaction.  Their expectation is as to the total amount spent in a period.
            case UNPLANNED:   // Unplanned transactions have no expectation as to a particular amount either.
               isOk = true;
               break;

            case PERIODIC:  // Periodic and unplanned transactions can vary no more than 5%
               double acceptableVariance;
               if (itemType == INSTALLMENT_LOAN)
                  acceptableVariance = .02;
               else
                  acceptableVariance = .05;
               isOk = Math.abs(variance) < Math.abs(amount * acceptableVariance);
               break;

            case VARIABLE_PERIODIC:  // Variable periodic transactions can vary from half to double the amount:
               double transactionAmount = Math.abs(amount) + variance;
               isOk = (transactionAmount > (Math.abs(amount) / 2)) && (transactionAmount < (Math.abs(amount) * 2));
               break;
         }
      } else {
         isOk = true;
      }
      return isOk;
   }

   // Print out an item:
   public String toString() {

      String endDate = null;
      if (this.endDate != null) {
         endDate = Utility.calendarDateToStringDate(this.endDate);
      } else {
         endDate = "null";
      }
      String line = "Item: " + id + ", category = " + category + ", payee = " + payee +
              ", period = " + period + ", amount = " + amount + ", running balance = " + runningBalance +
              ", start date = " + Utility.calendarDateToStringDate(startDate) + " number of payments = " +
              numberOfPayments + ", end date = " + endDate + ", item type = " + itemType + ", how important = " +
              howImportant + ", how occurs = " + howOccurs +", how paid = " + howPaid + ".";
      return line;
   }
}
