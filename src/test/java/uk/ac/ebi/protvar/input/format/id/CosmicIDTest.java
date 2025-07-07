package uk.ac.ebi.protvar.input.format.id;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.input.parser.variantid.CosmicInputParser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CosmicIDTest {

    @Test
    void test_valid() {
        assertTrue(CosmicInputParser.valid("COSV123456789")); // upper case
        assertTrue(CosmicInputParser.valid("cosv123456789")); // lower case / ignore case
        assertTrue(CosmicInputParser.valid("COSM123456789"));
        assertTrue(CosmicInputParser.valid("COSN123456789"));
    }

    @Test
    void test_invalid() {
        assertFalse(CosmicInputParser.valid(""));  // empty
        assertFalse(CosmicInputParser.valid("COS123456789"));  // invalid prefix
        assertFalse(CosmicInputParser.valid("COSXXX"));  // invalid prefix
        assertFalse(CosmicInputParser.valid("COSMXXX"));  // invalid post-fix
    }

}