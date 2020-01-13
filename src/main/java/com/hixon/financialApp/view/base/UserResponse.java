package com.hixon.financialApp.view.base;

import com.hixon.financialApp.utility.Utility.StartDateType;
import com.hixon.financialApp.model.forecast.ForecastTransactionSplit;

import java.util.Calendar;

public class UserResponse {

   private StartDateType startDate;
   private Calendar date;

   public Calendar getDate() {return date;}
   public void setDate(Calendar date) {
      this.date = date;
   }

   // Required format of the response if it is a date:
   public enum dataType {DATE, DASH_DATE, SLASH_DATE, CSV_LINE}

   // Disposition instructions from the user:
   private ForecastTransactionSplit.SplitDisposition disposition = null;

   public ForecastTransactionSplit.SplitDisposition getDisposition() {
      return disposition;
   }

   public void setDisposition(ForecastTransactionSplit.SplitDisposition disposition) {
      this.disposition = disposition;
   }

   // A response line from the user:
   private String response = null;
   public String getResponse() {
      return response;
   }
   public void setResponse(String response) {
      this.response = response;
   }

   // What date to start the update on (today, first of next month, etc.)
   public StartDateType getStartDate() {return startDate;}
   public void setStartDate(StartDateType start) {
      this.startDate = start;
   }
}
