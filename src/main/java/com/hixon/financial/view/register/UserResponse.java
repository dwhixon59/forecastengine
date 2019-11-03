package com.hixon.financial.view.register;

import com.hixon.financial.model.forecast.ForecastTransactionSplit;

public class UserResponse {

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

   // Required data type of the response:
   public enum dataType {DATE, DASH_DATE, SLASH_DATE, CSV_LINE}
}
