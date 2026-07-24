package com.hixon.financialApp.model.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link MatchQuery#getQuery(String)} focusing on the multi-word tokenized search
 * behavior added so that a seed like "ADT SECURITY" can surface the merchant "ADT Safe Haven".
 */
class MatchQueryTokenizedSearchTest {

    /** A merchant-style match query: single name column, no caller-supplied ORDER BY. */
    private MatchQuery merchantQuery() {
        return new MatchQuery("select * from merchant m WHERE ", "m.name", "m.name");
    }

    @Test
    void multiWordSeedMatchesAnyWordAndRanksByMatchCount() {
        String sql = merchantQuery().getQuery("ADT SECURITY");

        // Each word is matched independently (OR semantics) so a partial match is found.
        assertTrue(sql.contains("m.name LIKE '%ADT%'"), "should match on the word ADT: " + sql);
        assertTrue(sql.contains("m.name LIKE '%SECURITY%'"), "should match on the word SECURITY: " + sql);
        assertTrue(sql.contains(" OR "), "words should be OR'd together: " + sql);

        // Results are ranked by the number of matching words.
        assertTrue(sql.contains("ORDER BY ("), "should rank results: " + sql);
        assertTrue(sql.contains(" DESC"), "ranking should be descending: " + sql);
        assertTrue(sql.contains(" + "), "match count should sum per-word predicates: " + sql);
    }

    @Test
    void singleWordSeedUsesWholePhraseLike() {
        String sql = merchantQuery().getQuery("ADT");

        assertTrue(sql.contains("m.name LIKE '%ADT%'"), "single word still uses LIKE: " + sql);
        // A single word should not trigger the tokenized ranking path.
        assertFalse(sql.contains("ORDER BY ("), "single word should not add relevance ranking: " + sql);
    }

    @Test
    void stopwordsDoNotCountAsMeaningfulWords() {
        // "THE" is a stopword, leaving only "HOME" and "DEPOT" as meaningful words.
        String sql = merchantQuery().getQuery("THE HOME DEPOT");

        assertTrue(sql.contains("m.name LIKE '%HOME%'"), "should match HOME: " + sql);
        assertTrue(sql.contains("m.name LIKE '%DEPOT%'"), "should match DEPOT: " + sql);
        assertFalse(sql.contains("LIKE '%THE%'"), "stopword THE should be dropped: " + sql);
        assertTrue(sql.contains("ORDER BY ("), "two meaningful words should rank: " + sql);
    }

    @Test
    void seedWithOnlyOneMeaningfulWordFallsBackToPhrase() {
        // "OF THE" are both stopwords, leaving a single meaningful word "GAP".
        String sql = merchantQuery().getQuery("GAP OF THE");

        assertFalse(sql.contains("ORDER BY ("), "fewer than two meaningful words should not rank: " + sql);
    }

    @Test
    void tokenizedSearchNotAppliedWhenCallerSuppliesOrderBy() {
        // Budget-item style query supplies its own ORDER BY via selectQueryAfterMatch.
        MatchQuery ordered = new MatchQuery(
                "select * from budgetitem bi WHERE ", "bi.payee", "bi.payee", "ORDER BY bi.payee ASC");
        String sql = ordered.getQuery("CASH ADVANCE");

        // Falls back to the single whole-phrase LIKE and keeps the caller's ORDER BY.
        assertTrue(sql.contains("bi.payee LIKE '%CASH ADVANCE%'"), "should use whole phrase: " + sql);
        assertTrue(sql.contains("ORDER BY bi.payee ASC"), "should keep caller ORDER BY: " + sql);
    }

    @Test
    void paymentProcessorNoiseIsExcludedFromTokenizedSearch() {
        // "PY" is a payment-processor prefix and should not be treated as a meaningful word.
        // Only "PRODIGY", "PEST", and "SOLU" should contribute to the score.
        String sql = merchantQuery().getQuery("PY *PRODIGY PEST SOLU");

        assertTrue(sql.contains("m.name LIKE '%PRODIGY%'"), "should match PRODIGY: " + sql);
        assertTrue(sql.contains("m.name LIKE '%PEST%'"), "should match PEST: " + sql);
        assertTrue(sql.contains("m.name LIKE '%SOLU%'"), "should match SOLU: " + sql);
        assertFalse(sql.contains("LIKE '%PY%'"), "noise prefix PY should be dropped: " + sql);
        assertTrue(sql.contains("ORDER BY ("), "multi-word merchant name should rank: " + sql);
    }

    @Test
    void shortTokensAreExcludedFromTokenizedSearch() {
        // "A" and "B" are too short to be meaningful search tokens.  Only "CANDLE" remains,
        // so the tokenized ranking path is abandoned and the query falls back to a whole-phrase LIKE.
        String sql = merchantQuery().getQuery("A B CANDLE");

        assertTrue(sql.contains("m.name LIKE '%A B CANDLE%'"), "should fall back to whole-phrase LIKE: " + sql);
        assertFalse(sql.contains("ORDER BY ("), "single meaningful word should not use tokenized ranking: " + sql);
    }

    @Test
    void googlePrefixIsExcludedFromTokenizedSearch() {
        // "GOOGLE" is a payment-processor prefix and is filtered out.  "YouTube" remains but
        // "TV" is only two characters, so it is also filtered.  With only one meaningful word
        // left, the query falls back to a whole-phrase LIKE.
        String sql = merchantQuery().getQuery("GOOGLE *YouTube TV");

        assertTrue(sql.contains("m.name LIKE '%GOOGLE *YouTube TV%'"),
                "should fall back to whole-phrase LIKE when only one meaningful word remains: " + sql);
        assertFalse(sql.contains("LIKE '%GOOGLE%'"), "noise prefix GOOGLE should not appear as its own token: " + sql);
        assertFalse(sql.contains("ORDER BY ("), "single meaningful word should not use tokenized ranking: " + sql);
    }
}
