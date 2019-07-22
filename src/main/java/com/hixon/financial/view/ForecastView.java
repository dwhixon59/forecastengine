package com.hixon.financial.view;

import com.hixon.financial.model.forecast.ForecastException;
import com.hixon.financial.model.forecast.LongTermForecast;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;

// These are the forecast views in the MVC architecture for the forecast:
public interface ForecastView {

    public void setForecast (LongTermForecast forecastToRender);
    public boolean render(String filename, String encoding) throws ForecastException, FileNotFoundException, UnsupportedEncodingException;
}
