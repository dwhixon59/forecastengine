package com.hixon.financialApp.notification.async.file;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.BudgetItemMerchant;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.register.Merchant;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.model.user.UserResource;
import com.hixon.financialApp.notification.async.NotificationServiceException;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.ViewException;
import com.hixon.financialApp.view.text.ForecastView;
import com.hixon.financialApp.view.text.RegisterView;

import java.io.*;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.List;

import static com.hixon.financialApp.utility.Utility.getResolver;

/**
 * The file based notification service performs "notification" by putting files created by underlying services in the
 * correct place for the target user and performs management functions like versioning and deleting old instances.
 */
public class fileBasedNotificationService implements NotificationServiceInt {

    /*
     * Fields:
     */
    private static final String NOTIFICATION_FILE_PREFIX = "C:\\Users\\dwhix\\Dropbox\\Hixon Family Personal Business" +
            "\\Finances\\Expenses\\";
    public static final String NOTIFICATION_FILE_POSTFIX = "_Notifications.txt";
    private static final String ENCODING = "UTF-8";
    public static final String OVERDUE_AND_UPCOMING_ITEMS_REPORT = "OverdueAndUpcomingItemsReport.txt";
    public static final String NEW_TRANSACTION_SUMMARY_REPORT = "NewTransactionSummaryReport.txt";


    /*
     * Getters and setters:
     */
    private String getNotificationFilename(User user) {
        return NOTIFICATION_FILE_PREFIX + user.getFirstName() + NOTIFICATION_FILE_POSTFIX;
    }

    /*
     * Helper methods:
     */
    /**
     * Copy a file to a users personal file system, renaming it in the process if required and versioning any previous
     * such file.
     *
     * @param user The user to send the file to.
     * @param file The file to send to the user.  May be null in which case we only version the old file.
     * @param targetFilename A new name for the file.  May be null in which case the existing filename will be used.
     * @throws IOException If any error occurs manipulating the files.
     * @throws NotificationServiceException If both the file and target filename are null.
     */
    private void sendFileToUser(User user, File file, String targetFilename) throws IOException, NotificationServiceException {

        // Either the file to be sent, or target filename must not be null:
        if (file != null || targetFilename != null) {

            // If the caller provided a new name for the file, then use that while copying it.  Otherwise use the current
            // filename:
            String filename;
            if (targetFilename != null) {
                filename = targetFilename;
            } else {
                filename = file.getName();
            }

            // Version the previous report on the user's personal file system:
            Utility.versionUserFile(user, filename);

            // If a file was provided, then copy it to the user's filesystem:
            if (file != null) {
                // Copy the new file to the user's personal file system:
                Utility.copyToUsersFileSystem(user, file, filename);

                // Log the results:
                getResolver().say("File " + file.getName() + " was written to the file " + filename + " on the users personal " +
                        "file system " + user.getPersonalFileSystem());
            } else {
                // Log the results:
                getResolver().say("File " + targetFilename + " was versioned on the users personal " +
                        "file system " + user.getPersonalFileSystem());
            }
        } else {
            throw new NotificationServiceException("To send a file to the user, you must specify the file, the target " +
                    "filename, or both.");
        }
    }


    /*
     * Main methods:
     */
    @Override
    public void requestIdentifyMerchant(User user, Transaction transaction) throws FileNotFoundException,
            UnsupportedEncodingException {

        try (PrintWriter writer = new PrintWriter(getNotificationFilename(user), ENCODING)) {
            writer.println("");
            writer.println(user.getFirstName() + ":  Please identify the merchant for the following transaction:");
            writer.println(transaction);
            getResolver().say("Request to identify the merchant for a transaction sent to user " + user.getFirstName() +
                    " was written to the file: " + getNotificationFilename(user));
        }
    }

    @Override
    public void requestAssignBudgetItems(User user, Merchant merchant) throws FileNotFoundException,
            UnsupportedEncodingException {

        try (PrintWriter writer = new PrintWriter(getNotificationFilename(user), ENCODING)) {
            writer.println("");
            writer.println("Hi " + user.getFirstName() + ".  What budget items should be associated with the merchant "
                    + merchant + "?");
            getResolver().say("Request to assign budget items the merchant " + merchant + " sent to " +
                    user.getFirstName() + " was written to the file: " + getNotificationFilename(user));
        }
    }

