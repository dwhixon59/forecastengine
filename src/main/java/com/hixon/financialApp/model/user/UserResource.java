package com.hixon.financialApp.model.user;

import java.io.File;

public class UserResource {

    /*
     * This class represents an association between a user and file based resource:
     */
    private User user;

    public UserResource(User user, ResourceType resourceType, File file) {
        setUser(user);
        setResourceType(resourceType);
        setFile(file);
    }

    public enum ResourceType {NewTransactionSummaryReport, upcomingItemsReport, overdueItemsReport, ItemsOfInterestReport,
        EnvelopeReport, BudgetReport, RegisterReport, MerchantReport, TransactionReport, UserReport}
    private ResourceType resourceType;
    private File file;

    public User getUser() {
        return user;
    }
    public void setUser(User user) {
        this.user = user;
    }

    public ResourceType getResourceType() {
        return resourceType;
    }
    public void setResourceType(ResourceType resourceType) {
        this.resourceType = resourceType;
    }

    public File getFile() {
        return file;
    }
    public void setFile(File file) {
        this.file = file;
    }
}
