package com.hixon.financialApp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for keeping the OFX parser's own logging off the console.
 *
 * <p>ofx4j logs through commons-logging, which with no log4j 1.x and no jcl-over-slf4j on the
 * classpath resolves to its {@code Jdk14Logger} -- java.util.logging, whose default handler writes
 * to the console, in the middle of the import conversation.
 *
 * <p>The first attempt at this set the level on the parent logger from a static initializer and
 * held nothing.  {@code LogManager} keeps loggers weakly, so the configured parent was collected
 * before the parse ran and the child's parent then resolved to the root logger at INFO.  The
 * INTU.BID line printed anyway on 09-06-2026 -- exit code fine, nothing to show the setting had
 * evaporated.  These tests assert the outcome rather than the call, so a regression of that shape
 * fails here instead of in a log file.
 */
@DisplayName("OFX Parser Chatter Tests")
class OfxParserChatterTest {

    @Test
    @DisplayName("The lines the parser prints on every import are suppressed")
    void testParserInfoLinesAreNotLoggable() {

        Main.silenceOfxParserChatter();

        // The two classes that actually produced the noise, named in the log they came from:
        //   BaseOFXReader              "Processing OFX 1 headers..."
        //   AggregateStackContentHandler  "Element INTU.BID is not supported on aggregate SONRS"
        assertFalse(Logger.getLogger("com.webcohesion.ofx4j.io.BaseOFXReader").isLoggable(Level.INFO),
                "this is the line that opens every import");
        assertFalse(Logger.getLogger("com.webcohesion.ofx4j.io.AggregateStackContentHandler")
                        .isLoggable(Level.INFO),
                "and this is the INTU.BID line that follows it");
    }

    @Test
    @DisplayName("A problem worth hearing about still gets through")
    void testWarningsAndErrorsStillLog() {

        Main.silenceOfxParserChatter();

        // The point is to drop chatter, not to go deaf.  A malformed statement must still say so.
        assertTrue(Logger.getLogger("com.webcohesion.ofx4j.io.BaseOFXReader").isLoggable(Level.WARNING));
        assertTrue(Logger.getLogger("com.webcohesion.ofx4j.io.BaseOFXReader").isLoggable(Level.SEVERE));
    }

    @Test
    @DisplayName("An ofx4j class that has never logged before is quiet too")
    void testUnnamedChildrenInheritTheLevel() {

        Main.silenceOfxParserChatter();

        // Naming the two known classes must not become the only thing that works:  a different
        // ofx4j class logging on some other statement should inherit the parent's level.
        assertFalse(Logger.getLogger("com.webcohesion.ofx4j.io.SomeOtherReader").isLoggable(Level.INFO),
                "the parent level has to cover children that are not named individually");
    }

    @Test
    @DisplayName("The configured logger is held, so its level cannot be collected away")
    void testLoggerIsStronglyReferenced() throws Exception {

        // The actual defect:  LogManager holds loggers weakly, so a level set on one nobody
        // references survives only until the next collection.  Main keeps the parent in a static
        // field;  this asserts that field exists and holds the right logger, because the symptom
        // otherwise appears minutes later and only in a log file.
        java.lang.reflect.Field held = Main.class.getDeclaredField("OFX4J_LOGGER");
        held.setAccessible(true);

        Object logger = held.get(null);
        assertNotNull(logger, "the parent logger must be held, not just configured");
        assertTrue(logger instanceof Logger);
        assertEquals("com.webcohesion.ofx4j", ((Logger) logger).getName());
        assertEquals(Level.WARNING, ((Logger) logger).getLevel());
    }
}
