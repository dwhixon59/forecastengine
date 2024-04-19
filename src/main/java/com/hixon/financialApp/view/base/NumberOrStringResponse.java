package com.hixon.financialApp.view.base;

/**
 * A class to hold the result of a user's selection from a list of entities or a search string.  This was designed for
 * use in the command line interface, where the user can select an entity from a numbered list or enter a new search
 * string to be used to regenerate the list.
 *
 */
public class NumberOrStringResponse {
    private Integer selectedIndex;
    private String searchString;
    private boolean isNumber;

    public NumberOrStringResponse(Integer selectedIndex) {
        this.selectedIndex = selectedIndex;
        this.isNumber = true;
    }

    public NumberOrStringResponse(String searchString) {
        this.searchString = searchString;
        this.isNumber = false;
    }

    public boolean isNumber() {
        return isNumber;
    }

    public Integer getSelectedIndex() {
        if (!isNumber) {
            throw new IllegalStateException("Result is a search string, not a number.");
        }
        return selectedIndex;
    }

    public String getSearchString() {
        if (isNumber) {
            throw new IllegalStateException("Result is a number, not a search string.");
        }
        return searchString;
    }

    public void setSelectedIndex(int selectedIndex) {
        this.selectedIndex = selectedIndex;
    }
}
