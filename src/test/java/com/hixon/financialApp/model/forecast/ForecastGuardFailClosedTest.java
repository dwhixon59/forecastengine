package com.hixon.financialApp.model.forecast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Calendar;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins that the two guards {@code ForecastEngine.generateForecastTransactions} relies on fail closed.
 *
 * <p>Generation adds an occurrence for every date an item falls on unless one of these two says the date is already
 * taken -- {@code hasOverriddenForecastTransactionOnDate} for an occurrence the user overrode, and
 * {@code hasReconciledForecastTransactionOnDate} for one that already has a charge linked to it.  Both used to catch
 * {@link SQLException} and answer {@code false}, one of them commented "to avoid blocking forecast generation".
 * False is the one answer that cannot be safely guessed:  it means "nothing is there", so a transient database error
 * would let generation add a second occurrence beside every overridden and every reconciled one in the forecast.
 * The override guard was worse again -- it cached its empty fallback in the field it reads from, so a single failure
 * removed the protection for the rest of the object's life rather than just the failing call.
 *
 * <p>These assertions are on the method signatures rather than on behaviour, and that is a real limitation worth
 * stating:  both methods need a live database connection, so a unit test cannot provoke the {@code SQLException}
 * whose handling is the thing being fixed.  What the signature does prove is that the exception is no longer
 * swallowed -- a caller is now forced to deal with it, and {@code ForecastEngine} lets it abort the run.  If someone
 * later re-adds a {@code catch} block, the {@code throws} clause is what they would have to delete to do it, and
 * deleting it fails this test.
 */
@DisplayName("Forecast generation guards fail closed")
class ForecastGuardFailClosedTest {

    private static Method guard(String name) throws NoSuchMethodException {
        return Forecast.class.getMethod(name, ForecastItem.class, Calendar.class);
    }

    private static boolean declares(Method method, Class<?> exception) {
        return Arrays.asList(method.getExceptionTypes()).contains(exception);
    }

    @Test
    @DisplayName("the overridden-occurrence guard propagates a database error")
    void testOverriddenGuardPropagates() throws NoSuchMethodException {
        assertTrue(declares(guard("hasOverriddenForecastTransactionOnDate"), SQLException.class),
                "it must not answer 'nothing is overridden' because the database was unreachable");
    }

    @Test
    @DisplayName("the reconciled-occurrence guard propagates a database error")
    void testReconciledGuardPropagates() throws NoSuchMethodException {
        assertTrue(declares(guard("hasReconciledForecastTransactionOnDate"), SQLException.class),
                "it must not answer 'nothing is reconciled' because the database was unreachable");
    }

    @Test
    @DisplayName("the lazy loader behind the overridden guard propagates too")
    void testKeyLoaderPropagates() throws NoSuchMethodException {

        // The public guard can only propagate what the loader gives it;  the loader is where the empty-set fallback
        // lived, and where it was cached:
        Method loader = Forecast.class.getDeclaredMethod("loadOverriddenTransactionKeys");
        assertTrue(declares(loader, SQLException.class),
                "the loader must not hand back an empty set when the read failed");
    }

    @Test
    @DisplayName("a null item or date is still an ordinary false, not an error")
    void testNullArgumentsAreStillFalse() throws Exception {

        // The guards are called for every date of every item, and a null there means "no such thing to check",
        // which is a legitimate false.  Only a failed read is exceptional.  This runs without a database because
        // both methods return on the null check before opening a connection:
        Forecast forecast = new Forecast();

        assertFalse(forecast.hasOverriddenForecastTransactionOnDate(null, Calendar.getInstance()));
        assertFalse(forecast.hasOverriddenForecastTransactionOnDate(null, null));
        assertFalse(forecast.hasReconciledForecastTransactionOnDate(null, Calendar.getInstance()));
        assertFalse(forecast.hasReconciledForecastTransactionOnDate(null, null));
    }
}
