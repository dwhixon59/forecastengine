package com.hixon.financialApp.model.qfx;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for reading the date the bank attached to a ledger balance.
 *
 * <p>{@code DTASOF} was parsed and thrown away, which was fine while every download was the latest
 * one.  It stops being fine the moment a statement is pulled over a wider date range to recover a
 * missing charge:  wider is not newer, and without the date the two balances are indistinguishable
 * numbers.  The pair that prompted this differ by $99.95 and three days, and the older one is the
 * wider file.
 *
 * <p>The OFX below is the shape of a real Citi credit-card download -- OFX 1.02 SGML, a banking
 * message set with {@code ACCTTYPE CREDITLINE} -- with invented account identifiers.
 */
@DisplayName("QFX Ledger Balance As-Of Tests")
class QfxLedgerBalanceAsOfTest {

    private static String ofx(String ledgerBalanceBlock) {
        return """
                OFXHEADER:100
                DATA:OFXSGML
                VERSION:102
                SECURITY:NONE
                ENCODING:USASCII
                CHARSET:1252
                COMPRESSION:NONE
                OLDFILEUID:NONE
                NEWFILEUID:NONE

                <OFX>
                <SIGNONMSGSRSV1>
                <SONRS>
                <STATUS>
                <CODE>0
                <SEVERITY>INFO
                </STATUS>
                <DTSERVER>20260904091953
                <LANGUAGE>ENG
                </SONRS>
                </SIGNONMSGSRSV1>
                <BANKMSGSRSV1>
                <STMTTRNRS>
                <TRNUID>0
                <STATUS>
                <CODE>0
                <SEVERITY>INFO
                </STATUS>
                <STMTRS>
                <CURDEF>USD
                <BANKACCTFROM>
                <BANKID>000000000
                <ACCTID>XXXXXXXXXXXX0000
                <ACCTTYPE>CREDITLINE
                </BANKACCTFROM>
                <BANKTRANLIST>
                <DTSTART>20260828000000
                <DTEND>20260830235959
                <STMTTRN>
                <TRNTYPE>DEBIT
                <DTPOSTED>20260828090000
                <TRNAMT>-25.00
                <FITID>20260828090001
                <NAME>TEST MERCHANT
                </STMTTRN>
                </BANKTRANLIST>
                """ + ledgerBalanceBlock + """
                </STMTRS>
                </STMTTRNRS>
                </BANKMSGSRSV1>
                </OFX>
                """;
    }

    private static QfxStatement parse(String document) throws Exception {
        QfxParser parser = new QfxParser();
        try {
            parser.open(new ByteArrayInputStream(document.getBytes(StandardCharsets.US_ASCII)));
            return parser.getStatement();
        } finally {
            parser.close();
        }
    }

    @Test
    @DisplayName("The balance and the date it was true on are both read")
    void testAsOfDateIsParsed() throws Exception {

        // The narrow 09-04 download: the balance that must win against the wider, older one.
        QfxStatement statement = parse(ofx("""
                <LEDGERBAL>
                <BALAMT>-11986.25
                <DTASOF>20260904091953
                </LEDGERBAL>
                """));

        assertEquals(-11986.25, statement.getLedgerBalance(), 0.005);

        Calendar asOf = statement.getLedgerBalanceAsOf();
        assertNotNull(asOf, "DTASOF is what distinguishes a newer statement from a merely wider one");
        assertEquals(2026, asOf.get(Calendar.YEAR));
        assertEquals(Calendar.SEPTEMBER, asOf.get(Calendar.MONTH));
        assertEquals(4, asOf.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    @DisplayName("The wider statement reads as the older one it is")
    void testWiderStatementIsOlder() throws Exception {

        // The same account three days earlier and $99.95 short, because it predates the 08-30
        // charge.  Read as a bare number this looks like a correction; read with its date it is
        // plainly stale.
        QfxStatement wider = parse(ofx("""
                <LEDGERBAL>
                <BALAMT>-11886.30
                <DTASOF>20260901080239
                </LEDGERBAL>
                """));

        assertEquals(-11886.30, wider.getLedgerBalance(), 0.005);
        assertEquals(1, wider.getLedgerBalanceAsOf().get(Calendar.DAY_OF_MONTH));
    }

    @Test
    @DisplayName("A statement with no ledger balance yields no date, and does not throw")
    void testMissingLedgerBalance() throws Exception {

        // DTASOF and LEDGERBAL are both optional.  A file without them must parse, so that the
        // balance question falls back to asking rather than failing the import.
        QfxStatement statement = parse(ofx(""));

        assertNull(statement.getLedgerBalanceAsOf(),
                "no balance means no date to compare, which the caller treats as unknown");
    }
}
