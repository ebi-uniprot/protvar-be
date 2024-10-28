package uk.ac.ebi.protvar.input.format.id;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClinVarIDTest {

    @Test
    void test_valid() {
        assertTrue(ClinVarID.valid("RCV123456789")); // upper case
        assertTrue(ClinVarID.valid("rcv123456789")); // lower case / ignore case
    }

    @Test
    void test_invalid() {
        assertFalse(ClinVarID.valid(""));  // empty
        assertFalse(ClinVarID.valid("xy123456789"));  // invalid prefix
        assertFalse(ClinVarID.valid("rc123456789"));  // invalid prefix
        assertFalse(ClinVarID.valid("rcvXXX"));  // invalid post-fix
    }

}