package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.entity.IndependentEntity;
import com.hixon.financialApp.model.entity.MatchQuery;
import com.hixon.financialApp.view.base.EntityOrStringResult;
import com.hixon.financialApp.view.base.ViewInt;

import java.io.IOException;
import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.hixon.financialApp.utility.Utility.toTitleCase;

public class SelectionController {

    /*
     * Statics and constants:
     */
    // Help text properties loaded from file
    private static final Properties helpText = new Properties();

    static {
        try (InputStream input = SelectionController.class.getClassLoader()
                .getResourceAsStream("help-text.properties")) {
            if (input == null) {
                throw new RuntimeException("Unable to find help-text.properties");
            }
            helpText.load(input);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to load help text properties", ex);
        }
    }

    /*
     * Member variables:
     */
    protected ViewInt view;

    public SelectionController(ViewInt view) {
        this.view = view;
    }

    /*
     * Main methods for SelectionController:
     */

    /**
     * Get an entity from the user that is taken from a list of entities retrieved using a full text search on a name
     * that the user provided. The starting name can be passed in, and if it was passed in, then the search will begin
     * with that name.  If not, then the user will be prompted for a name before the searching begins. This version
     * allows the caller to specify which of the usual escape exceptions (cancel, quit, and/or skip) they want to be
     * active for the user.
     * <p>
     * <b>Search Behavior:</b>
     * <ul>
     *   <li>Pressing Enter without typing anything (or entering "*") will display all entities in alphabetical order</li>
     *   <li>Entering a search term will perform a full-text search and display matching results by relevance</li>
     *   <li>Users can enter a new search string from the results list to refine their search</li>
     * </ul>
     *
     * @param <T>                 The type of the entity, extending IndependentEntity
     * @param seedName            The name provided by the user (can be null or empty to prompt for search)
     * @param scope               The scope entity (e.g., Budget) that constrains the search
     * @param allowNone           Allow the user to not make a choice
     * @param allowCreate         Allow the user to create a new entity
     * @param isCancelAllowed     Is the user allowed to cancel this operation?
     * @param isQuitAllowed       Is the user allowed to quit this operation?
     * @param isSkipAllowed       Is the user allowed to skip this selection?
     * @param typeName            The type name of the entity that the user is selecting
     * @param getDisplayString    A function that returns a description of the entity suitable for displaying to the user
     * @param matchQuery          The query to retrieve the entities from the database using full-text search
     * @param rsEntityCreator     A function that creates an instance of T from a ResultSet
     * @param stringEntityCreator A function that creates an instance of T from a string
     * @return The entity that the user selected from a candidate list, or null if the user did not select an entity
     * @throws EntityException if a database error occurs
     * @throws CancelException if the user cancels the operation
     * @throws QuitException if the user quits the operation
     * @throws SkipException if the user skips the operation
     */
    public <T extends IndependentEntity> T getByNameFullText(
            String seedName,
            IndependentEntity scope,
            boolean allowNone,
            boolean allowCreate,
            boolean isCancelAllowed,
            boolean isQuitAllowed,
            boolean isSkipAllowed,
            String typeName,
            Function<T, String> getDisplayString,
            MatchQuery matchQuery,
            Function<ResultSet, T> rsEntityCreator,
            BiFunction<IndependentEntity, String, T> stringEntityCreator)
            throws EntityException, CancelException, QuitException, SkipException {
        try {
            boolean firstTime = true;

            // If a starting value for name was not passed in, then ask the user for a name:
            if (seedName == null) {
                seedName = "";
            }
            if (seedName.isEmpty()) {
                String searchPrompt = "Search for " + typeName + " (by " + matchQuery.getSearchableFieldsDescription() + ")";
                seedName = view.getResponseString(searchPrompt, (String) null, ViewInt.ALLOW_NONE,
                        ViewInt.DO_NOT_SHOW_CANCEL_QUIT_SKIP, ViewInt.ALLOW_CANCEL, ViewInt.ALLOW_QUIT,
                        ViewInt.DO_NOT_ALLOW_SKIP, () -> getSearchHelpText(typeName));
            }

            // Create a new entity of type T with the name provided by the user just so that we can call methods on it
            // that are not static:
            T entity = (T) stringEntityCreator.apply(scope, toTitleCase(seedName));

            // Loop until the user selects an entity or cancels, quits, or skips the operation:
            boolean allowCreateInList = allowCreate;
            while (true) {

                // Try to get an entity that is an exact match for the name, this won't work on the first time through
                // the loop, but it could work on later iterations if the user has entered a new name.  If we find an
                // exact match, then ask the user to confirm that it is the correct entity, and if so, return the entity:
                // Note: Only attempt this if entity is not null (stringEntityCreator may return null when creation is not supported)
                if (!firstTime && entity != null) {
                    boolean loaded = entity.loadByName(scope, seedName);
                    if (loaded) {
                        if (view.getYesOrNo(typeName + " with the name " + entity.getName() + " found.  " +
                                "Is " + getDisplayString.apply(entity) + " the correct " + typeName)) {
                            return entity;
                        }
                    }
                }

                // No exact match was found, so try to get a list of entities that are similar to the name:
                ResultSet rs = EntityInt.getRS(matchQuery.getQuery(seedName), "trying to get the " + typeName +
                        " with the name like " + toTitleCase(seedName));

                // If the name contains wildcards, then the name is not suitable for creating a new entity, so turn
                // off the flag that allows the user to create a new entity:
                boolean suitableNameForCreate = true;
                if (MatchQuery.checkStringPattern(seedName)) {
                    suitableNameForCreate = false;
                }

                // If there is at least one similar entity found:
                if (rs.next()) {

                    // Create an entity for this row:
                    entity = rsEntityCreator.apply(rs);

                    // If there are more rows in the result set:
                    if (rs.next()) {
                        List<T> entities = new ArrayList<>();
                        entities.add(entity);

                        // and if this entity matches the name, then there is no sense to giving the user the option
                        // to create one with that name, so set a flag to prevent the option to create a new entity:
                        boolean alreadyInLIst = false;
                        if (entity.getName().equalsIgnoreCase(seedName)) {
                            alreadyInLIst = true;
                        }

                        // Loop through the result set and create an entity for each row and add the entity to the list of
                        // entities:
                        do {
                            // Create an entity for this row and add the entity to the list of entities:
                            // The rsEntityCreator.apply(rs) returns a T, but some callers use BudgetItem.createFromResultSet
                            // which returns a BudgetItem; narrow unchecked cast suppressed here.
                            @SuppressWarnings("unchecked")
                            T nextEntity = (T) rsEntityCreator.apply(rs);
                            entities.add(nextEntity);

                            // and if this entity matches the name, then there is no sense to giving the user the option
                            // to create one with that name, so set a flag to prevent the option to create a new entity:
                            if (entities.get(entities.size() - 1).getName().equalsIgnoreCase(seedName)) {
                                alreadyInLIst = true;
                            }
                        } while (rs.next());

                        // The first time through, we don't have a potential entity name, we have a seed name.  Since it
                        // is not an entity, we don't want to add it to the list of entities and give the user the option
                        // to create one with that name.  For subsequent iterations of the selection, the user should
                        // have typed in an entity name on the last iteration, so add it to the list if they are allowed
                        // to create new ones and it isn't already in the list:
                        if (allowCreateInList && !firstTime && !alreadyInLIst && suitableNameForCreate) {
                            entities.add(stringEntityCreator.apply(scope, toTitleCase(seedName)));
                        }

                        // Ask the user to select the correct entity from the list of entities:
                        // Always allow entering a new search string when showing search results
                        String selectionPrompt = "Select the correct " + typeName + " for \"" + seedName + "\"" +
                                (allowCreate ? " (or enter a new search string)" : "");
                        EntityOrStringResult<T> entityOrStringResult = view.selectByNameFromListOrString(
                                selectionPrompt, entities,
                                getDisplayString, allowNone, allowCreate, isCancelAllowed, isQuitAllowed,
                                isSkipAllowed);

                        // If the user selected an entity from the list, then return the entity:
                        if (entityOrStringResult.isEntitySelected()) {
                            return (T) entityOrStringResult.getSelectedEntity();
                        } else {
                            // else get a new search string:
                            seedName = entityOrStringResult.getSearchString();
                        }
                    } else {
                        // If there is only one similar entity found, then ask the user if it is the correct entity:
                        if (view.getYesOrNo("Only one similar " + typeName + " found.  Is " +
                                getDisplayString.apply(entity) + " the correct " + typeName)) {
                            return entity;
                        } else {
                            // If the entity is an exact match, then there is no sense to giving the user the option
                            // to create one with that name, so set a flag to prevent the option to create a new entity:
                            if (entity.getName().equalsIgnoreCase(seedName)) {
                                allowCreateInList = false;
                            }

                            // It's not the correct entity, so ask the user if they want to create a new entity:
                            if (
                                    allowCreateInList &&
                                            view.getYesOrNo("Do you want to create a new " + typeName +
                                                    " called " + toTitleCase(seedName) + "?")
                            ) {
                                return stringEntityCreator.apply(scope, toTitleCase(seedName));
                            } else {
                                // Since the entity was not found and the user is not allowed to or does not want to
                                // create a new entity, then ask the user for a new search string:
                                seedName = view.getResponseString("Enter a new search string", (String) null, false, false,
                                        ViewInt.ALLOW_CANCEL, ViewInt.ALLOW_QUIT, ViewInt.ALLOW_SKIP, () -> getSearchHelpText(typeName));
                            }
                        }
                    }
                } else {
                    // Let the user know that no similar entities were found:
                    view.say("No " + typeName + " found with a name similar to " + seedName + ".");

                    // If the user is allowed to create a new entity, ask — but default to "y" so that pressing
                    // Enter is sufficient when the user typed the full name they intended.  Typing "n" lets the
                    // user enter a new search string instead (e.g., when they typed a short browse term).
                    if (allowCreateInList) {
                        String confirm = view.getResponseString(
                                "Create a new " + typeName + " called '" + toTitleCase(seedName) + "'? (y/n):",
                                "y", ViewInt.ALLOW_NONE, ViewInt.DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                                ViewInt.ALLOW_CANCEL, ViewInt.ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP, null);
                        if (confirm.equalsIgnoreCase("y")) {
                            return stringEntityCreator.apply(scope, toTitleCase(seedName));
                        }
                    }

                    // Since the entity was not found and the user is not allowed to or does not want to create a new entity,
                    // then ask the user for a new search string:
                    String searchPrompt = "Search for " + typeName + " (by " + matchQuery.getSearchableFieldsDescription() + ")";
                    seedName = view.getResponseString(searchPrompt, (String) null, false, false, true,
                            true, true, () -> getSearchHelpText(typeName));
                }
                firstTime = false;
            }
        } catch (SQLException e) {
            EntityException ee = new EntityException("Database error occurred.", e);
            throw ee;
        }
    }