    @Override
    public void requestAssignSplits(User user, Transaction transaction) throws IOException, EntityException,
            RegisterException, ParseException, BudgetException, SQLException {

        try (FileWriter writer = new FileWriter(getNotificationFilename(user), true)) {

            writer.append("");
            writer.append("\nHi " + user.getFirstName() + ":  Please classify the following transaction:\n");
            writer.append(transaction.toStringSummary());

            // Get the budget items for the merchant associated with this transaction:
            List<BudgetItemMerchant> budgetItems = BudgetItemMerchant.getAssignedBudgetItems(transaction.getMerchant());
            writer.append("\nThe assigned budget items and amounts (if specified) for this merchant are:\n");
            int i = 1;
            for (BudgetItemMerchant budgetItem : budgetItems
            ) {
                String lineEnd = "\n";
                if (budgetItem.getAmount() > 0) {
                    lineEnd = ", " + Utility.formatDollarAmount(budgetItem.getBudgetItem().getAmount()) + ", 0";
                } else {
                    if (budgetItem.getPercentage() > 0) {
                        lineEnd = ", 0, " + budgetItem.getPercentage() + "%";
                    }
                }
                writer.append("   " + i++ + ".  " + budgetItem.getBudgetItem().getPayee() + lineEnd);
            }
            writer.append("Enter:  item_number <sp> amount <sp> memo (if multiple items add <comma> between):  \n");

            getResolver().say("Request to " + user.getFirstName() + " classify transaction was written to the " +
                    "file: " + getNotificationFilename(user));
        }
    }

    @Override
    public void sendItemsOfInterestReport(Forecast forecast) throws Exception, EntityException,
            BudgetException, ViewException, RegisterException {
        ForecastView forecastView = new ForecastView(forecast);
        List<UserResource> reports = forecastView.renderItemsOfInterestReport();
        for (UserResource userResource : reports
        ) {
            Utility.getResolver().say("Items of interest report for user " + userResource.getUser().getFirstName() +
                    " written to the file " + userResource.getFile().getAbsolutePath());
        }
    }

    /**
     * Generate an Overdue and Upcoming Items Report for each registered user of this account and "send" it by copying it
     * to the each user's personal file system.
     *
     * @param forecast The forecast in which to look for overdue and upcoming forecast transactions.
     * @throws Exception
     * @throws ViewException
     * @throws EntityException
     * @throws BudgetException
     * @throws RegisterException
     */
    @Override
    public void sendOverdueAndUpcomingItemsReport(Forecast forecast) throws Exception, ViewException,
            EntityException, BudgetException, RegisterException, NotificationServiceException {

        // Generate the Overdue and Upcoming Items Report using the text based forecast view:
        ForecastView forecastView = new com.hixon.financialApp.view.text.ForecastView(forecast);
        List<UserResource> overdueItemsReports = forecastView.renderOverdueItemsReport(forecast);
        List<UserResource> upcomingItemsReports = forecastView.renderUpcomingItemsReport(forecast);
        for (int i = 0; i < Math.max(overdueItemsReports.size(), upcomingItemsReports.size()); i++) {

            UserResource overdueAndUpcomingItemsReport = null;

            // If an overdue items report was generated:
            if (overdueItemsReports.get(i) != null) {

                // then make that the overdue and upcoming items report:
                overdueAndUpcomingItemsReport = overdueItemsReports.get(i);

                // and if an upcoming items report was also generated:
                if (upcomingItemsReports.get(i) != null) {

                    // Then append the upcoming items report to the end of the overdue items report:
                    Utility.appendToFile(overdueItemsReports.get(i).getFile(), upcomingItemsReports.get(i).getFile());
                }
            } else { // but if there isn't an overdue items report:

                // then if there is an upcoming items report:
                if (upcomingItemsReports.get(i) != null) {

                    // then make it the overdue and upcoming items report:
                    overdueAndUpcomingItemsReport = upcomingItemsReports.get(i);
                }
            }

            if (overdueAndUpcomingItemsReport != null) {
                sendFileToUser(overdueAndUpcomingItemsReport.getUser(), overdueAndUpcomingItemsReport.getFile(),
                        OVERDUE_AND_UPCOMING_ITEMS_REPORT);
            } else {
                Utility.getResolver().say("No overdue or upcoming items were found.  No report generated.");
            }

        }
    }


    @Override
    public void sendNewTransactionSummaryReport(Register register) throws Exception, ViewException, EntityException,
            BudgetException, RegisterException, NotificationServiceException {
        RegisterView registerView = new RegisterView(register);
        List<UserResource> reports = registerView.renderNewTransactionSummaryReport();
        for (UserResource userResource : reports
        ) {
            sendFileToUser(userResource.getUser(), userResource.getFile(), NEW_TRANSACTION_SUMMARY_REPORT);
            if (userResource.getFile().getAbsolutePath() == null) {
                Utility.getResolver().say("No new transactions were found.  No report generated.");
            }
        }

        // If we successfully rendered the new transaction reports, then set the new transactions flags to false:
        register.setTransactionsToNotNew();
    }
}
