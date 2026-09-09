package com.hixon.financialApp.model.financialinstitution;

import com.hixon.financialApp.model.qfx.QfxParser;
import com.hixon.financialApp.model.qfx.QfxTransaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

/**
 * Unit tests for undoing Wells Fargo's double-escaping of XML entities in NAME and MEMO.
 *
 * <p>Wells Fargo escapes those fields twice, so {@code D&G Svc Plan DGAppC} is written to the QFX
 * file as {@code D&amp;amp;amp;G Svc Plan DGAppC}.  ofx4j resolves one level -- correctly;  it is the
 * bank that is wrong -- and hands back {@code D&amp;amp;G Svc Plan DGAppC}, which is what used to be
 * stored.  On 09-08-2026 the import said "No merchant found for payee: D&amp;amp;G Svc Plan DGAppC" and
 * offered to create a merchant called "D&amp;g Svc Plan Dgappc".
 *
 * <p>The repair belongs to the institution, not the parser:  Citibank escapes correctly and must come
 * through untouched.  Both cases are covered below, each against a fixture in the shape its bank
 * actually sends.
 */
@DisplayName("Double-escaped XML entity tests")
public class DoubleEscapedEntityTest {

    /** A WellsFargoBank whose real methods run, without the constructor's session/database setup. */
    private final WellsFargoBank wellsFargo =
            mock(WellsFargoBank.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));

    /** A Citibank whose real methods run, for the same reason. */
    private final CitiBank citi =
            mock(CitiBank.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));

    /** The NAME of the single transaction in a fixture, as the OFX parser produces it. */
    private static String parsedName(String resource) throws Exception {
        try (InputStream in = DoubleEscapedEntityTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "fixture " + resource + " should be on the test classpath");
            QfxParser parser = new QfxParser();
            parser.open(in);
            QfxTransaction transaction = parser.getNext();
            assertNotNull(transaction, "the fixture should hold one transaction");
            return transaction.getName();
        }
    }

    /*
     * What the parser hands the institution.  These two tests are the diagnosis:  they record that
     * ofx4j is doing the right thing on both files, so the difference between them is the bank's.
     */

    @Test
    @DisplayName("The parser leaves one level of escaping on a Wells Fargo name")
    void theParserLeavesOneLevelOfEscapingOnAWellsFargoName() throws Exception {

        // The fixture holds <NAME>D&amp;amp;G Svc Plan DGAppC, exactly as the 09-08-2026 statement did.
        // One unescape is all a conforming reader should do, and it is not enough here.
        assertEquals("D&amp;G Svc Plan DGAppC", parsedName("/qfx/test-wf-double-escaped-name.qfx"),
                "ofx4j resolves one level;  Wells Fargo applied two");
    }

    @Test
    @DisplayName("The parser fully resolves a Citibank name")
    void theParserFullyResolvesACitibankName() throws Exception {

        // The fixture holds <NAME>FIORELLI WINERY &amp; VINE -- escaped once, as it should be.
        assertEquals("FIORELLI WINERY & VINE", parsedName("/qfx/test-citi-single-escaped-name.qfx"),
                "a correctly escaped name needs no repair");
    }

    /*
     * What each institution then does with it.
     */

    @Test
    @DisplayName("Wells Fargo resolves the second level of escaping")
    void wellsFargoResolvesTheSecondLevelOfEscaping() throws Exception {

        String parsed = parsedName("/qfx/test-wf-double-escaped-name.qfx");

        assertEquals("D&G Svc Plan DGAppC", wellsFargo.normalizeImportedText(parsed),
                "the payee stored should be the merchant's actual name");
    }

    @Test
    @DisplayName("Citibank text is left exactly as the parser produced it")
    void citibankTextIsLeftAlone() throws Exception {

        String parsed = parsedName("/qfx/test-citi-single-escaped-name.qfx");

        // The default hook is the identity, so a bank that escapes correctly is untouched.  Repairing
        // in the parser instead would have applied Wells Fargo's fix to this too.
        assertEquals("FIORELLI WINERY & VINE", citi.normalizeImportedText(parsed),
                "a correctly escaped name must not be altered");
    }

    /*
     * Edges of the Wells Fargo repair.
     */

    @Test
    @DisplayName("Text that is already clean is unchanged")
    void alreadyCleanTextIsUnchanged() {

        // The overwhelming majority of rows, and the shape Wells Fargo would send if they ever fixed
        // their exporter -- the repair has to stay a no-op on it.
        assertEquals("PUBLIX #2108 BRADENTON FL", wellsFargo.normalizeImportedText("PUBLIX #2108 BRADENTON FL"));
        assertEquals("D&G Svc Plan DGAppC", wellsFargo.normalizeImportedText("D&G Svc Plan DGAppC"));
        assertEquals("TST* FINE WINE & T", wellsFargo.normalizeImportedText("TST* FINE WINE & T"));
    }

    @Test
    @DisplayName("A bare ampersand is not treated as the start of an entity")
    void aBareAmpersandIsLeftAlone() {

        // Bank descriptors are full of these.  unescapeXml only resolves complete references, which is
        // why it is used here rather than an HTML unescape.
        assertEquals("A & B & C", wellsFargo.normalizeImportedText("A & B & C"));
        assertEquals("AT&T MOBILITY", wellsFargo.normalizeImportedText("AT&T MOBILITY"));
    }

    @Test
    @DisplayName("A doubly-escaped apostrophe is resolved too")
    void aDoublyEscapedNumericReferenceIsResolved() {

        // Wells Fargo escapes whatever needs escaping twice, not only the ampersand, so the repair
        // covers the numeric references as well.
        assertEquals("MCDONALD'S #402", wellsFargo.normalizeImportedText("MCDONALD&#39;S #402"));
    }

    @Test
    @DisplayName("Null text stays null")
    void nullTextStaysNull() {

        // MEMO is absent on most transactions, and the join handles the null itself.
        assertNull(wellsFargo.normalizeImportedText(null));
    }
}
