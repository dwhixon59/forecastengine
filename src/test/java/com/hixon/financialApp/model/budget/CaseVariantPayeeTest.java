package com.hixon.financialApp.model.budget;

import com.hixon.financialApp.model.budget.Budget.CaseVariantPayee;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for spotting budget item payees that are one name spelled two ways.
 *
 * <p>MySQL's default collation is case-insensitive, which is what makes these pairs easy to create
 * and hard to notice:  nothing rejects the second spelling, and from then on there are two budget
 * items, two sets of assigned merchants, two lines in the forecast's expense breakdown and two
 * candidates competing for the same relevancy score at import time.
 *
 * <p>Ten such pairs were in the budget on 09-04-2026.  {@code Smart Phones} / {@code Smart phones}
 * across six items is the one the import log showed:  the forecast listed both as separate payees
 * inside one Utilities category, and the Visible charge was offered three near-identical choices
 * spanning both spellings.
 */
@DisplayName("Case Variant Payee Tests")
class CaseVariantPayeeTest {

    private static CaseVariantPayee item(String payee, String category, int count) {
        return new CaseVariantPayee(payee, category, count);
    }

    @Test
    @DisplayName("One name spelled two ways is reported")
    void testTwoSpellingsAreFound() {

        // The real pair, with the real item counts.
        List<List<CaseVariantPayee>> variants = Budget.groupCaseVariantPayees(List.of(
                item("Smart Phones", "Utilities", 4),
                item("Smart phones", "Utilities", 2)));

        assertEquals(1, variants.size(), "the two spellings are one finding, not two");
        assertEquals(2, variants.get(0).size(), "both spellings are reported so the user can pick one");
    }

    @Test
    @DisplayName("Several items sharing one spelling is not a finding")
    void testRepeatedSpellingIsNotAFinding() {

        // Several budget items for one payee is ordinary and deliberate -- four Car Maintenance
        // items is a way of budgeting, not a mistake.  Only a second *spelling* is the problem, so
        // a single spelling must stay silent however many items carry it.
        assertTrue(Budget.groupCaseVariantPayees(List.of(
                item("Car Maintenance", "Automotive", 4))).isEmpty(),
                "one spelling is never a finding, whatever its item count");
    }

    @Test
    @DisplayName("Distinct names are left alone")
    void testDistinctNamesAreNotGrouped() {

        // The check must not reach for near-misses.  "Smart Phones" and "Smart Phone" are different
        // names, and guessing that one is a typo of the other would put the user in front of a
        // question with no right answer.
        assertTrue(Budget.groupCaseVariantPayees(List.of(
                item("Smart Phones", "Utilities", 1),
                item("Smart Phone", "Utilities", 1),
                item("Electricity", "Utilities", 1))).isEmpty(),
                "only case differences are reported, never similar spellings");
    }

    @Test
    @DisplayName("Several distinct findings come back separately, in name order")
    void testMultipleFindingsAreSeparateAndOrdered() {

        List<List<CaseVariantPayee>> variants = Budget.groupCaseVariantPayees(List.of(
                item("Smart Phones", "Utilities", 4),
                item("Smart phones", "Utilities", 2),
                item("Home Goods", "Household", 1),
                item("Home goods", "Household", 1),
                item("Electricity", "Utilities", 1)));

        assertEquals(2, variants.size(), "two names spelled two ways are two findings");
        // Ordered by the lower-cased name so the report reads the same way twice running.
        assertEquals("Home Goods", variants.get(0).get(0).payee());
        assertEquals("Smart Phones", variants.get(1).get(0).payee());
    }

    @Test
    @DisplayName("A variant spanning two categories is still one name spelled two ways")
    void testVariantsAcrossCategories() {

        // Grouping is by name, not by name-and-category:  the same payee spelled two ways in two
        // categories is the same confusion, and reporting it twice would not help.
        List<List<CaseVariantPayee>> variants = Budget.groupCaseVariantPayees(List.of(
                item("Internet Access", "Utilities", 1),
                item("Internet access", "Online Services", 1)));

        assertEquals(1, variants.size());
        assertEquals(2, variants.get(0).size(), "both categories are shown so the user can tell them apart");
    }

    @Test
    @DisplayName("Per-item rows are tallied here, not by the database")
    void testPerItemRowsAreTallied() {

        // The shape the query actually returns:  one row per budget item, count one.  Counting was
        // moved out of SQL because the payee column is utf8mb3_general_ci, so a GROUP BY folded the
        // two spellings together before this code saw either of them.
        List<List<CaseVariantPayee>> variants = Budget.groupCaseVariantPayees(List.of(
                item("Smart Phones", "Utilities", 1),
                item("Smart Phones", "Utilities", 1),
                item("Smart Phones", "Utilities", 1),
                item("Smart Phones", "Utilities", 1),
                item("Smart phones", "Utilities", 1),
                item("Smart phones", "Utilities", 1)));

        assertEquals(1, variants.size(), "six rows, one name, two spellings, one finding");
        assertEquals(2, variants.get(0).size());
        assertEquals(4, variants.get(0).get(0).count(), "the four items spelled one way");
        assertEquals(2, variants.get(0).get(1).count(), "the two spelled the other");
    }

    @Test
    @DisplayName("The query must not group on the payee, whatever the collation")
    void testQueryDoesNotGroupOnPayee() {

        // The bug this file exists to prevent a repeat of.  budget_item.payee is
        // utf8mb3_general_ci:  MySQL considers "Smart Phones" and "Smart phones" equal, so
        //   GROUP BY bi.payee, bi.category
        // returned a single row reading "Smart Phones | Utilities | 6" and the check reported
        // nothing against a budget holding ten such pairs.  The Java grouping was correct and
        // tested; the defect was upstream of it, which is why the query is asserted here too.
        String query = Budget.caseVariantPayeeQuery(
                UUID.fromString("11111111-2222-3333-4444-555555555555"));

        assertFalse(query.toUpperCase().contains("GROUP BY"),
                "grouping in SQL applies the case-insensitive collation and hides every finding");
        assertFalse(query.toUpperCase().contains("COUNT("),
                "counting in SQL implies grouping in SQL");
        assertTrue(query.contains("bi.payee"), "the payee still has to be selected");
        assertTrue(query.contains("bi.category"), "and the category, to tell two findings apart");
        assertTrue(query.contains("uuid_to_bin('11111111-2222-3333-4444-555555555555')".toUpperCase())
                        || query.contains("UUID_TO_BIN('11111111-2222-3333-4444-555555555555')"),
                "scoped to one budget");
    }

    @Test
    @DisplayName("Nothing to check is not an error")
    void testEmptyAndNullTolerated() {

        // The check runs inside the daily update, where an exception costs the user their run.
        assertTrue(Budget.groupCaseVariantPayees(List.of()).isEmpty());

        List<CaseVariantPayee> withNulls = new java.util.ArrayList<>();
        withNulls.add(null);
        withNulls.add(item(null, "Utilities", 1));
        withNulls.add(item("Electricity", "Utilities", 1));
        assertTrue(Budget.groupCaseVariantPayees(withNulls).isEmpty(),
                "a missing payee is skipped, not thrown over");
    }
}
