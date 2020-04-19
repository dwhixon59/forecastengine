package com.hixon.financialApp.view.csv;

import com.hixon.financialApp.model.budget.Item.HowImportant;
import com.hixon.financialApp.model.budget.Item.HowOccurs;
import com.hixon.financialApp.model.forecast.ForecastItem;
import com.hixon.financialApp.model.forecast.ForecastTransaction;

import java.util.Calendar;
import java.util.UUID;

public class ViewForecastTransaction extends ForecastTransaction {

   /*
    * Fields:
    */


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
      return getCredit();
   }
   public void setCredit(Double credit) {
      if (credit < 0.01) {
         setCredit(0.0);
      } else {
         setCredit(credit);
      }
      setRemainingAmount(getCredit() - getDebit());
   }

   public Double getDebit() {
      return getDebit();
   }
   public void setDebit(Double debit) {
      if (debit < 0.01) {
         setDebit(0.0);
      } else {
         setDebit(debit);
      }
      setRemainingAmount(getCredit() - getDebit());
   }

   public Double getBalance() {
      return getRunningBalance();
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
      return getRemainingAmount();
   }
   public void setAmount(Double amount) {
      setRemainingAmount(amount);
   }

   // The entity name:
   @Override
   public String getPrintableEntityTypeName() {
      return "CSV forecast transaction";
   }

   /*
    * Constructors:
    */
   public ViewForecastTransaction() {
      ForecastItem forecastItem = new ForecastItem();
      setForecastItem(forecastItem);
   }
}
