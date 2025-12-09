package com.hixon.financialApp.view.base;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.IndependentEntity;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.register.RegisterException;
import lombok.Data;

import java.sql.SQLException;
import java.util.*;

/**
 * This class represent a history of entities that were processed during a session.  It is a set in that there
 * will only be one instance of any entity in the list (duplicates are removed).  It is a chronological list
 * in that it can be traversed in the order that the entities were added.  In the case of addition of a duplicate, the
 * prior instance will be deleted and a new instance added to the end of the list.
 */
@Data
public class IndependentEntityHistory<T extends IndependentEntity> {

    /*
     * Fields:
     */
    // Create the entity history list:
    private List<UUID> entityIdsList = new ArrayList<>();

    // Create the entity history set to quickly determine if an entity exists in the entity list before scanning the
    // list to find it.  This is especially important when adding to the list:
    private Set<UUID> entityIdsSet = new HashSet<>();


    /*
     * Constructors:
     */


    /*
     * Helper methods:
     */

    /**
     * Get a list of Entities over a range of values in the history list.
     *
     * @param rangeStart The index of the starting entity.
     * @param rangeEnd The index of the last entity in the range.
     * @return A list of entities corresponding to the range requested.
     */
    List<T> getEntitiesInRange(int rangeStart, int rangeEnd)
            throws SQLException, EntityException, RegisterException, BudgetException, ForecastException {
        List<T> entities = new ArrayList<>();
        for (int i = rangeStart; i <= rangeEnd; i++) {
            // IndependentEntity.getById returns an IndependentEntity; cast to T is unchecked but expected
            @SuppressWarnings("unchecked")
            T item = (T) IndependentEntity.getById(entityIdsList.get(i));
            entities.add(item);
        }
        return entities;
    }


    /*
     * Main methods:
     */

    /**
     * This method adds an entity to the end of the entity history list.
     *
     * @param entity The entity to be added to the list.
     * @return The entity that was added to the list for convenience.
     */
    public T add(T entity) {
        if (entityIdsSet.contains(entity.getId())) {
            entityIdsList.remove(entity.getId());
        }
        entityIdsList.add(entity.getId());
        return entity;
    }

    /**
     * This method deletes an entity from the entity history list.
     *
     * @param entity The entity to be removed from the list.
     * @return The entity that was deleted from the list for convenience.
     */
    public T delete(T entity) {
        if (entityIdsSet.contains(entity.getId())) {
            entityIdsSet.remove(entity.getId());
            entityIdsList.remove(entity.getId());
        }
        return entity;
    }

    /**
     * This method resets (empties) the entity history list.
     */
    public void reset() {
        entityIdsSet.clear();
        entityIdsList.clear();
    }

    /**
     * This method gets the entity history list in chronological order.
     *
     * @return An entity history list.  List may be empty.
     */
    public List<T> get() throws SQLException, EntityException, RegisterException, BudgetException, ForecastException {
        return getEntitiesInRange(0, entityIdsList.size() - 1);
    }

    /**
     * This method returns a list of the most recent 'n' entities of the entity history list in chronological
     * order. If there aren't 'n' entities in the list, it returns whatever is in the list.
     *
     * @return
     */
    public List<T> getMostRecent(int numberOfEntities)
            throws SQLException, EntityException, RegisterException, BudgetException, ForecastException {
        return getEntitiesInRange(entityIdsList.size() - 1 - numberOfEntities, entityIdsList.size() - 1);
    }

    /**
     * This method gets the entity history as a list of entities of at most 'n' entries in reverse
     * chronological order.
     *
     * @return
     */
    public List<T> getMostRecentReversed(int numberOfEntities)
            throws SQLException, EntityException, RegisterException, BudgetException, ForecastException {
        List<T> recentEntities = getMostRecent(numberOfEntities);
        List<T> recentEntitiesReversed = new ArrayList<>();
        for (int i = recentEntities.size() - 1; i >= 0; i--) {
            recentEntitiesReversed.add(recentEntities.get(i));
        }
        return recentEntitiesReversed;
    }
}