package uk.ac.ebi.protvar.input.format.id;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.input.parser.variantid.DbsnpInputParser;

import static org.junit.jupiter.api.Assertions.*;

class DbsnpIDTest {

    @Test
    void test_valid() {
        assertTrue(DbsnpInputParser.valid("rs123456789")); // upper case
        assertTrue(DbsnpInputParser.valid("RS123456789")); // lower case / ignore case
    }

    @Test
    void test_invalid() {
        assertFalse(DbsnpInputParser.valid(""));  // empty
        assertFalse(DbsnpInputParser.valid("ys123456789"));  // invalid prefix
        assertFalse(DbsnpInputParser.valid("r123456789"));  // invalid prefix
        assertFalse(DbsnpInputParser.valid("rsXXX"));  // invalid post-fix
    }

}