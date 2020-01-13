package com.hixon.financialApp.view.csv;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.view.base.ForecastView;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.Calendar;

public class CsvForecastView extends ForecastView {


   /*
    * Helper methods:
    */


   /*
    * Main methods:
    */
   @Override
   public void openLongTermForecastOutput() throws FileNotFoundException, UnsupportedEncodingException {

   }

   @Override
   protected void renderLongTermForecastFrontMatter() {

   }

   @Override
   public void renderMonthHeader(Calendar plannedDate) {

   }

   @Override
   public void renderForecastTransaction(ForecastTransaction forecastTransaction, int credit, int debit) throws EntityException,
           SQLException, ForecastException, BudgetException {

   }

   @Override
   protected void renderLongTermForecastBackMatter() {

   }

   @Override
   protected void closeLongTermForecastOutput() {

   }
}
