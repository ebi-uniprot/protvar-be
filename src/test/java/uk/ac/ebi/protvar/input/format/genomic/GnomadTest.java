package uk.ac.ebi.protvar.input.format.genomic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.parser.genomic.GnomadInputParser;

import static org.junit.jupiter.api.Assertions.*;

class GnomadTest {

    @Test
    void test_matchesPattern() {
        assertFalse(GnomadInputParser.matchesPattern(""));  // empty
        assertFalse(GnomadInputParser.matchesPattern("---")); // no space between dash
        assertFalse(GnomadInputParser.matchesPattern(" - - - ")); // should not work ? in ?-?-?-? should be non-space
        assertFalse(GnomadInputParser.matchesPattern("X 149498202 C G"));  // diff sep
        assertTrue(GnomadInputParser.matchesPattern("X-X-X-X")); // any char between dashes
        assertTrue(GnomadInputParser.matchesPattern("x-149498202-c-g")); // an actual valid input
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "X-149498202-C-G", // upper case
            "x-149498202-c-g" // lower case / ignore case
    })
    void test_valid(String inputStr) {
        Gnomad userInput = GnomadInputParser.parse(inputStr);
        assertParsedInput(true, inputStr, "X", 149498202, "C", "G", "X:149498202", userInput);
    }

    @Test
    void test_parse_invalid_chr() {
        Gnomad g = GnomadInputParser.parse("H-149498202-C-G");
        assertTrue(g.getErrors().stream().anyMatch(ErrorConstants.INVALID_CHR.getErrorMessage()::equalsIgnoreCase));
    }

    @Test
    void test_parse_invalid_pos() {
        Gnomad g = GnomadInputParser.parse("X-pos-C-G");
        assertTrue(g.getErrors().stream().anyMatch(ErrorConstants.INVALID_POS.getErrorMessage()::equalsIgnoreCase));
    }

    @Test
    void test_parse_invalid_ref() {
        Gnomad g = GnomadInputParser.parse("X-149498202-E-G");
        assertTrue(g.getErrors().stream().anyMatch(ErrorConstants.INVALID_REF.getErrorMessage()::equalsIgnoreCase));
    }

    @Test
    void test_parse_invalid_base() {
        Gnomad g = GnomadInputParser.parse("X-149498202-E-Y");
        assertTrue(g.getErrors().stream().anyMatch(ErrorConstants.INVALID_REF.getErrorMessage()::equalsIgnoreCase));
        assertTrue(g.getErrors().stream().anyMatch(ErrorConstants.INVALID_ALT.getErrorMessage()::equalsIgnoreCase));
    }

    private void assertParsedInput(boolean valid, String inputStr, String chr, Integer pos, String ref, String alt,
                                   String group, Gnomad actual) {
        assertEquals(valid, actual.isValid());
        assertEquals(inputStr, actual.getInputStr());
        assertEquals(chr, actual.getChr());
        assertEquals(pos, actual.getPos());
        assertEquals(ref, actual.getRef());
        assertEquals(alt, actual.getAlt());
        assertEquals(group, actual.getVariantKey());
    }
}