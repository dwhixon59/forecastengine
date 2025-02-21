package com.hixon.financialApp.view.base;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.Entity;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.view.ViewException;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.List;

public interface ReportRendererInt {

   void openReportOutput() throws FileNotFoundException, UnsupportedEncodingException, ViewException;

   void renderReportFrontMatter();

   void renderHeaderRow();

   List<Entity> getItems() throws Exception;

   void renderItemRow(Entity item) throws Exception;

   void renderSummaryRow() throws Exception;

   void renderReportBackMatter() throws EntityException, SQLException, ForecastException, BudgetException, Exception, RegisterException;

   void closeReportOutput();

}