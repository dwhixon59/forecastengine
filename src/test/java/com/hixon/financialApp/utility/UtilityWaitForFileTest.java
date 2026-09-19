package com.hixon.financialApp.utility;

import com.hixon.financialApp.view.base.ViewInt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link Utility#waitForFileToBeReleased} and {@link Utility#isFileHeldOpen}.
 *
 * <p>On 09-18-2026 the Citi forecast workbook was read before Excel had saved the user's deletion of a $38.00
 * occurrence, so the occurrence survived.  The read now waits until Excel has let go of the workbook.
 */
@DisplayName("Utility waitForFileToBeReleased")
class UtilityWaitForFileTest {

    @TempDir
    Path tempDir;

    private ViewInt originalView;
    private ViewInt mockView;

    @BeforeEach
    void setUp() {
        originalView = Utility.getView();
        mockView = mock(ViewInt.class);
        Utility.setView(mockView);
    }

    @AfterEach
    void tearDown() {
        Utility.setView(originalView);
    }

    @Test
    @DisplayName("A file nothing has open is read at once, without a word")
    void freeFileDoesNotWait() throws InterruptedException {
        AtomicInteger pauses = new AtomicInteger();

        Utility.waitForFileToBeReleased(tempDir.resolve("forecast.xlsx"), path -> false, pauses::incrementAndGet);

        assertEquals(0, pauses.get());
        verify(mockView, never()).say(anyString());
    }

    @Test
    @DisplayName("A file held open is waited on until it is released, and the wait is explained")
    void heldFileIsWaitedOn() throws InterruptedException {
        AtomicInteger checks = new AtomicInteger();
        AtomicInteger pauses = new AtomicInteger();

        // Held for the first three checks, then released.
        Utility.waitForFileToBeReleased(tempDir.resolve("forecast.xlsx"),
                path -> checks.incrementAndGet() <= 3, pauses::incrementAndGet);

        assertEquals(2, pauses.get());
        verify(mockView, times(2)).say(anyString());
    }

    @Test
    @DisplayName("A file this process can open for writing is not held open")
    void writableFileIsNotHeldOpen() throws IOException {
        Path file = Files.writeString(tempDir.resolve("forecast.xlsx"), "workbook");

        assertFalse(Utility.isFileHeldOpen(file));
    }

    @Test
    @DisplayName("A missing file is not held open, so nothing waits for it forever")
    void missingFileIsNotHeldOpen() {
        assertFalse(Utility.isFileHeldOpen(tempDir.resolve("missing.xlsx")));
    }

    @Test
    @DisplayName("A read-only file is not held open, so nothing waits for it forever")
    void readOnlyFileIsNotHeldOpen() throws IOException {
        Path file = Files.writeString(tempDir.resolve("forecast.xlsx"), "workbook");
        file.toFile().setReadOnly();
        try {
            assertFalse(Utility.isFileHeldOpen(file));
        } finally {
            file.toFile().setWritable(true);
        }
    }
}
