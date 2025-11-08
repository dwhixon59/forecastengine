package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.entity.MatchQuery;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.view.base.ViewInt;

import static com.hixon.financialApp.view.base.ViewInt.*;

/**
 * Controller for managing users.
 * Provides methods for selecting and managing user entities.
 */
public class UserController {

    private ViewInt view;
    private NotificationServiceInt notificationService;

    /**
     * Constructor for UserController.
     *
     * @param view The view interface for user interaction
     * @param notificationService The notification service for sending notifications
     */
    public UserController(ViewInt view, NotificationServiceInt notificationService) {
        this.view = view;
        this.notificationService = notificationService;
    }

    /**
     * Select a user from all available users.
     *
     * @return The selected User, or null if cancelled
     * @throws Exception if any error occurs
     */
    public User selectUser() throws Exception {
        SelectionController selectionController = new SelectionController(view);
        return selectionController.getByNameFullText(
                null,  // No seed name
                null,  // No scope for users (they're global)
                ALLOW_NONE,
                DO_NOT_ALLOW_CREATE,
                ALLOW_CANCEL,
                ALLOW_QUIT,
                DO_NOT_ALLOW_SKIP,
                User.getPrintableTypeName_static(),
                user -> user.getFirstName() + " " + user.getLastName() + " (" + user.getUserName() + ")",
                new MatchQuery(User.getSelectQuery() + " WHERE ", "u.userName",
                        "u.firstName, u.lastName, u.userName"),
                rs -> {
                    try {
                        return new User(rs);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                (scope, newName) -> null);  // Don't allow creating users from this interface
    }
}

