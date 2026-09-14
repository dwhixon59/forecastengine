package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.utility.Utility;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Says why the daily update is offering to update the forecast.
 *
 * <p>The offer is made whenever the forecast is out of sync, and it used to say only "Budget items were
 * changed."  On 09-14-2026 that appeared after a run in which the user had changed no budget item
 * knowingly:  picking Airfare from a search had copied it from the Bill Pay Dave budget into Bill Pay
 * Danni's, and a transaction had been recategorized.  Nothing on screen connected either one to the
 * question.  This compares the budget before and after the run and names what it finds.
 */
public final class ForecastChangeReasons {

    private ForecastChangeReasons() {
    }

    /**
     * One budget item as the user would recognise it, and the values that make the forecast stale when
     * they change.
     */
    record ItemState(String displayString, String signature) {
    }

    /**
     * Captures the budget items that matter to the forecast, keyed by id.
     *
     * @param items the budget's items
     * @return the snapshot to pass to {@link #describe}
     */
    static Map<String, ItemState> snapshot(List<BudgetItem> items) {
        Map<String, ItemState> snapshot = new LinkedHashMap<>();
        for (BudgetItem item : items) {
            if (item == null || item.getId() == null) {
                continue;
            }
            String display = item.getDisplayString();
            String signature = display + "|" + item.getAmount() + "|" + item.getPeriod() + "|" +
                    item.getHowOccurs() + "|" + date(item.getStartDate()) + "|" + date(item.getEndDate());
            snapshot.put(item.getId().toString(), new ItemState(display, signature));
        }
        return snapshot;
    }

    /**
     * The reasons the forecast is out of date, in the order the user should read them.
     *
     * @param before         the budget when the update started, or null if it could not be read
     * @param after          the budget now, or null if it could not be read
     * @param recategorized  whether a transaction was recategorized during the review
     * @param staleAtStart   whether the forecast was already out of date when the update started
     * @return one line per reason;  empty when none could be identified
     */
    static List<String> describe(Map<String, ItemState> before, Map<String, ItemState> after,
                                 boolean recategorized, boolean staleAtStart) {
        List<String> reasons = new ArrayList<>();
        if (before != null && after != null) {
            for (Map.Entry<String, ItemState> entry : after.entrySet()) {
                ItemState was = before.get(entry.getKey());
                if (was == null) {
                    reasons.add("Budget item added:  " + entry.getValue().displayString());
                } else if (!was.signature().equals(entry.getValue().signature())) {
                    reasons.add("Budget item changed:  " + entry.getValue().displayString());
                }
            }
            for (Map.Entry<String, ItemState> entry : before.entrySet()) {
                if (!after.containsKey(entry.getKey())) {
                    reasons.add("Budget item removed:  " + entry.getValue().displayString());
                }
            }
        }
        if (recategorized) {
            reasons.add("An imported transaction was recategorized.");
        }
        if (staleAtStart) {
            reasons.add("The forecast was already out of date when this update started.");
        }
        return reasons;
    }

    private static String date(Calendar date) {
        return date == null ? "" : Utility.calendarDateToStringDate(date);
    }
}
