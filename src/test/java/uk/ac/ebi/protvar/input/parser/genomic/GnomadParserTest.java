package uk.ac.ebi.protvar.input.parser.genomic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.GenomicInput;

import static org.junit.jupiter.api.Assertions.*;

class GnomadParserTest {

    @Test
    void test_matchesPattern() {
        assertFalse(GnomadParser.matchesPattern(""));  // empty
        assertFalse(GnomadParser.matchesPattern("---")); // no space between dash
        assertFalse(GnomadParser.matchesPattern(" - - - ")); // should not work ? in ?-?-?-? should be non-space
        assertFalse(GnomadParser.matchesPattern("X 149498202 C G"));  // diff sep
        assertTrue(GnomadParser.matchesPattern("X-X-X-X")); // any char between dashes
        assertTrue(GnomadParser.matchesPattern("x-149498202-c-g")); // an actual valid input
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "X-149498202-C-G", // upper case
            "x-149498202-c-g" // lower case / ignore case
    })
    void test_valid(String inputStr) {
        GenomicInput userInput = GnomadParser.parse(inputStr);
        assertParsedInput(true, inputStr, "X", 149498202, "C", "G", userInput);
    }

    @Test
    void test_parse_invalid_chr() {
        GenomicInput g = GnomadParser.parse("H-149498202-C-G");
        assertTrue(g.getErrors().stream().anyMatch(ErrorConstants.INVALID_CHR.getErrorMessage()::equalsIgnoreCase));
    }

    @Test
    void test_parse_invalid_pos() {
        GenomicInput g = GnomadParser.parse("X-pos-C-G");
        assertTrue(g.getErrors().stream().anyMatch(ErrorConstants.INVALID_POS.getErrorMessage()::equalsIgnoreCase));
    }

    @Test
    void test_parse_invalid_ref() {
        GenomicInput g = GnomadParser.parse("X-149498202-E-G");
        assertTrue(g.getErrors().stream().anyMatch(ErrorConstants.INVALID_REF.getErrorMessage()::equalsIgnoreCase));
    }

    @Test
    void test_parse_invalid_base() {
        GenomicInput g = GnomadParser.parse("X-149498202-E-Y");
        assertTrue(g.getErrors().stream().anyMatch(ErrorConstants.INVALID_REF.getErrorMessage()::equalsIgnoreCase));
        assertTrue(g.getErrors().stream().anyMatch(ErrorConstants.INVALID_ALT.getErrorMessage()::equalsIgnoreCase));
    }

    private void assertParsedInput(boolean valid, String inputStr, String chr, Integer pos, String ref, String alt,
                                   GenomicInput actual) {
        assertEquals(valid, actual.isValid());
        assertEquals(inputStr, actual.getInputStr());
        assertEquals(chr, actual.getChr());
        assertEquals(pos, actual.getPos());
        assertEquals(ref, actual.getRef());
        assertEquals(alt, actual.getAlt());
    }
}