    /**
     * Generates help text explaining the various search options and prefixes available for entity searches.
     *
     * @param typeName The type of entity being searched (e.g., "Budget Item", "Merchant")
     * @return Formatted help text explaining all search options
     */
    private String getSearchHelpText(String typeName) {
        String helpContent = helpText.getProperty("search.help", "No help available");
        return "Search Help for " + typeName + ":\n\n" + helpContent;
    }

    /**
     * Retrieves all entities of a given type for the provided scope (e.g., all budget items for a budget).
     *
     * @param <T>             The type of the entity, extending IndependentEntity
     * @param scope           The scope (e.g., Budget) to filter entities
     * @param isCancelAllowed Is the user allowed to cancel this operation?
     * @param isQuitAllowed   Is the user allowed to quit this operation?
     * @return List of entities of type T
     * @throws EntityException if a database error occurs
     */
    public <T extends IndependentEntity> List<T> getAll(
            IndependentEntity scope,
            boolean isCancelAllowed,
            boolean isQuitAllowed
    ) throws EntityException {
        List<T> entities = new ArrayList<>();
        try {
            // For BudgetItem, get all items for the budget
            if (scope instanceof Budget) {
                String query = BudgetItem.getSelectQuery() + " WHERE bi.Budget_idBudget = uuid_to_bin('" + scope.getId() + "')";
                ResultSet rs = EntityInt.getRS(query, "getting all budget items for budget");
                while (rs.next()) {
                    // BudgetItem.createFromResultSet returns a BudgetItem; cast to T is unchecked but expected here
                    @SuppressWarnings("unchecked")
                    T entity = (T) BudgetItem.createFromResultSet(rs);
                    entities.add(entity);
                }
            }
            // Add other entity types as needed
            return entities;
        } catch (SQLException e) {
            throw new EntityException("Database error occurred.", e);
        }
    }
}