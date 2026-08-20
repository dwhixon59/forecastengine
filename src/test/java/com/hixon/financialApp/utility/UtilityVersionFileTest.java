package com.hixon.financialApp.utility;

import com.hixon.financialApp.view.base.ViewInt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link Utility#versionFile(String, String)}.
 *
 * <p>The behaviour that matters here is what happens to the existing previous version when the versioning cannot be
 * completed.  The rename fails whenever the current file is open in another program — an everyday occurrence for the
 * forecast workbook, which lives in OneDrive and is routinely open in Excel — and the previous version used to be
 * deleted before that rename was attempted, so a failed render destroyed the backup it was supposed to rotate.</p>
 */
@DisplayName("Utility versionFile")
class UtilityVersionFileTest {

    @TempDir
    Path tempDir;

    private ViewInt originalView;

    @BeforeEach
    void setUp() {
        originalView = Utility.getView();
        ViewInt mockView = mock(ViewInt.class);

        // Decline the "would you like to try again?" prompt so a failing rename gives up rather than looping.
        when(mockView.getYesOrNo(anyString())).thenReturn(false);
        Utility.setView(mockView);
    }

    @AfterEach
    void tearDown() {
        Utility.setView(originalView);
    }

    @Test
    @DisplayName("renames the current file when there is no previous version")
    void renamesCurrentFileWhenThereIsNoPreviousVersion() throws IOException {

        Path current = writeFile("forecast.xlsx", "current");

        assertTrue(Utility.versionFile(current.toString(), "_old"));

        assertFalse(Files.exists(current), "the current file should have been renamed away");
        assertEquals("current", Files.readString(tempDir.resolve("forecast_old.xlsx")));
    }

    @Test
    @DisplayName("replaces the previous version and leaves nothing behind")
    void replacesThePreviousVersion() throws IOException {

        Path current = writeFile("forecast.xlsx", "current");
        writeFile("forecast_old.xlsx", "previous");

        assertTrue(Utility.versionFile(current.toString(), "_old"));

        assertFalse(Files.exists(current), "the current file should have been renamed away");
        assertEquals("current", Files.readString(tempDir.resolve("forecast_old.xlsx")),
                "the previous version should have been replaced by the current file");
        assertFalse(Files.exists(tempDir.resolve("forecast_old.xlsx" + Utility.SUPERSEDED_FILENAME_EXTENSION)),
                "the file held aside during the rename should have been cleaned up");
    }

    /**
     * The regression this guards: a rename that cannot complete must not cost the user the previous version.
     */
    @Test
    @DisplayName("keeps the previous version when the rename fails")
    void keepsThePreviousVersionWhenTheRenameFails() throws IOException {

        Path current = writeFile("forecast.xlsx", "current");
        writeFile("forecast_old.xlsx", "previous");

        // Holding the current file open blocks the rename, the same way Excel does with an open workbook.  Windows
        // refuses to rename a file that is open without delete sharing, which is what makes this reproducible; the
        // application is Windows-only, so the failure is asserted rather than tolerated.
        try (FileOutputStream holdOpen = new FileOutputStream(current.toFile(), true)) {

            assertFalse(Utility.versionFile(current.toString(), "_old"),
                    "the rename cannot succeed while the current file is held open");

            assertEquals("previous", Files.readString(tempDir.resolve("forecast_old.xlsx")),
                    "a failed versioning must leave the previous version intact");
            assertTrue(Files.exists(current), "the current file should still be there after a failed rename");
            assertFalse(Files.exists(tempDir.resolve("forecast_old.xlsx" + Utility.SUPERSEDED_FILENAME_EXTENSION)),
                    "the file held aside should have been put back, not left under its temporary name");
        }
    }

    @Test
    @DisplayName("reports failure when the current file does not exist")
    void reportsFailureWhenTheCurrentFileDoesNotExist() {

        assertFalse(Utility.versionFile(tempDir.resolve("missing.xlsx").toString(), "_old"));
    }

    private Path writeFile(String name, String contents) throws IOException {
        Path path = tempDir.resolve(name);
        Files.writeString(path, contents);
        return path;
    }
}
