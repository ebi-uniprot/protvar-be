package uk.ac.ebi.protvar.input.parser.id;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import uk.ac.ebi.protvar.input.VariantFormat;
import uk.ac.ebi.protvar.input.VariantInput;
import uk.ac.ebi.protvar.input.parser.variantid.CosmicParser;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for COSMIC ID Parser
 */
public class CosmicParserTest {

    @Test
    @DisplayName("Test Valid COSMIC IDs")
    public void testValidCosmicIds() {
        // New COSV format
        assertValid("COSV12345678");
        assertValid("COSV1");
        assertValid("COSV999999999");
        assertValid("COSV123456789012345"); // Very long

        // Legacy COSM format
        assertValid("COSM123456");
        assertValid("COSM1");
        assertValid("COSM999999999");

        // Legacy COSN format
        assertValid("COSN123456");
        assertValid("COSN1");
        assertValid("COSN999999999");

        // Case insensitive
        assertValid("cosv12345678");
        assertValid("cosm123456");
        assertValid("cosn123456");
        assertValid("Cosv12345678");
        assertValid("COSM123456");
    }

    @Test
    @DisplayName("Test Invalid COSMIC IDs")
    public void testInvalidCosmicIds() {
        // Wrong prefix
        assertInvalid("COSX123456");  // Invalid prefix
        assertInvalid("COST123456");  // Invalid prefix
        assertInvalid("COS123456");   // Too short prefix

        // Missing digits
        assertInvalid("COSV");
        assertInvalid("COSM");
        assertInvalid("COSN");

        // Invalid characters
        assertInvalid("COSV123abc");
        assertInvalid("COSM123-456");
        assertInvalid("COSN123.456");
        assertInvalid("COSV 123456");  // Space

        // Empty/null
        assertInvalid("");
        assertInvalid(null);
    }

    @Test
    @DisplayName("Test Structural Validation")
    public void testStructuralValidation() {
        assertTrue(CosmicParser.matchesStructure("COSV12345678"));
        assertTrue(CosmicParser.matchesStructure("COSM123456"));
        assertTrue(CosmicParser.matchesStructure("COSN123456"));
        assertTrue(CosmicParser.matchesStructure("cosv12345678"));

        assertFalse(CosmicParser.matchesStructure("COSX123456"));
        assertFalse(CosmicParser.matchesStructure("COS123456"));
        assertFalse(CosmicParser.matchesStructure("COSV"));
        assertFalse(CosmicParser.matchesStructure("12345678"));
        assertFalse(CosmicParser.matchesStructure(""));
        assertFalse(CosmicParser.matchesStructure(null));
    }

    @Test
    @DisplayName("Test Prefix Extraction")
    public void testPrefixExtraction() {
        assertEquals("COSV", CosmicParser.getPrefix("COSV12345678"));
        assertEquals("COSM", CosmicParser.getPrefix("COSM123456"));
        assertEquals("COSN", CosmicParser.getPrefix("COSN123456"));
        assertEquals("COSV", CosmicParser.getPrefix("cosv12345678"));

        assertEquals("", CosmicParser.getPrefix("COSX123456"));
        assertEquals("", CosmicParser.getPrefix("ABC123456"));
        assertEquals("", CosmicParser.getPrefix("COS"));
        assertEquals("", CosmicParser.getPrefix(""));
        assertEquals("", CosmicParser.getPrefix(null));
    }

    @Test
    @DisplayName("Test Different COSMIC Formats")
    public void testDifferentCosmicFormats() {
        // Test that all three prefixes work
        assertValid("COSV55555555");  // New variant format
        assertValid("COSM3312345");   // Legacy mutation format
        assertValid("COSN1234567");   // Legacy nucleotide change format

        // Ensure they're all recognized as COSMIC format
        assertEquals(VariantFormat.COSMIC, CosmicParser.parse("COSV55555555").getFormat());
        assertEquals(VariantFormat.COSMIC, CosmicParser.parse("COSM3312345").getFormat());
        assertEquals(VariantFormat.COSMIC, CosmicParser.parse("COSN1234567").getFormat());
    }

    private void assertValid(String input) {
        VariantInput result = CosmicParser.parse(input);
        assertNotNull(result, "Parser should never return null: " + input);
        assertTrue(result.isValid(), "Should be valid: " + input);
        assertEquals(VariantFormat.COSMIC, result.getFormat(), "Wrong format: " + input);
        assertEquals(input, result.getInputStr(), "Input string should be preserved: " + input);
    }

    private void assertInvalid(String input) {
        VariantInput result = CosmicParser.parse(input);
        assertNotNull(result, "Parser should never return null: " + input);
        assertFalse(result.isValid(), "Should be invalid: " + input);
        assertFalse(result.getErrors().isEmpty(), "Invalid input should have errors: " + input);
    }
}