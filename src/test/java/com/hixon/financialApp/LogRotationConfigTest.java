package com.hixon.financialApp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.CompositeTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.TimeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.TriggeringPolicy;
import org.apache.logging.log4j.core.config.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the shipped log4j2.properties, asserted against the configuration log4j actually
 * built from it.
 *
 * <p>These test the real file rather than a fixture, because the failure being guarded against is a
 * typo in it.  A broken log4j configuration does not throw:  log4j falls back to a default console
 * configuration, {@code getAppender("File")} returns null, and every diagnostic the import writes
 * goes nowhere.  Nothing in the app's output would say so.
 *
 * <p>Rotation was added after a plain File appender let logs/app.log reach 12 MB with nothing to
 * stop it -- the matcher writes a scored candidate list for every imported transaction, so the file
 * grows with every import and never shrinks.
 */
@DisplayName("Log Rotation Config Tests")
class LogRotationConfigTest {

    private static Appender fileAppender() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration configuration = context.getConfiguration();
        return configuration.getAppender("File");
    }

    @Test
    @DisplayName("The configuration parses and still yields an appender named File")
    void testAppenderExistsAndRolls() {

        Appender appender = fileAppender();

        // Null here means log4j rejected the configuration and fell back to its default.  Six
        // loggers reference this appender by the name "File", so renaming it would be as silent a
        // break as a typo.
        assertNotNull(appender, "no appender named 'File' -- the configuration failed to parse");
        assertInstanceOf(RollingFileAppender.class, appender,
                "a plain File appender is what let the log reach 12 MB");
    }

    @Test
    @DisplayName("It rolls on size and on the day changing")
    void testBothTriggeringPolicies() {

        RollingFileAppender rolling = (RollingFileAppender) fileAppender();
        TriggeringPolicy policy = rolling.getTriggeringPolicy();

        assertInstanceOf(CompositeTriggeringPolicy.class, policy,
                "size alone lets a quiet week hold one file open; time alone lets one busy import "
                        + "bury a day's history");

        TriggeringPolicy[] policies = ((CompositeTriggeringPolicy) policy).getTriggeringPolicies();

        SizeBasedTriggeringPolicy size = (SizeBasedTriggeringPolicy) Arrays.stream(policies)
                .filter(SizeBasedTriggeringPolicy.class::isInstance).findFirst()
                .orElseThrow(() -> new AssertionError("no size-based policy"));
        assertEquals(10L * 1024 * 1024, size.getMaxFileSize(), "10MB, parsed as log4j parses it");

        assertTrue(Arrays.stream(policies).anyMatch(TimeBasedTriggeringPolicy.class::isInstance),
                "the daily roll is what keeps an archive findable by date");
    }

    @Test
    @DisplayName("Archives are dated, numbered and compressed")
    void testFilePattern() {

        RollingFileAppender rolling = (RollingFileAppender) fileAppender();
        String pattern = rolling.getFilePattern();

        assertNotNull(pattern);
        assertTrue(pattern.contains("%d{"), "a date in the name is how a past import is found");
        assertTrue(pattern.contains("%i"), "the counter is what a size roll within one day uses");
        assertTrue(pattern.endsWith(".gz"),
                "the suffix is what turns compression on; losing it multiplies the archive size");
    }

    @Test
    @DisplayName("Old archives are deleted, so the directory is bounded and not merely tidy")
    void testArchivesAreBounded() {

        RollingFileAppender rolling = (RollingFileAppender) fileAppender();

        DefaultRolloverStrategy strategy =
                assertInstanceOf(DefaultRolloverStrategy.class,
                        rolling.getManager().getRolloverStrategy());

        assertEquals(20, strategy.getMaxIndex(), "the per-day %i cap");

        // The point of the test.  maxIndex alone bounds one day's files; without a Delete action a
        // daily roll keeps every day forever, which is the same unbounded growth in another shape.
        assertNotNull(strategy.getCustomActions(), "no custom actions -- nothing deletes old archives");
        assertFalse(strategy.getCustomActions().isEmpty(),
                "a Delete action is what bounds the total, not just the count within a day");
    }
}
