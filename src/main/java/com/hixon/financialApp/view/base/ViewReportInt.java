package com.hixon.financialApp.view.base;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.Entity;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.view.ViewException;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.List;

public interface ViewReportInt {

   void openReportOutput() throws FileNotFoundException, UnsupportedEncodingException, ViewException;

   void renderReportFrontMatter();

   void renderHeaderRow();

   List<Entity> getItems();

   void renderItemRow(Entity item) throws EntityException, ForecastException, SQLException, BudgetException;

   void renderSummaryRow();

   void renderReportBackMatter();

   void closeReportOutput();

}