package uk.ac.ebi.protvar.input.parser.genomic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.input.GenomicInput;

import static org.junit.jupiter.api.Assertions.*;

class GenomicParserTest {
    @Test
    @DisplayName("Test Space Format 1: CHR POS")
    public void testSpaceFormat1() {
        // Valid cases
        assertResult("chr1 12345", "1", 12345, null, null);
        assertResult("1 12345", "1", 12345, null, null);
        assertResult("chr22 999999", "22", 999999, null, null);
        assertResult("X 54321", "X", 54321, null, null);
        assertResult("Y 1000", "Y", 1000, null, null);
        assertResult("MT 16569", "MT", 16569, null, null);
        assertResult("chrMT 16569", "MT", 16569, null, null);
        assertResult("mitochondria 302", "MT", 302, null, null);
    }

    @Test
    @DisplayName("Test Space Format 2: CHR POS REF")
    public void testSpaceFormat2() {
        assertResult("chr1 12345 A", "1", 12345, "A", null);
        assertResult("X 54321 G", "X", 54321, "G", null);
        assertResult("MT 16569 T", "MT", 16569, "T", null);
        assertResult("mit 302 C", "MT", 302, "C", null);
    }

    @Test
    @DisplayName("Test Space Format 3: CHR POS REF ALT")
    public void testSpaceFormat3() {
        assertResult("chr1 12345 A T", "1", 12345, "A", "T");
        assertResult("22 999999 G C", "22", 999999, "G", "C");
        assertResult("X 54321 A G", "X", 54321, "A", "G");
        assertResult("mitochondrion 16569 T C", "MT", 16569, "T", "C");
    }

    @Test
    @DisplayName("Test Space Format 4: CHR POS REF/ALT")
    public void testSpaceFormat4() {
        assertResult("chr1 12345 A/T", "1", 12345, "A", "T");
        assertResult("X 54321 G/C", "X", 54321, "G", "C");
        assertResult("MT 16569 T/C", "MT", 16569, "T", "C");
        assertResult("mtDNA 302 A/G", "MT", 302, "A", "G");
    }

    @Test
    @DisplayName("Test Space Format 5: CHR POS REF>ALT")
    public void testSpaceFormat5() {
        assertResult("chr1 12345 A>T", "1", 12345, "A", "T");
        assertResult("Y 1000 C>G", "Y", 1000, "C", "G");
        assertResult("MT 16569 T>C", "MT", 16569, "T", "C");
        assertResult("M 302 G>A", "MT", 302, "G", "A");
    }

    @Test
    @DisplayName("Test Colon Format 6: CHR:POS")
    public void testColonFormat6() {
        assertResult("chr1:12345", "1", 12345, null, null);
        assertResult("1:12345", "1", 12345, null, null);
        assertResult("X:54321", "X", 54321, null, null);
        assertResult("MT:16569", "MT", 16569, null, null);
        assertResult("mitochondria:302", "MT", 302, null, null);
    }

    @Test
    @DisplayName("Test Colon Format 7: CHR:POS:REF")
    public void testColonFormat7() {
        assertResult("chr1:12345:A", "1", 12345, "A", null);
        assertResult("X:54321:G", "X", 54321, "G", null);
        assertResult("MT:16569:T", "MT", 16569, "T", null);
        assertResult("mit:302:C", "MT", 302, "C", null);
    }

    @Test
    @DisplayName("Test Colon Format 8: CHR:POS:REF:ALT")
    public void testColonFormat8() {
        assertResult("chr1:12345:A:T", "1", 12345, "A", "T");
        assertResult("22:999999:G:C", "22", 999999, "G", "C");
        assertResult("X:54321:A:G", "X", 54321, "A", "G");
        assertResult("MT:16569:T:C", "MT", 16569, "T", "C");
        assertResult("mitochondrion:302:A:G", "MT", 302, "A", "G");
    }

    @Test
    @DisplayName("Test Chromosome Normalization")
    public void testChromosomeNormalization() {
        // Leading zeros removal
        assertResult("chr01 12345", "1", 12345, null, null);
        assertResult("chr001:12345", "1", 12345, null, null);
        assertResult("00022 999999", "22", 999999, null, null);

        // Chr prefix removal
        assertResult("chr1 12345", "1", 12345, null, null);
        assertResult("chrX:54321", "X", 54321, null, null);
        assertResult("chrY 1000", "Y", 1000, null, null);

        // Mitochondrial aliases
        assertResult("M 16569", "MT", 16569, null, null);
        assertResult("MT:16569", "MT", 16569, null, null);
        assertResult("mit 302", "MT", 302, null, null);
        assertResult("mtDNA:302", "MT", 302, null, null);
        assertResult("mitochondria 302", "MT", 302, null, null);
        assertResult("mitochondrion:302", "MT", 302, null, null);

        // Case insensitive
        assertResult("CHR1 12345", "1", 12345, null, null);
        assertResult("x:54321", "X", 54321, null, null);
        assertResult("y 1000", "Y", 1000, null, null);
        assertResult("MITOCHONDRIA 302", "MT", 302, null, null);
    }

