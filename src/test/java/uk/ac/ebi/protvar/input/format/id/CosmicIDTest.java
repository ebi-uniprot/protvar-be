package uk.ac.ebi.protvar.input.format.id;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CosmicIDTest {

    @Test
    void test_valid() {
        assertTrue(CosmicID.isValid("COSV123456789")); // upper case
        assertTrue(CosmicID.isValid("cosv123456789")); // lower case / ignore case
        assertTrue(CosmicID.isValid("COSM123456789"));
        assertTrue(CosmicID.isValid("COSN123456789"));
    }

    @Test
    void test_invalid() {
        assertFalse(CosmicID.isValid(""));  // empty
        assertFalse(CosmicID.isValid("COS123456789"));  // invalid prefix
        assertFalse(CosmicID.isValid("COSXXX"));  // invalid prefix
        assertFalse(CosmicID.isValid("COSMXXX"));  // invalid post-fix
    }

}