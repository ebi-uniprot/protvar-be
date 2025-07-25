package uk.ac.ebi.protvar.input.parser.genomic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.input.GenomicInput;

import static org.junit.jupiter.api.Assertions.*;

public class GnomadParserTest {

    @Test
    @DisplayName("Test Valid GnomAD Format - Basic Cases")
    public void testValidBasicCases() {
        // Simple SNVs
        assertResult("1-12345-A-T", "1", 12345, "A", "T");
        assertResult("chr1-12345-A-T", "1", 12345, "A", "T");
        assertResult("22-999999-G-C", "22", 999999, "G", "C");
        assertResult("X-54321-C-G", "X", 54321, "C", "G");
        assertResult("Y-1000-T-A", "Y", 1000, "T", "A");

        // Mitochondrial chromosomes
        assertResult("MT-16569-T-C", "MT", 16569, "T", "C");
        assertResult("M-16569-A-G", "MT", 16569, "A", "G");
        assertResult("chrMT-16569-C-T", "MT", 16569, "C", "T");
    }

    @Test
    @DisplayName("Test Mitochondrial Aliases")
    public void testMitochondrialAliases() {
        assertResult("mit-302-A-T", "MT", 302, "A", "T");
        assertResult("mtDNA-302-G-C", "MT", 302, "G", "C");
        assertResult("mitochondria-302-T-A", "MT", 302, "T", "A");
        assertResult("mitochondrion-302-C-G", "MT", 302, "C", "G");

        // Mixed case
        assertResult("MIT-302-A-T", "MT", 302, "A", "T");
        assertResult("MtDnA-302-G-C", "MT", 302, "G", "C");
        assertResult("MITOCHONDRIA-302-T-A", "MT", 302, "T", "A");
    }

    @Test
    @DisplayName("Test Single Base Variants Only")
    public void testSingleBaseVariants() {
        // All valid single base combinations
        assertResult("1-12345-A-T", "1", 12345, "A", "T");
        assertResult("1-12345-A-C", "1", 12345, "A", "C");
        assertResult("1-12345-A-G", "1", 12345, "A", "G");
        assertResult("1-12345-T-A", "1", 12345, "T", "A");
        assertResult("1-12345-T-C", "1", 12345, "T", "C");
        assertResult("1-12345-T-G", "1", 12345, "T", "G");
        assertResult("1-12345-C-A", "1", 12345, "C", "A");
        assertResult("1-12345-C-T", "1", 12345, "C", "T");
        assertResult("1-12345-C-G", "1", 12345, "C", "G");
        assertResult("1-12345-G-A", "1", 12345, "G", "A");
        assertResult("1-12345-G-T", "1", 12345, "G", "T");
        assertResult("1-12345-G-C", "1", 12345, "G", "C");

        // Different chromosomes
        assertResult("X-54321-A-T", "X", 54321, "A", "T");
        assertResult("Y-1000-C-G", "Y", 1000, "C", "G");
        assertResult("MT-16569-T-C", "MT", 16569, "T", "C");
    }

    @Test
    @DisplayName("Test Chromosome Normalization")
    public void testChromosomeNormalization() {
        // Leading zeros removal
        assertResult("chr01-12345-A-T", "1", 12345, "A", "T");
        assertResult("chr001-12345-G-C", "1", 12345, "G", "C");
        assertResult("00022-999999-T-A", "22", 999999, "T", "A");

        // Chr prefix removal
        assertResult("chr1-12345-A-T", "1", 12345, "A", "T");
        assertResult("chrX-54321-G-C", "X", 54321, "G", "C");
        assertResult("chrY-1000-T-A", "Y", 1000, "T", "A");

        // Case insensitive chromosomes
        assertResult("CHR1-12345-A-T", "1", 12345, "A", "T");
        assertResult("x-54321-G-C", "X", 54321, "G", "C");
        assertResult("y-1000-T-A", "Y", 1000, "T", "A");
    }

    @Test
    @DisplayName("Test Position Normalization")
    public void testPositionNormalization() {
        // Leading zeros in position
        assertResult("1-00001-A-T", "1", 1, "A", "T");
        assertResult("1-00123-G-C", "1", 123, "G", "C");
        assertResult("1-000456-T-A", "1", 456, "T", "A");

        // Large positions
        assertResult("1-999999999-A-T", "1", 999999999, "A", "T");
        assertResult("X-123456789-G-C", "X", 123456789, "G", "C");
    }

    @Test
    @DisplayName("Test Base Normalization")
    public void testBaseNormalization() {
        // Lowercase bases
        assertResult("1-12345-a-t", "1", 12345, "A", "T");
        assertResult("1-12345-g-c", "1", 12345, "G", "C");
        assertResult("1-12345-c-a", "1", 12345, "C", "A");
        assertResult("1-12345-t-g", "1", 12345, "T", "G");

        // Mixed case bases
        assertResult("1-12345-A-t", "1", 12345, "A", "T");
        assertResult("X-54321-g-C", "X", 54321, "G", "C");
        assertResult("Y-1000-T-a", "Y", 1000, "T", "A");
    }

