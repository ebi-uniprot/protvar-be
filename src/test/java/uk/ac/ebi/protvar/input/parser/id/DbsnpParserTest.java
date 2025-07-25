package uk.ac.ebi.protvar.input.parser.id;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import uk.ac.ebi.protvar.input.VariantFormat;
import uk.ac.ebi.protvar.input.VariantInput;
import uk.ac.ebi.protvar.input.parser.variantid.DbsnpParser;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for DbSNP ID Parser
 */
public class DbsnpParserTest {

    @Test
    @DisplayName("Test Valid DbSNP IDs")
    public void testValidDbsnpIds() {
        // Basic valid formats
        assertValid("rs123456");
        assertValid("rs1");
        assertValid("rs999999999");
        assertValid("rs12345678901234567890"); // Very long ID

        // Case insensitive
        assertValid("RS123456");
        assertValid("Rs123456");
        assertValid("rS123456");

        // Edge cases
        assertValid("rs1000000000"); // 10 digits
    }

    @Test
    @DisplayName("Test Invalid DbSNP IDs")
    public void testInvalidDbsnpIds() {
        // Missing rs prefix
        assertInvalid("123456");
        assertInvalid("dbsnp123456");

        // Wrong prefix
        assertInvalid("ss123456");    // SRA submission ID, not dbSNP
        assertInvalid("rv123456");    // Wrong prefix

        // No digits after rs
        assertInvalid("rs");
        assertInvalid("rsabc");
        assertInvalid("rs123abc");

        // Invalid characters
        assertInvalid("rs123-456");
        assertInvalid("rs123.456");
        assertInvalid("rs123_456");
        assertInvalid("rs 123456");   // Space

        // Empty/null
        assertInvalid("");
    }

    @Test
    @DisplayName("Test Structural Validation")
    public void testStructuralValidation() {
        assertTrue(DbsnpParser.matchesStructure("rs123456"));
        assertTrue(DbsnpParser.matchesStructure("RS123456"));
        assertTrue(DbsnpParser.matchesStructure("rs1"));

        assertFalse(DbsnpParser.matchesStructure("123456"));
        assertFalse(DbsnpParser.matchesStructure("ss123456"));
        assertFalse(DbsnpParser.matchesStructure("rs"));
        assertFalse(DbsnpParser.matchesStructure(""));
        assertFalse(DbsnpParser.matchesStructure(null));
    }

    private void assertValid(String input) {
        VariantInput result = DbsnpParser.parse(input);
        assertNotNull(result, "Parser should never return null: " + input);
        assertTrue(result.isValid(), "Should be valid: " + input);
        assertEquals(VariantFormat.DBSNP, result.getFormat(), "Wrong format: " + input);
        assertEquals(input != null ? input.toLowerCase() : null, result.getInputStr(), "Input should be normalized to lowercase: " + input);
    }

    private void assertInvalid(String input) {
        VariantInput result = DbsnpParser.parse(input);
        assertNotNull(result, "Parser should never return null: " + input);
        assertFalse(result.isValid(), "Should be invalid: " + input);
        assertFalse(result.getErrors().isEmpty(), "Invalid input should have errors: " + input);
    }
}