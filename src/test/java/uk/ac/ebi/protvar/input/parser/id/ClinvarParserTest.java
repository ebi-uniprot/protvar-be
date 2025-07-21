package uk.ac.ebi.protvar.input.parser.id;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.input.parser.variantid.ClinvarParser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClinvarParserTest {

    @Test
    void test_valid() {
        assertTrue(ClinvarParser.valid("RCV123456789")); // upper case
        assertTrue(ClinvarParser.valid("rcv123456789")); // lower case / ignore case
    }

    @Test
    void test_invalid() {
        assertFalse(ClinvarParser.valid(""));  // empty
        assertFalse(ClinvarParser.valid("xy123456789"));  // invalid prefix
        assertFalse(ClinvarParser.valid("rc123456789"));  // invalid prefix
        assertFalse(ClinvarParser.valid("rcvXXX"));  // invalid post-fix
    }

}