package uk.ac.ebi.protvar.input.parser.id;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.input.parser.variantid.CosmicParser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CosmicParserTest {

    @Test
    void test_valid() {
        assertTrue(CosmicParser.valid("COSV123456789")); // upper case
        assertTrue(CosmicParser.valid("cosv123456789")); // lower case / ignore case
        assertTrue(CosmicParser.valid("COSM123456789"));
        assertTrue(CosmicParser.valid("COSN123456789"));
    }

    @Test
    void test_invalid() {
        assertFalse(CosmicParser.valid(""));  // empty
        assertFalse(CosmicParser.valid("COS123456789"));  // invalid prefix
        assertFalse(CosmicParser.valid("COSXXX"));  // invalid prefix
        assertFalse(CosmicParser.valid("COSMXXX"));  // invalid post-fix
    }

}