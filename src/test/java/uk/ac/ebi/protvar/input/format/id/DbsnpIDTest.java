package uk.ac.ebi.protvar.input.format.id;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DbsnpIDTest {

    @Test
    void test_valid() {
        assertTrue(DbsnpID.isValid("rs123456789")); // upper case
        assertTrue(DbsnpID.isValid("RS123456789")); // lower case / ignore case
    }

    @Test
    void test_invalid() {
        assertFalse(DbsnpID.isValid(""));  // empty
        assertFalse(DbsnpID.isValid("ys123456789"));  // invalid prefix
        assertFalse(DbsnpID.isValid("r123456789"));  // invalid prefix
        assertFalse(DbsnpID.isValid("rsXXX"));  // invalid post-fix
    }

}