package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.entity.IndependentEntity;
import com.hixon.financialApp.model.entity.MatchQuery;
import com.hixon.financialApp.view.base.EntityOrStringResult;
import com.hixon.financialApp.view.base.ViewInt;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.hixon.financialApp.utility.Utility.toTitleCase;

public class SelectionController {

    /*
     * Statics and constants:
     */

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
     *
     * @param <T>                 The type of the entity, extending IndependentEntity
     * @param seedName            The name provided by the user
     * @param allowNone           Allow the user to not make a choice
     * @param allowCreate         Allow the user to create a new entity
     * @param isCancelAllowed     Is the user allowed to cancel this operation?
     * @param isQuitAllowed       Is the user allowed to quit this operation?
     * @param isSkipAllowed       Is the user allowed to skip this selection?
     * @param typeName            The type name of the entity that the user is selecting
     * @param getDisplayString    A description of the entity that is suitable for displaying it to the user
     * @param matchQuery          The query to retrieve the entities from the database
     * @param rsEntityCreator     A function that creates an instance of T from a ResultSet
     * @param stringEntityCreator A function that creates an instance of T from a string
     * @return The entity that the user selected from a candidate list, or null if the user did not select an entity
     * @throws EntityException
     * @throws CancelException
     * @throws QuitException
     * @throws SkipException
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
                seedName = new String("");
            }
            if (seedName.isEmpty()) {
                seedName = view.getResponseString("Enter the name of the " + typeName + ":", false, true,
                        true, true);
            }

            // Create a new entity of type T with the name provided by the user just so that we can call methods on it
            // that are not static:
            T entity = (T) stringEntityCreator.apply(scope, toTitleCase(seedName));

            // Loop until the user selects an entity or cancels, quits, or skips the operation:
            boolean allowCreateInList;
            while (true) {

                // Each time through the loop reset the flag that allows the user to create a new entity to the value
                // that was passed in to the method:
                allowCreateInList = allowCreate && !firstTime;

                // Try to get an entity that is an exact match for the name, this won't work on the first time through
                // the loop, but it could work on later iterations if the user has entered a new name.  If we find an
                // exact match, then ask the user to confirm that it is the correct entity, and if so, return the entity:
                if (!firstTime) {
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
                if (MatchQuery.checkStringPattern(seedName)) {
                    allowCreateInList = false;
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
                        if (entity.getName().equalsIgnoreCase(seedName)) {
                            allowCreateInList = false;
                        }

                        // Loop through the result set and create an entity for each row and add the entity to the list of
                        // entities:
                        do {
                            // Create an entity for this row and add the entity to the list of entities:
                            entities.add(rsEntityCreator.apply(rs));

                            // and if this entity matches the name, then there is no sense to giving the user the option
                            // to create one with that name, so set a flag to prevent the option to create a new entity:
                            if (entities.get(entities.size() - 1).getName().equalsIgnoreCase(seedName)) {
                                allowCreateInList = false;
                            }
                        } while (rs.next());

                        // If the user is allowed to create a new entity then add the name to the list of entities:
                        if (allowCreateInList) {
                            entities.add(stringEntityCreator.apply(scope, toTitleCase(seedName)));
                        }

                        // Ask the user to select the correct entity from the list of entities:
                        EntityOrStringResult<T> entityOrStringResult = view.selectByNameFromListOrString(
                                "Select the correct " + typeName + " for " + seedName, entities,
                                getDisplayString, allowNone, allowCreateInList, isCancelAllowed, isQuitAllowed,
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
                                getDisplayString.apply(entity) + " the correct " + typeName))
                        {
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
                                seedName = view.getResponseString("Enter a new search string:", false,
                                        ViewInt.ALLOW_CANCEL, ViewInt.ALLOW_QUIT, ViewInt.ALLOW_SKIP);
                            }
                        }
                    }
                } else {
                    // Let the user know that no similar entities were found:
                    view.say("No " + typeName + " found with a name similar to " + seedName + ".");

                    // If the user is allowed to create a new entity, then ask them if they want to create one:
                    if (
                            allowCreateInList &&
                            view.getYesOrNo("Do you want to create a new " + typeName + " called " +
                                toTitleCase(seedName) + "?", ViewInt.DO_NOT_ALLOW_CANCEL, ViewInt.DO_NOT_ALLOW_QUIT,
                                ViewInt.DO_NOT_ALLOW_SKIP)
                    ) {
                        return stringEntityCreator.apply(scope, toTitleCase(seedName));
                    }

                    // Since the entity was not found and the user is not allowed to or does not want to create a new entity,
                    // then ask the user for a new search string:
                    seedName = view.getResponseString("Enter the name of the " + typeName + ":", false, true,
                            true, true);
                }
                firstTime = false;
            }
        } catch (SQLException e) {
            EntityException ee = new EntityException("Database error occurred.", e);
            throw ee;
        }
    }
}
