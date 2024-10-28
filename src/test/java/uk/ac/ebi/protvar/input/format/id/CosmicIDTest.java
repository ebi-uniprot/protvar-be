package uk.ac.ebi.protvar.input.format.id;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CosmicIDTest {

    @Test
    void test_valid() {
        assertTrue(CosmicID.valid("COSV123456789")); // upper case
        assertTrue(CosmicID.valid("cosv123456789")); // lower case / ignore case
        assertTrue(CosmicID.valid("COSM123456789"));
        assertTrue(CosmicID.valid("COSN123456789"));
    }

    @Test
    void test_invalid() {
        assertFalse(CosmicID.valid(""));  // empty
        assertFalse(CosmicID.valid("COS123456789"));  // invalid prefix
        assertFalse(CosmicID.valid("COSXXX"));  // invalid prefix
        assertFalse(CosmicID.valid("COSMXXX"));  // invalid post-fix
    }

}