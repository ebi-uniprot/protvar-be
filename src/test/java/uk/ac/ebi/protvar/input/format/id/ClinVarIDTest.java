package uk.ac.ebi.protvar.input.format.id;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClinVarIDTest {

    @Test
    void test_valid() {
        assertTrue(ClinVarID.isValid("RCV123456789")); // upper case
        assertTrue(ClinVarID.isValid("rcv123456789")); // lower case / ignore case
    }

    @Test
    void test_invalid() {
        assertFalse(ClinVarID.isValid(""));  // empty
        assertFalse(ClinVarID.isValid("xy123456789"));  // invalid prefix
        assertFalse(ClinVarID.isValid("rc123456789"));  // invalid prefix
        assertFalse(ClinVarID.isValid("rcvXXX"));  // invalid post-fix
    }

}