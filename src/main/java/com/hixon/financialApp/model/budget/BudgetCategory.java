package com.hixon.financialApp.model.budget;

import com.hixon.financialApp.model.entity.DependentEntity;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.ForecastException;
import org.apache.commons.lang3.NotImplementedException;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * This class represents a budget category.  Budget categories group like items in the budget like "automotive"
 * expenses.
 */
public class BudgetCategory extends DependentEntity {

    /*
     * Constants and ennumerations:
     */
    // The type of budget category:
    public enum CategoryType {
        INCOME, EXPENSE;
    }

    /*
     * Fields:
     */
    String name;
    CategoryType categoryType;

    /*
     * Getters and setters:
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public CategoryType getCategoryType() {
        return categoryType;
    }

    public void setCategoryType(CategoryType categoryType) {
        this.categoryType = categoryType;
    }

    /*
     * Constructors:
     */
    public BudgetCategory(ResultSet resultSet) throws SQLException, BudgetException {
        this.name = resultSet.getString("bi.category");
        if (Item.parseItemType(resultSet.getString("bi.itemType")) == Item.ItemType.INCOME) {
            this.categoryType = CategoryType.INCOME;
        } else {
            this.categoryType = categoryType.EXPENSE;
        }
    }

    /*
     * CRUD methods:
     */
    @Override
    public String getInsertQuery() throws BudgetException, ForecastException, EntityException, SQLException, NotImplementedException {
        return null;
    }

    @Override
    public String getInsertOnDuplicateUpdateQuery() throws BudgetException, EntityException, SQLException, ForecastException {
        return null;
    }

    @Override
    public String getUpdateByIdQuery() throws BudgetException {
        return null;
    }

    @Override
    public String getDeleteByIdQuery() {
        return null;
    }

    @Override
    public String getPrintableEntityTypeName() {
        return "Budget Category";
    }

    public boolean isIncome() {
        return Item.isIncomeCategory(name);
    }
}
