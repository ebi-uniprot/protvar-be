package uk.ac.ebi.protvar.input.format.genomic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.ac.ebi.protvar.input.ErrorConstants;

import static org.junit.jupiter.api.Assertions.*;

class GnomadTest {

    @Test
    void test_matchesPattern() {
        assertFalse(Gnomad.matchesPattern(""));  // empty
        assertFalse(Gnomad.matchesPattern("---")); // no space between dash
        assertFalse(Gnomad.matchesPattern(" - - - ")); // should not work ? in ?-?-?-? should be non-space
        assertFalse(Gnomad.matchesPattern("X 149498202 C G"));  // diff sep
        assertTrue(Gnomad.matchesPattern("X-X-X-X")); // any char between dashes
        assertTrue(Gnomad.matchesPattern("x-149498202-c-g")); // an actual valid input
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "X-149498202-C-G", // upper case
            "x-149498202-c-g" // lower case / ignore case
    })
    void test_valid(String inputStr) {
        Gnomad userInput = Gnomad.parse(inputStr);
        assertParsedInput(true, inputStr, "X", 149498202, "C", "G", "X-149498202", userInput);
    }

    @Test
    void test_parse_invalid_chr() {
        Gnomad g = Gnomad.parse("H-149498202-C-G");
        assertTrue(g.getErrors().stream().anyMatch(ErrorConstants.INVALID_CHR.getErrorMessage()::equalsIgnoreCase));
    }

    @Test
    void test_parse_invalid_pos() {
        Gnomad g = Gnomad.parse("X-pos-C-G");
        assertTrue(g.getErrors().stream().anyMatch(ErrorConstants.INVALID_POS.getErrorMessage()::equalsIgnoreCase));
    }

    @Test
    void test_parse_invalid_ref() {
        Gnomad g = Gnomad.parse("X-149498202-E-G");
        assertTrue(g.getErrors().stream().anyMatch(ErrorConstants.INVALID_REF.getErrorMessage()::equalsIgnoreCase));
    }

    @Test
    void test_parse_invalid_base() {
        Gnomad g = Gnomad.parse("X-149498202-E-Y");
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
        assertEquals(group, actual.groupByChrAndPos());
    }
}