    @Test
    @DisplayName("Test Pattern Matching")
    public void testPatternMatching() {
        // Valid patterns should match
        assertTrue(GnomadParser.matchesPattern("1-12345-A-T"));
        assertTrue(GnomadParser.matchesPattern("chr1-12345-G-C"));
        assertTrue(GnomadParser.matchesPattern("X-54321-C-A"));
        assertTrue(GnomadParser.matchesPattern("MT-16569-T-C"));
        assertTrue(GnomadParser.matchesPattern("mitochondria-302-A-G"));

        // Invalid patterns should not match
        assertFalse(GnomadParser.matchesPattern("1-12345-A"));     // Missing ALT
        assertFalse(GnomadParser.matchesPattern("1:12345:A:T"));   // Wrong separator
        assertFalse(GnomadParser.matchesPattern("1 12345 A T"));   // Wrong separator
        assertFalse(GnomadParser.matchesPattern("12345-A-T"));     // Missing CHR
        assertFalse(GnomadParser.matchesPattern("1-0-A-T"));       // Invalid position
        assertFalse(GnomadParser.matchesPattern("1-12345-ATCG-T"));// Multi-base not allowed
        assertFalse(GnomadParser.matchesPattern("1-12345-A-TCGC"));// Multi-base not allowed
    }

    @Test
    @DisplayName("Test Invalid Formats - Missing Components")
    public void testInvalidMissingComponents() {
        // Missing ALT
        assertInvalid("1-12345-A");
        assertInvalid("chr1-12345-A");
        assertInvalid("X-54321-G");

        // Missing REF and ALT
        assertInvalid("1-12345");
        assertInvalid("chr1-12345");
        assertInvalid("MT-16569");

        // Missing REF, ALT, and POS
        assertInvalid("1");
        assertInvalid("chr1");
        assertInvalid("X");

        // Missing CHR
        assertInvalid("12345-A-T");
        assertInvalid("54321-G-C");

        // Empty components
        assertInvalid("1--A-T");        // Empty position
        assertInvalid("1-12345--T");    // Empty ref
        assertInvalid("1-12345-A-");    // Empty alt
        assertInvalid("-12345-A-T");    // Empty chr
    }

    @Test
    @DisplayName("Test Invalid Formats - Wrong Separators")
    public void testInvalidWrongSeparators() {
        // Colon separator (GenomicParser format)
        assertInvalid("1:12345:A:T");
        assertInvalid("chr1:12345:A:T");
        assertInvalid("X:54321:G:C");

        // Space separator (GenomicParser format)
        assertInvalid("1 12345 A T");
        assertInvalid("chr1 12345 A T");
        assertInvalid("X 54321 G C");

        // Other separators
        assertInvalid("1;12345;A;T");    // Semicolon
        assertInvalid("1,12345,A,T");    // Comma
        assertInvalid("1_12345_A_T");    // Underscore
        assertInvalid("1.12345.A.T");    // Dot
        assertInvalid("1|12345|A|T");    // Pipe

        // Mixed separators
        assertInvalid("1-12345:A-T");
        assertInvalid("1:12345-A:T");
        assertInvalid("1 12345-A-T");
    }

    @Test
    @DisplayName("Test Invalid Chromosomes")
    public void testInvalidChromosomes() {
        // Invalid numeric chromosomes
        assertInvalid("0-12345-A-T");        // chr0 doesn't exist
        assertInvalid("chr0-12345-A-T");     // chr0 doesn't exist
        assertInvalid("23-12345-A-T");       // chr23 doesn't exist
        assertInvalid("chr23-12345-A-T");    // chr23 doesn't exist
        assertInvalid("100-12345-A-T");      // chr100 doesn't exist

        // Invalid letter chromosomes
        assertInvalid("Z-12345-A-T");        // chrZ doesn't exist
        assertInvalid("chrZ-12345-A-T");     // chrZ doesn't exist
        assertInvalid("W-12345-A-T");        // chrW doesn't exist

        // Invalid names
        assertInvalid("invalid-12345-A-T");
        assertInvalid("chromosome1-12345-A-T");
        assertInvalid("chr1a-12345-A-T");
        assertInvalid("1a-12345-A-T");
    }

    @Test
    @DisplayName("Test Invalid Positions")
    public void testInvalidPositions() {
        // Zero position
        assertInvalid("1-0-A-T");
        assertInvalid("1-00-A-T");
        assertInvalid("1-000-A-T");

        // Negative positions
        assertInvalid("1--123-A-T");        // Negative (double dash)
        assertInvalid("1-ABC-A-T");         // Non-numeric
        assertInvalid("1-12.5-A-T");        // Decimal
        assertInvalid("1-12,345-A-T");      // Comma in number
        assertInvalid("1-12 345-A-T");      // Space in number
    }

