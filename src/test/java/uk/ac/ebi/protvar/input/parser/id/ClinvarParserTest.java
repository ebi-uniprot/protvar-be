package uk.ac.ebi.protvar.input.parser.id;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import uk.ac.ebi.protvar.input.VariantFormat;
import uk.ac.ebi.protvar.input.VariantInput;
import uk.ac.ebi.protvar.input.parser.variantid.ClinvarParser;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for ClinVar ID Parser
 */
public class ClinvarParserTest {

    @Test
    @DisplayName("Test Valid ClinVar IDs")
    public void testValidClinvarIds() {
        // RCV format
        assertValid("RCV000000001");
        assertValid("RCV000123456");
        assertValid("RCV999999999");

        // VCV format
        assertValid("VCV000000001");
        assertValid("VCV000123456");
        assertValid("VCV999999999");

        // With version numbers
        assertValid("RCV000123456.1");
        assertValid("VCV000123456.2");
        assertValid("RCV000123456.10");
        assertValid("VCV000123456.123");

        // Case insensitive
        assertValid("rcv000123456");
        assertValid("vcv000123456");
        assertValid("Rcv000123456.1");
        assertValid("VcV000123456.2");
    }

    @Test
    @DisplayName("Test Invalid ClinVar IDs")
    public void testInvalidClinvarIds() {
        // Wrong prefix
        assertInvalid("SCV000123456");  // Submission ID, not supported
        assertInvalid("XCV000123456");  // Invalid prefix
        assertInvalid("RCX000123456");  // Wrong prefix

        // Wrong digit count
        assertInvalid("RCV12345678");   // 8 digits (need 9)
        assertInvalid("RCV1234567890"); // 10 digits (need 9)
        assertInvalid("VCV00123456");   // 8 digits
        assertInvalid("VCV0123456789"); // 10 digits

        // Missing digits
        assertInvalid("RCV");
        assertInvalid("VCV");
        assertInvalid("RCV000");

        // Invalid characters
        assertInvalid("RCV000123abc");
        assertInvalid("VCV000123-456");
        assertInvalid("RCV 000123456");  // Space

        // Invalid version format
        assertInvalid("RCV000123456.");  // Dot but no version
        assertInvalid("RCV000123456.a"); // Non-numeric version

        // Empty/null
        assertInvalid("");
        assertInvalid(null);
    }

    @Test
    @DisplayName("Test Structural Validation")
    public void testStructuralValidation() {
        assertTrue(ClinvarParser.matchesStructure("RCV000123456"));
        assertTrue(ClinvarParser.matchesStructure("VCV000123456"));
        assertTrue(ClinvarParser.matchesStructure("rcv000123456"));

        assertFalse(ClinvarParser.matchesStructure("SCV000123456"));
        assertFalse(ClinvarParser.matchesStructure("RCV"));          // No digits
        assertFalse(ClinvarParser.matchesStructure("RCVABC"));       // No digits, letters instead
        assertFalse(ClinvarParser.matchesStructure("000123456"));
        assertFalse(ClinvarParser.matchesStructure(""));
        assertFalse(ClinvarParser.matchesStructure(null));
    }

    @Test
    @DisplayName("Test Prefix Extraction")
    public void testPrefixExtraction() {
        assertEquals("RCV", ClinvarParser.getPrefix("RCV000123456"));
        assertEquals("VCV", ClinvarParser.getPrefix("VCV000123456"));
        assertEquals("RCV", ClinvarParser.getPrefix("rcv000123456"));
        assertEquals("VCV", ClinvarParser.getPrefix("vcv000123456"));

        assertEquals("", ClinvarParser.getPrefix("SCV000123456"));
        assertEquals("", ClinvarParser.getPrefix("ABC123456"));
        assertEquals("", ClinvarParser.getPrefix("RC"));
        assertEquals("", ClinvarParser.getPrefix(""));
        assertEquals("", ClinvarParser.getPrefix(null));
    }

    @Test
    @DisplayName("Test Version Stripping")
    public void testVersionStripping() {
        assertEquals("RCV000123456", ClinvarParser.stripVersion("RCV000123456.1"));
        assertEquals("VCV000123456", ClinvarParser.stripVersion("VCV000123456.10"));
        assertEquals("RCV000123456", ClinvarParser.stripVersion("RCV000123456"));

        assertNull(ClinvarParser.stripVersion(null));
        assertEquals("", ClinvarParser.stripVersion(""));
    }

    private void assertValid(String input) {
        VariantInput result = ClinvarParser.parse(input);
        assertNotNull(result, "Parser should never return null: " + input);
        assertTrue(result.isValid(), "Should be valid: " + input);
        assertEquals(VariantFormat.CLINVAR, result.getFormat(), "Wrong format: " + input);
        assertEquals(input, result.getInputStr(), "Input string should be preserved: " + input);
    }

    private void assertInvalid(String input) {
        VariantInput result = ClinvarParser.parse(input);
        assertNotNull(result, "Parser should never return null: " + input);
        assertFalse(result.isValid(), "Should be invalid: " + input);
        assertFalse(result.getErrors().isEmpty(), "Invalid input should have errors: " + input);
    }
}