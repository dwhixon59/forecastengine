package com.hixon.financialApp.view.csv;

import com.hixon.financialApp.model.budget.Item.HowImportant;
import com.hixon.financialApp.model.budget.Item.HowOccurs;
import com.hixon.financialApp.model.forecast.ForecastTransaction;

public class CsvForecastTransaction extends ForecastTransaction {

   /*
    * Fields:
    */
   protected String category;
   protected String payee;
   protected Double credit;
   protected Double debit;
   protected HowImportant howImportant;
   protected HowOccurs howOccurs;
   protected Double amount;

   /*
    * Getters and setters:
    */

   public String getCategory() {
      return category;
   }
   public void setCategory(String category) {
      this.category = category;
   }

   public String getPayee() {
      return payee;
   }
   public void setPayee(String payee) {
      this.payee = payee;
   }

   public Double getCredit() {
      return credit;
   }
   public void setCredit(Double credit) {
      this.credit = credit;
   }

   public Double getDebit() {
      return debit;
   }
   public void setDebit(Double debit) {
      this.debit = debit;
   }

   public HowOccurs getHowOccurs() {
      return howOccurs;
   }
   public void setHowOccurs(HowOccurs howOccurs) {
      this.howOccurs = howOccurs;
   }

   public HowImportant getHowImportant() {
      return howImportant;
   }
   public void setHowImportant(HowImportant howImportant) {
      this.howImportant = howImportant;
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

}
