package com.hixon.financialApp.model.entity;

import com.hixon.financialApp.model.budget.BudgetItem;

import java.util.UUID;

public class BudgetItemMatchQuery extends MatchQuery {
    private UUID budget_idBudget;

    // Constructor that takes the budget_idBudget value as a UUID
    public BudgetItemMatchQuery(UUID budget_idBudget) {
        this.budget_idBudget = budget_idBudget;
    }

    // Method to generate the query with the budget ID and name substituted in
    @Override
    public String getQuery(String name) {

        // Generate the SQL query
        return BudgetItem.getSelectQuery() +
                "WHERE `Budget_idBudget` = uuid_to_bin('" + budget_idBudget + "')" +
                "AND MATCH(`bi,category`, `bi.payee`, 'bi.memo') AGAINST ('" + escapeSQL(name) + "' IN NATURAL LANGUAGE MODE);";
    }
}

