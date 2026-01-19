# Excel Forecast Edit Feature

## Summary
Added functionality to automatically open the rendered long-term forecast in Excel for review during the daily update process. The application will wait for the user to close Excel before continuing with the remaining update steps.

## Changes Made

### 1. Added `editLongTermForecast()` Method to Interface and Implementations

#### ForecastViewInt.java
- Added new method signature: `void editLongTermForecast() throws Exception;`
- This method is called after rendering the long-term forecast to open it in Excel

#### AbstractForecastView.java
- Added abstract method declaration: `public abstract void editLongTermForecast() throws Exception;`

#### ExcelForecastView.java
- **Implemented the method** to:
  - Check if the `longTermForecastFilename` field is set
  - Verify the file exists
  - Open Excel synchronously using: `cmd /c start /wait excel "<filename>"`
  - Wait for Excel to close before continuing
  - Handle errors and throw `ViewException` if issues occur

#### CsvForecastView.java, ForecastView.java (text), SpreadsheetXmlForecastView.java
- Added stub implementations that throw `ViewException` indicating that editing is not supported for these view types
- Only the Excel view supports the edit functionality

### 2. Updated DailyUpdateController.java

Added a new step in the daily update process after rendering the forecast:

```java
// Open the forecast in Excel for review:
view.sayH2("OPEN FORECAST IN EXCEL FOR REVIEW");
try {
    sessionController.getForecastView().editLongTermForecast();
} catch (QuitException qe) {
    throw qe;
} catch (Exception e) {
    if (!view.askContinue("\nThe error '" + e + "' occurred while opening the forecast in Excel.")) {
        throw e;
    }
    view.say("Skipped opening forecast in Excel.");
}
```

## How It Works

1. During the daily update process, after the long-term forecast is rendered to an Excel file
2. The system displays "OPEN FORECAST IN EXCEL FOR REVIEW"
3. The `editLongTermForecast()` method is called on the forecast view
4. For ExcelForecastView:
   - Excel opens with the forecast file
   - The process waits (blocks) until Excel is closed
   - Once Excel is closed, the daily update process continues
5. If an error occurs, the user is prompted whether to skip this step and continue
6. The user can review and potentially edit the forecast in Excel before continuing

## Benefits

- **User Review**: Users can review the forecast immediately after it's generated
- **Synchronous**: The process waits for Excel to close, preventing the user from accidentally continuing before review
- **Error Handling**: Graceful error handling allows users to skip if Excel can't be opened
- **View-Specific**: Only implemented for Excel view, which is appropriate for this feature

## Technical Notes

- The Excel file path is stored in the `longTermForecastFilename` field of `ExcelForecastView`
- This field is set during construction based on the forecast description
- The Windows command `cmd /c start /wait excel "<file>"` ensures synchronous execution
- The `Process.waitFor()` method blocks until Excel exits
- Error handling follows the same pattern as other daily update steps

## Testing Recommendations

1. Test the daily update process with the Bill Pay Dave account
2. Verify Excel opens with the correct forecast file
3. Make changes in Excel (if desired) and close it
4. Verify the daily update process continues after Excel closes
5. Test error handling by renaming/deleting the forecast file before the step
6. Verify the user can skip the step if needed

## Future Enhancements

Potential improvements for the future:
- Allow users to opt-out of automatic Excel opening
- Support for other spreadsheet applications (LibreOffice, etc.)
- Option to open in read-only mode
- Auto-import changes made in Excel back to the database
