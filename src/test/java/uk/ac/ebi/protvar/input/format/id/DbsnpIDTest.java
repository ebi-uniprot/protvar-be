package uk.ac.ebi.protvar.input.format.id;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DbsnpIDTest {

    @Test
    void test_valid() {
        assertTrue(DbsnpID.valid("rs123456789")); // upper case
        assertTrue(DbsnpID.valid("RS123456789")); // lower case / ignore case
    }

    @Test
    void test_invalid() {
        assertFalse(DbsnpID.valid(""));  // empty
        assertFalse(DbsnpID.valid("ys123456789"));  // invalid prefix
        assertFalse(DbsnpID.valid("r123456789"));  // invalid prefix
        assertFalse(DbsnpID.valid("rsXXX"));  // invalid post-fix
    }

}