    @Test
    @DisplayName("Test Invalid Formats")
    public void testInvalidFormats() {
        // Invalid chromosomes
        assertInvalid("chr0 12345");        // chr0 doesn't exist
        assertInvalid("chr23 12345");       // chr23 doesn't exist
        assertInvalid("chrZ 12345");        // chrZ doesn't exist
        assertInvalid("invalid 12345");     // invalid chromosome

        // Invalid positions
        assertInvalid("chr1 0");            // position 0 invalid
        assertInvalid("chr1 abc");          // non-numeric position
        assertInvalid("chr1 -123");         // negative position

        // Invalid bases
        assertInvalid("chr1 12345 X");      // invalid base X
        assertInvalid("chr1 12345 A N");    // invalid base N
        assertInvalid("chr1 12345 A/X");    // invalid alt base X
        assertInvalid("chr1:12345:A:Z");    // invalid alt base Z

        // Mixed formats (invalid)
        assertInvalid("chr1:12345 A");      // mixed colon/space
        assertInvalid("chr1 12345:A");      // mixed space/colon
        assertInvalid("chr1:12345 A>T");    // mixed formats

        // Empty or malformed
        assertInvalid("");                  // empty string
        assertInvalid("chr1");              // missing position
        assertInvalid("12345");             // missing chromosome
        assertInvalid("chr1 12345 A B C");  // too many components
        assertInvalid("chr1:12345:A:T:G");  // too many components

        // Invalid separators
        assertInvalid("chr1;12345");        // semicolon separator
        assertInvalid("chr1,12345");        // comma separator
        assertInvalid("chr1_12345");        // underscore separator
        assertInvalid("chr1.12345");        // dot separator

        // Multiple separators mixed incorrectly
        assertInvalid("chr1 12345/A>T");    // multiple alt separators
        assertInvalid("chr1 12345 A/>T");   // malformed alt
        assertInvalid("chr1 12345 A<T");    // wrong alt separator
    }

    @DisplayName("Test Edge Cases")
    public void testEdgeCases() {
        // Minimum and maximum chromosomes
        assertResult("1 1", "1", 1, null, null);                     // min chr, min pos
        assertResult("22 999999999", "22", 999999999, null, null);   // max chr, large pos

        // All valid bases
        String[] bases = {"A", "T", "C", "G"};
        for (String ref : bases) {
            for (String alt : bases) {
                if (!ref.equals(alt)) { // Different ref/alt
                    assertResult("1 12345 " + ref + " " + alt, "1", 12345, ref, alt);
                    assertResult("1 12345 " + ref + "/" + alt, "1", 12345, ref, alt);
                    assertResult("1 12345 " + ref + ">" + alt, "1", 12345, ref, alt);
                    assertResult("1:12345:" + ref + ":" + alt, "1", 12345, ref, alt);
                }
            }
        }
    }

    @Test
    @DisplayName("Test Case Sensitivity")
    public void testCaseSensitivity() {
        // Bases are normalized to uppercase
        assertResult("chr1 12345 a t", "1", 12345, "A", "T");
        assertResult("CHR1 12345 A T", "1", 12345, "A", "T");
        assertResult("Chr1 12345 A t", "1", 12345, "A", "T");

        // Mixed case mitochondrial
        assertResult("MiToChOnDrIa 302", "MT", 302, null, null);
        assertResult("mTdNa:302", "MT", 302, null, null);
    }

    @Test
    @DisplayName("Test Leading Zeros in Position")
    public void testLeadingZerosInPosition() {
        // Leading zeros should be handled properly
        assertResult("chr1 0001", "1", 1, null, null);
        assertResult("chr1 00123", "1", 123, null, null);
        assertResult("chr1:000456", "1", 456, null, null);

        // But zero itself should be invalid (caught by regex)
        assertInvalid("chr1 0");
        assertInvalid("chr1 00");
        assertInvalid("chr1 000");
    }

    // Helper method to assert variant parsing results
    private void assertResult(String input, String expectedChr, Integer expectedPos,
                              String expectedRef, String expectedAlt) {
        GenomicInput result = GenomicParser.parse(input);
        assertNotNull(result, "Failed to parse: " + input);
        assertTrue(result.isValid(), "Input should be valid: " + input);
        assertEquals(expectedChr, result.getChromosome(), "Wrong chromosome for: " + input);
        assertEquals(expectedPos, result.getPosition(), "Wrong position for: " + input);
        assertEquals(expectedRef, result.getRefBase(), "Wrong reference for: " + input);
        assertEquals(expectedAlt, result.getAltBase(), "Wrong alternate for: " + input);
    }

    // Helper method to assert invalid input
    private void assertInvalid(String input) {
        GenomicInput result = GenomicParser.parse(input);
        assertNotNull(result, "Parser should never return null: " + input);
        assertFalse(result.isValid(), "Input should be invalid: " + input);
        assertEquals(input, result.getInputStr(), "Input string should be preserved: " + input);
    }
}