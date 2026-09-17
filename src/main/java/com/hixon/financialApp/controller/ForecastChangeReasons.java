package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.budget.Item;
import com.hixon.financialApp.utility.Utility;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Says why the daily update is offering to update the forecast.
 *
 * <p>The offer is made whenever the forecast is out of sync, and it used to say only "Budget items were
 * changed."  On 09-14-2026 that appeared after a run in which the user had changed no budget item
 * knowingly:  picking Airfare from a search had copied it from the Bill Pay Dave budget into Bill Pay
 * Danni's, and a transaction had been recategorized.  Nothing on screen connected either one to the
 * question.  This compares the budget before and after the run and names what it finds.
 *
 * <p>Only changes a forecast regeneration would act on are named.  On-demand and unplanned items generate
 * no occurrences:  on 09-15-2026 the reasons offered were "Budget item added: Dog Food (Pets, $-144
 * On-Demand)" and a recategorization from Dog Food to Dog treats, both on-demand, and updating the forecast
 * for either would have changed nothing.
 */
public final class ForecastChangeReasons {

    private ForecastChangeReasons() {
    }

    /**
     * One budget item as the user would recognise it, the values that make the forecast stale when they
     * change, and whether the item generates forecast occurrences at all.
     */
    record ItemState(String displayString, String signature, boolean affectsForecast) {

        /** An item that generates forecast occurrences. */
        ItemState(String displayString, String signature) {
            this(displayString, signature, true);
        }
    }

    /**
     * Whether a budget item generates forecast occurrences.  On-demand and unplanned items do not:  their
     * occurrences are created one at a time to hold a transaction that has already happened.  The same rule
     * the orphan check and the import summary use.
     */
    public static boolean affectsForecast(Item.PeriodType period, Item.HowOccurs howOccurs) {
        return period != Item.PeriodType.ON_DEMAND && howOccurs != Item.HowOccurs.UNPLANNED;
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
            snapshot.put(item.getId().toString(),
                    new ItemState(display, signature, affectsForecast(item.getPeriod(), item.getHowOccurs())));
        }
        return snapshot;
    }

    /**
     * The reasons the forecast is out of date, in the order the user should read them.
     *
     * @param before         the budget when the update started, or null if it could not be read
     * @param after          the budget now, or null if it could not be read
     * @param recategorized  whether a transaction involving a forecast-generating item was recategorized
     * @param staleAtStart   whether the forecast was already out of date when the update started
     * @return one line per reason;  empty when nothing that affects the forecast changed
     */
    static List<String> describe(Map<String, ItemState> before, Map<String, ItemState> after,
                                 boolean recategorized, boolean staleAtStart) {
        List<String> reasons = new ArrayList<>();
        if (before != null && after != null) {
            for (Map.Entry<String, ItemState> entry : after.entrySet()) {
                ItemState now = entry.getValue();
                ItemState was = before.get(entry.getKey());
                if (was == null) {
                    if (now.affectsForecast()) {
                        reasons.add("Budget item added:  " + now.displayString());
                    }
                } else if (!was.signature().equals(now.signature())
                        && (was.affectsForecast() || now.affectsForecast())) {
                    // Either side counts:  an item that stops generating occurrences changes the forecast too.
                    reasons.add("Budget item changed:  " + now.displayString());
                }
            }
            for (Map.Entry<String, ItemState> entry : before.entrySet()) {
                if (!after.containsKey(entry.getKey()) && entry.getValue().affectsForecast()) {
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

    /**
     * The budget items whose changes {@link #describe} would report, by id:  the ones a forecast update has
     * to regenerate.
     *
     * @param before the budget when the update started, or null if it could not be read
     * @param after  the budget now, or null if it could not be read
     * @return their ids, or null when either snapshot is missing and so nothing can be ruled out
     */
    static Set<UUID> changedItemIds(Map<String, ItemState> before, Map<String, ItemState> after) {
        if (before == null || after == null) {
            return null;
        }
        Set<UUID> ids = new HashSet<>();
        for (Map.Entry<String, ItemState> entry : after.entrySet()) {
            ItemState now = entry.getValue();
            ItemState was = before.get(entry.getKey());
            if ((was == null && now.affectsForecast()) || (was != null && !was.signature().equals(now.signature())
                    && (was.affectsForecast() || now.affectsForecast()))) {
                ids.add(UUID.fromString(entry.getKey()));
            }
        }
        for (Map.Entry<String, ItemState> entry : before.entrySet()) {
            if (!after.containsKey(entry.getKey()) && entry.getValue().affectsForecast()) {
                ids.add(UUID.fromString(entry.getKey()));
            }
        }
        return ids;
    }

    /**
     * Which budget items a daily update's forecast update should regenerate.
     *
     * @param staleAtStart        whether the forecast was already out of date when the update started;  what
     *                            made it so is not known, so everything is regenerated
     * @param changedItems        the budget items that changed during the run, or null if unknown
     * @param recategorized       whether a recategorization changed the forecast
     * @param recategorizedItems  the budget items those recategorizations touched, or null if unknown
     * @return the budget items to regenerate, or null to regenerate the whole forecast
     */
    static Set<UUID> updateScope(boolean staleAtStart, Set<UUID> changedItems, boolean recategorized,
                                 Set<UUID> recategorizedItems) {
        if (staleAtStart || changedItems == null || (recategorized && recategorizedItems == null)) {
            return null;
        }
        Set<UUID> scope = new HashSet<>(changedItems);
        if (recategorized) {
            scope.addAll(recategorizedItems);
        }
        return scope;
    }

    private static String date(Calendar date) {
        return date == null ? "" : Utility.calendarDateToStringDate(date);
    }
}
