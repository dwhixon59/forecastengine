# Added Forecast Selection to Manage Forecast Transactions

## Date: January 15, 2026

## Problem Description

When managing forecast transactions, once a forecast was selected, there was no way to switch to a different forecast without exiting and re-entering the manage data menu. The forecast was cached in the session and reused every time.

### User Experience (Before):

```
▸ What type of entity would you like to manage?
  ...
  f - Forecast transactions
Enter your choice:  f

--- Forecast Transaction Search ---
You can search by:
  • Planned date range: ...
  • Category, payee, or memo
  • Or press Enter to see all forecast transactions

Search for forecast transaction:  
```

**Issue:** No indication which forecast is being used, and no way to change it.

## The Solution

Added the ability to change forecasts from within the forecast transaction search interface by:
1. Displaying the current forecast name
2. Adding 'F' command to select a different forecast
3. Updating the session with the new forecast

### User Experience (After):

```
--- Forecast Transaction Search ---
Current forecast: Family Budget Forecast

You can search by:
  • Planned date range: ...
  • Category, payee, or memo
  • Or press Enter to see all forecast transactions
  • Type 'F' to select a different forecast

Search for forecast transaction (date range, category, payee, memo, filters, or 'F' for different forecast):  F

▸ Select a forecast:
  1 - Family Budget Forecast
  2 - Business Budget Forecast
Enter your choice:  2

Forecast changed to: Business Budget Forecast

--- Forecast Transaction Search ---
Current forecast: Business Budget Forecast
...
```

## Implementation Details

**File:** `ForecastTransactionController.java`  
**Method:** `selectForecastTransactionFromForecast()`  
**Lines:** 247-285

### Changes Made:

1. **Display Current Forecast:**
   ```java
   view.say("Current forecast: " + forecast.getDescription());
   ```

2. **Add 'F' Option to Menu:**
   ```java
   view.say("  • Type 'F' to select a different forecast");
   ```

3. **Update Prompt:**
   ```java
   searchString = view.getResponseString(
       "Search for forecast transaction (date range, category, payee, memo, filters, or 'F' for different forecast)",
       ...
   ```

4. **Handle 'F' Command:**
   ```java
   if (searchString != null && searchString.trim().equalsIgnoreCase("F")) {
       try {
           forecast = Forecast.selectForecast(budget);
           sessionController.setForecast(forecast);
           view.say("Forecast changed to: " + forecast.getDescription());
           searchString = null; // Reset to show menu again
           continue;
       } catch (Exception e) {
           view.say("Error selecting forecast: " + e.getMessage());
           searchString = null;
           continue;
       }
   }
   ```

## Benefits

✅ **User can see which forecast they're working with** - Displayed at the top of the search menu  
✅ **Easy to switch forecasts** - Just type 'F' instead of exiting and re-entering  
✅ **Maintains workflow** - Stays in the forecast transaction management interface  
✅ **Updates session** - New forecast is saved in sessionController for other operations  
✅ **Error handling** - Gracefully handles errors during forecast selection  

## Use Cases

1. **Comparing forecasts:**
   - Search for transactions in one forecast
   - Press 'F' to switch to another forecast
   - Compare the same search criteria across different forecasts

2. **Multiple budgets:**
   - Work with transactions from Family Budget forecast
   - Switch to Business Budget forecast
   - No need to exit back to main menu

3. **Forecast updates:**
   - User realizes they're looking at the wrong forecast
   - Can immediately switch without losing their place

## How to Use

1. **From the main menu:** Select 'f' for Forecast transactions
2. **See current forecast:** Displayed at top of search menu
3. **To change forecast:** Type 'F' at the search prompt
4. **Select new forecast:** Choose from the list of available forecasts
5. **Confirmation:** "Forecast changed to: [name]" message appears
6. **Continue working:** Search menu redisplays with new forecast

## Error Handling

- If forecast selection is cancelled, returns to search menu with original forecast
- If an error occurs, shows error message and returns to search menu
- Session is only updated if forecast selection succeeds

## Files Modified

- **ForecastTransactionController.java** (lines 247-285)
  - Added current forecast display
  - Added 'F' option to menu text
  - Added 'F' command handling logic
  - Updates sessionController with new forecast

## Testing

To verify the feature:
1. Go to Manage Data → Forecast transactions
2. Note the current forecast name displayed
3. Type 'F' at the search prompt
4. Select a different forecast
5. Verify the new forecast name is displayed
6. Search for transactions to confirm they're from the new forecast

## Related Features

This feature complements:
- The forecast context management in DataManagerController
- The session management across different controllers
- The budget/forecast selection workflow

Similar functionality could be added for:
- Changing budget from budget items management
- Changing register from transaction management

