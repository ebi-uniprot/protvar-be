package uk.ac.ebi.protvar.input.format.genomic;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.input.ErrorConstants;

import static org.junit.jupiter.api.Assertions.*;

class GnomadTest {

    @Test
    void test_preCheck() {
        assertFalse(Gnomad.preCheck("---")); // no space between dash
        assertTrue(Gnomad.preCheck(" - - - ")); // should work as fits general pattern _-_-_-_
        assertTrue(Gnomad.preCheck("X-X-X-X")); // any char between dashes
        assertTrue(Gnomad.preCheck("x-149498202-c-g")); // an actual valid input
    }

    @Test
    void test_valid() {
        assertTrue(Gnomad.isValid("X-149498202-C-G")); // upper case
        assertTrue(Gnomad.isValid("x-149498202-c-g")); // lower case / ignore case
    }

    @Test
    void test_invalid() {
        assertFalse(Gnomad.isValid(""));  // empty
        assertFalse(Gnomad.isValid("X 149498202 C G"));  // diff sep
        assertFalse(Gnomad.isValid("H-149498202-C-G"));  // invalid chr
        assertFalse(Gnomad.isValid("X-pos-C-G"));  // invalid pos
        assertFalse(Gnomad.isValid("X-149498202-E-G"));  // invalid dna
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
    void test_parse_invalid_base() {
        Gnomad g = Gnomad.parse("X-149498202-E-Y");
        assertTrue(g.getErrors().stream().anyMatch(ErrorConstants.INVALID_REF.getErrorMessage()::equalsIgnoreCase));
        assertTrue(g.getErrors().stream().anyMatch(ErrorConstants.INVALID_ALT.getErrorMessage()::equalsIgnoreCase));
    }

}