    @Test
    @DisplayName("Test Invalid Bases")
    public void testInvalidBases() {
        // Invalid reference bases
        assertInvalid("1-12345-X-T");       // X not valid
        assertInvalid("1-12345-N-T");       // N not valid
        assertInvalid("1-12345-U-T");       // U not valid (RNA)
        assertInvalid("1-12345-123-T");     // Numbers not valid
        assertInvalid("1-12345-*-T");       // Special chars not valid
        assertInvalid("1-12345-R-T");       // IUPAC ambiguous codes not valid
        assertInvalid("1-12345-Y-T");       // IUPAC ambiguous codes not valid

        // Invalid alternate bases
        assertInvalid("1-12345-A-X");       // X not valid
        assertInvalid("1-12345-A-N");       // N not valid
        assertInvalid("1-12345-A-U");       // U not valid (RNA)
        assertInvalid("1-12345-A-123");     // Numbers not valid
        assertInvalid("1-12345-A-*");       // Special chars not valid
        assertInvalid("1-12345-A-R");       // IUPAC ambiguous codes not valid
        assertInvalid("1-12345-A-Y");       // IUPAC ambiguous codes not valid

        // Multi-base sequences (not allowed with single base pattern)
        assertInvalid("1-12345-ATCG-T");    // Multi-base ref not allowed
        assertInvalid("1-12345-A-TCGC");    // Multi-base alt not allowed
        assertInvalid("1-12345-AT-CG");     // Multi-base both not allowed
        assertInvalid("1-12345-AA-T");      // Multi-base ref not allowed
        assertInvalid("1-12345-A-TT");      // Multi-base alt not allowed

        // Empty bases
        assertInvalid("1-12345--T");        // Empty ref
        assertInvalid("1-12345-A-");        // Empty alt

        // Invalid characters in bases
        assertInvalid("1-12345-A T-C");     // Space in ref
        assertInvalid("1-12345-A-T C");     // Space in alt
        assertInvalid("1-12345-A-T-C");     // Extra component
    }

    @Test
    @DisplayName("Test Malformed Input")
    public void testMalformedInput() {
        // Empty string
        assertInvalid("");

        // Too many components
        assertInvalid("1-12345-A-T-extra");
        assertInvalid("1-12345-A-T-G-extra");

        // Too few dashes
        assertInvalid("1-12345-AT");        // Looks like missing dash between A and T
        assertInvalid("112345-A-T");        // Looks like missing dash between 1 and 12345

        // Too many dashes
        assertInvalid("1--12345-A-T");      // Double dash
        assertInvalid("1-12345--A-T");      // Double dash
        assertInvalid("1-12345-A--T");      // Double dash
        assertInvalid("-1-12345-A-T");      // Leading dash
        assertInvalid("1-12345-A-T-");      // Trailing dash

        // Only dashes
        assertInvalid("---");
        assertInvalid("----");

        // Whitespace issues
        assertInvalid(" 1-12345-A-T");      // Leading space
        assertInvalid("1-12345-A-T ");      // Trailing space
        assertInvalid("1 -12345-A-T");      // Space before dash
        assertInvalid("1- 12345-A-T");      // Space after dash
    }

    @Test
    @DisplayName("Test Edge Cases")
    public void testEdgeCases() {
        // Minimum valid values
        assertResult("1-1-A-T", "1", 1, "A", "T");

        // Maximum chromosome
        assertResult("22-1-A-T", "22", 1, "A", "T");

        // All valid bases in different combinations
        String[] bases = {"A", "T", "C", "G"};
        for (String ref : bases) {
            for (String alt : bases) {
                if (!ref.equals(alt)) {
                    assertResult("1-12345-" + ref + "-" + alt, "1", 12345, ref, alt);
                }
            }
        }

        // Long sequences (not allowed with single base pattern)
        assertInvalid("1-12345-ATCGATCGATCG-GCATGCATGCAT");

        // All chromosome types with single bases only
        for (int i = 1; i <= 22; i++) {
            assertResult(i + "-12345-A-T", String.valueOf(i), 12345, "A", "T");
        }
        assertResult("X-12345-A-T", "X", 12345, "A", "T");
        assertResult("Y-12345-A-T", "Y", 12345, "A", "T");
        assertResult("MT-12345-A-T", "MT", 12345, "A", "T");
    }

    // Helper method to assert valid parsing results
    private void assertResult(String input, String expectedChr, Integer expectedPos,
                              String expectedRef, String expectedAlt) {
        GenomicInput result = GnomadParser.parse(input);
        assertNotNull(result, "Parser should never return null: " + input);
        assertTrue(result.isValid(), "Input should be valid: " + input);
        assertEquals(expectedChr, result.getChromosome(), "Wrong chromosome for: " + input);
        assertEquals(expectedPos, result.getPosition(), "Wrong position for: " + input);
        assertEquals(expectedRef, result.getRefBase(), "Wrong reference for: " + input);
        assertEquals(expectedAlt, result.getAltBase(), "Wrong alternate for: " + input);
    }

    // Helper method to assert invalid input
    private void assertInvalid(String input) {
        GenomicInput result = GnomadParser.parse(input);
        assertNotNull(result, "Parser should never return null: " + input);
        assertFalse(result.isValid(), "Input should be invalid: " + input);
        assertEquals(input, result.getInputStr(), "Input string should be preserved: " + input);
    }
}