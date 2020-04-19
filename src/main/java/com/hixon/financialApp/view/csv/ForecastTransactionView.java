package com.hixon.financialApp.view.csv;

import com.hixon.financialApp.model.budget.Item.HowImportant;
import com.hixon.financialApp.model.budget.Item.HowOccurs;
import com.hixon.financialApp.model.forecast.ForecastItem;
import com.hixon.financialApp.model.forecast.ForecastTransaction;

import java.util.Calendar;
import java.util.UUID;

public class ForecastTransactionView extends ForecastTransaction {

   /*
    * Fields:
    */
   double credit = 0.0;
   double debit = 0.0;
   double amount = 0.0;


   /*
    * Getters and setters:
    */
   public Calendar getDate() {
      return getPlannedDate();
   }
   public void setDate(Calendar date) {
      setPlannedDate(date);
   }

   public String getCategory() {
      return getCategory();
   }
   public void setCategory(String category) {
      forecastItem.setCategory(category);
   }

   public String getPayee() {
      return getPayee();
   }
   public void setPayee(String payee) {
      forecastItem.setPayee(payee);
   }

   public Double getCredit() {
      return credit;
   }
   public void setCredit(Double credit) {
      if (credit < 0.01) {
         this.credit = 0.0;
      } else {
         this.credit = credit;
      }
      this.setRemainingAmount(getCredit() - getDebit());
   }

   public Double getDebit() {
      return debit;
   }
   public void setDebit(Double debit) {
      if (debit < 0.01) {
         this.debit = 0.0;
      } else {
         this.debit = debit;
      }
      this.setRemainingAmount(getCredit() - getDebit());
   }

   public Double getBalance() {
      return this.getRunningBalance();
   }
   public void setBalance(Double balance) {
      setRunningBalance(balance);
   }

   public HowImportant getHowImportant() {
      return getHowImportant();
   }
   public void setHowImportant(HowImportant howImportant) {
      forecastItem.setHowImportant(howImportant);
   }

   public HowOccurs getHowOccurs() {
      return getHowOccurs();
   }
   public void setHowOccurs(HowOccurs howOccurs) {
      forecastItem.setHowOccurs(howOccurs);
   }

   public UUID getTransactionId() {
      return getId();
   }
   public void setTransactionID(UUID transactionId) {
      setId(transactionId);
   }

   public Double getAmount() {
      return amount;
   }
   public void setAmount(Double amount) {
      this.amount = amount;
   }

   // The entity name:
   @Override
   public String getPrintableEntityTypeName() {
      return "CSV forecast transaction";
   }

   /*
    * Constructors:
    */
   public ForecastTransactionView() {
      ForecastItem forecastItem = new ForecastItem();
      setForecastItem(forecastItem);
   }


   /*
    * Helper methods:
    */
   @Override
   public String toString() {
      return "ForecastTransactionView{" +
              "credit=" + credit +
              ", debit=" + debit +
              ", amount=" + amount +
              "}\n" +
              super.toString() + "\n" +
              forecastItem.toString();
   }
}
