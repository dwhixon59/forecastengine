package com.hixon.financialApp.view.text;

import com.hixon.financialApp.model.entity.Entity;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.view.base.AbstractForecastView;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/**
 * The ForecastReport class in the text package contains the logic common to formatting text based forecast reports.  For
 * example all text based forecast reports report on a forecast, so this class has a member variable for a {@link Forecast}.
 * Note that this is only formatting of the report.  Generating a list of items to include in the report is not specific
 * to a text based rendering of the report, so that logic is contained in the {@link AbstractForecastView} class.
 */
public class ForecastReport extends TextReport {

   protected final Forecast forecast;

   protected ForecastReport(Forecast forecast, List<Entity> items, File reportFile) throws FileNotFoundException {
      super(items, reportFile);
      this.forecast = forecast;
   }

}
