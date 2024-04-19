package com.hixon.financialApp.view.base;

import com.hixon.financialApp.model.entity.EntityInt;

public class EntityOrStringResult<T extends EntityInt> {
    private T selectedEntity;
    private boolean createdNewEntity;
    private String searchString;
    private boolean isEntitySelected;

    // Default constructor: no entity selected, no search string
    public EntityOrStringResult() {
        this.selectedEntity = null;
        this.isEntitySelected = true;
        this.searchString = null;
        this.createdNewEntity = false;
    }

    // Constructor for entity selection
    public EntityOrStringResult(T selectedEntity) {
        this.selectedEntity = selectedEntity;
        this.isEntitySelected = true;
        this.createdNewEntity = false;
    }

    // Constructor for search string
    public EntityOrStringResult(String searchString) {
        this.searchString = searchString;
        this.isEntitySelected = false;
        this.createdNewEntity = false;
    }

    public boolean isEntitySelected() {
        return isEntitySelected;
    }

    public T getSelectedEntity() {
        if (!isEntitySelected) {
            throw new IllegalStateException("No entity selected, search string was provided instead.");
        }
        return selectedEntity;
    }

    public String getSearchString() {
        if (isEntitySelected) {
            throw new IllegalStateException("Entity selected, no search string available.");
        }
        return searchString;
    }

    public  boolean isEntityCreated() {
        return createdNewEntity;
    }

    public void setEntityCreated(boolean createdNewEntity) {
        this.createdNewEntity = createdNewEntity;
    }
}