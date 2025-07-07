package uk.ac.ebi.protvar.input.format.id;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.input.parser.variantid.ClinvarInputParser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClinVarIDTest {

    @Test
    void test_valid() {
        assertTrue(ClinvarInputParser.valid("RCV123456789")); // upper case
        assertTrue(ClinvarInputParser.valid("rcv123456789")); // lower case / ignore case
    }

    @Test
    void test_invalid() {
        assertFalse(ClinvarInputParser.valid(""));  // empty
        assertFalse(ClinvarInputParser.valid("xy123456789"));  // invalid prefix
        assertFalse(ClinvarInputParser.valid("rc123456789"));  // invalid prefix
        assertFalse(ClinvarInputParser.valid("rcvXXX"));  // invalid post-fix
    }

}