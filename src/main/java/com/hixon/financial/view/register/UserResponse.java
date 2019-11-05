package com.hixon.financial.view.register;

import com.hixon.financial.model.forecast.ForecastTransactionSplit;

import java.util.Calendar;

import static com.hixon.financial.model.forecast.LongTermForecast.updateStart;

public class UserResponse {

   private updateStart updateStart;
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

   void setDisposition(ForecastTransactionSplit.SplitDisposition disposition) {
      this.disposition = disposition;
   }

   // A response line from the user:
   private String response = null;
   public String getResponse() {
      return response;
   }
   void setResponse(String response) {
      this.response = response;
   }

   // What date to start the update on (today, first of next month, etc.)
   public updateStart getUpdateStart () {return updateStart;}
   public void setUpdateStart(updateStart start) {
      this.updateStart = start;
   }
}
