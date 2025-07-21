package uk.ac.ebi.protvar.input.parser.id;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.input.parser.variantid.DbsnpParser;

import static org.junit.jupiter.api.Assertions.*;

class DbsnpParserTest {

    @Test
    void test_valid() {
        assertTrue(DbsnpParser.valid("rs123456789")); // upper case
        assertTrue(DbsnpParser.valid("RS123456789")); // lower case / ignore case
    }

    @Test
    void test_invalid() {
        assertFalse(DbsnpParser.valid(""));  // empty
        assertFalse(DbsnpParser.valid("ys123456789"));  // invalid prefix
        assertFalse(DbsnpParser.valid("r123456789"));  // invalid prefix
        assertFalse(DbsnpParser.valid("rsXXX"));  // invalid post-fix
    }

}