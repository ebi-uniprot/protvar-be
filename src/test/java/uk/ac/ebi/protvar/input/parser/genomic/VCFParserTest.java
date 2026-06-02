package uk.ac.ebi.protvar.input.parser.genomic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.input.GenomicInput;
import uk.ac.ebi.protvar.input.VariantFormat;

import static org.junit.jupiter.api.Assertions.*;

public class VCFParserTest {

  @Test
  @DisplayName("Test Valid VCF Format - Basic Cases")
  public void testValidBasicCases() {
    // Simple SNVs with dot ID
    assertResult("1 12345 . A T", "1", 12345, ".", "A", "T");
    assertResult("chr1 12345 . A T", "1", 12345, ".", "A", "T");
    assertResult("22 999999 . G C", "22", 999999, ".", "G", "C");
    assertResult("X 54321 . C G", "X", 54321, ".", "C", "G");
    assertResult("Y 1000 . T A", "Y", 1000, ".", "T", "A");

    // Mitochondrial chromosomes
    assertResult("MT 16569 . T C", "MT", 16569, ".", "T", "C");
    assertResult("M 16569 . A G", "MT", 16569, ".", "A", "G");
    assertResult("chrMT 16569 . C T", "MT", 16569, ".", "C", "T");

    // With actual variant IDs
    assertResult("1 12345 rs123456 A T", "1", 12345, "rs123456", "A", "T");
    assertResult("X 54321 variant1 G C", "X", 54321, "variant1", "G", "C");
    assertResult("MT 16569 m.16569A>G A G", "MT", 16569, "m.16569A>G", "A", "G");
  }

  @Test
  @DisplayName("Test Mitochondrial Aliases")
  public void testMitochondrialAliases() {
    assertResult("mit 302 . A T", "MT", 302, ".", "A", "T");
    assertResult("mtDNA 302 . G C", "MT", 302, ".", "G", "C");
    assertResult("mitochondria 302 . T A", "MT", 302, ".", "T", "A");
    assertResult("mitochondrion 302 . C G", "MT", 302, ".", "C", "G");

    // Mixed case
    assertResult("MIT 302 . A T", "MT", 302, ".", "A", "T");
    assertResult("MtDnA 302 . G C", "MT", 302, ".", "G", "C");
    assertResult("MITOCHONDRIA 302 . T A", "MT", 302, ".", "T", "A");
  }

  @Test
  @DisplayName("Test Single Base Variants Only")
  public void testSingleBaseVariants() {
    // All valid single base combinations
    String[] bases = {"A", "T", "C", "G"};
    for (String ref : bases) {
      for (String alt : bases) {
        if (!ref.equals(alt)) {
          assertResult("1 12345 . " + ref + " " + alt, "1", 12345, ".", ref, alt);
        }
      }
    }

    // Different chromosomes
    assertResult("X 54321 . A T", "X", 54321, ".", "A", "T");
    assertResult("Y 1000 . C G", "Y", 1000, ".", "C", "G");
    assertResult("MT 16569 . T C", "MT", 16569, ".", "T", "C");
  }

  @Test
  @DisplayName("Test Chromosome Normalization")
  public void testChromosomeNormalization() {
    // Leading zeros removal
    assertResult("chr01 12345 . A T", "1", 12345, ".", "A", "T");
    assertResult("chr001 12345 . G C", "1", 12345, ".", "G", "C");
    assertResult("00022 999999 . T A", "22", 999999, ".", "T", "A");

    // Chr prefix removal
    assertResult("chr1 12345 . A T", "1", 12345, ".", "A", "T");
    assertResult("chrX 54321 . G C", "X", 54321, ".", "G", "C");
    assertResult("chrY 1000 . T A", "Y", 1000, ".", "T", "A");

    // Case insensitive chromosomes
    assertResult("CHR1 12345 . A T", "1", 12345, ".", "A", "T");
    assertResult("x 54321 . G C", "X", 54321, ".", "G", "C");
    assertResult("y 1000 . T A", "Y", 1000, ".", "T", "A");
  }

  @Test
  @DisplayName("Test Position Normalization")
  public void testPositionNormalization() {
    // Leading zeros in position
    assertResult("1 00001 . A T", "1", 1, ".", "A", "T");
    assertResult("1 00123 . G C", "1", 123, ".", "G", "C");
    assertResult("1 000456 . T A", "1", 456, ".", "T", "A");

    // Large positions
    assertResult("1 999999999 . A T", "1", 999999999, ".", "A", "T");
    assertResult("X 123456789 . G C", "X", 123456789, ".", "G", "C");
  }

  @Test
  @DisplayName("Test Base Normalization")
  public void testBaseNormalization() {
    // Lowercase bases
    assertResult("1 12345 . a t", "1", 12345, ".", "A", "T");
    assertResult("1 12345 . g c", "1", 12345, ".", "G", "C");
    assertResult("1 12345 . c a", "1", 12345, ".", "C", "A");
    assertResult("1 12345 . t g", "1", 12345, ".", "T", "G");

    // Mixed case bases
    assertResult("1 12345 . A t", "1", 12345, ".", "A", "T");
    assertResult("X 54321 . g C", "X", 54321, ".", "G", "C");
    assertResult("Y 1000 . T a", "Y", 1000, ".", "T", "A");
  }

  @Test
  @DisplayName("Test ID Field Variations")
  public void testIdFieldVariations() {
    // Dot for unknown
    assertResult("1 12345 . A T", "1", 12345, ".", "A", "T");

    // rsID format
    assertResult("1 12345 rs123456 A T", "1", 12345, "rs123456", "A", "T");
    assertResult("1 12345 rs12345678 G C", "1", 12345, "rs12345678", "G", "C");

    // Custom variant IDs
    assertResult("1 12345 variant1 A T", "1", 12345, "variant1", "A", "T");
    assertResult("1 12345 SNP_001 G C", "1", 12345, "SNP_001", "G", "C");
    assertResult("1 12345 my_variant_id T A", "1", 12345, "my_variant_id", "T", "A");

    // Complex IDs
    assertResult("MT 16569 m.16569A>G A G", "MT", 16569, "m.16569A>G", "A", "G");
    assertResult("1 12345 1:12345:A>T A T", "1", 12345, "1:12345:A>T", "A", "T");
    assertResult("1 12345 chr1-12345-A-T A T", "1", 12345, "chr1-12345-A-T", "A", "T");
  }

  @Test
  @DisplayName("Test Additional Columns Ignored")
  public void testAdditionalColumnsIgnored() {
    // With QUAL, FILTER, INFO
    assertResult("1 12345 . A T 60 PASS DP=100", "1", 12345, ".", "A", "T");
    assertResult("1 12345 rs123 A T 30 . AF=0.5", "1", 12345, "rs123", "A", "T");
    assertResult("X 54321 . G C 90 PASS DP=200;AF=0.3", "X", 54321, ".", "G", "C");

    // With FORMAT and sample columns
    assertResult("1 12345 . A T 60 PASS . GT:DP 0/1:50", "1", 12345, ".", "A", "T");
    assertResult("1 12345 . A T 60 PASS . GT:DP:GQ 0/1:50:99 1/1:30:90", "1", 12345, ".", "A", "T");

    // Many additional columns
    assertResult("1 12345 . A T 60 PASS DP=100 GT:DP:GQ:AD:VAF 0/1:100:99:50,50:0.5 1/1:80:95:10,70:0.875",
            "1", 12345, ".", "A", "T");
  }

  @Test
  @DisplayName("Test Pattern Matching")
  public void testPatternMatching() {
    // Valid patterns should match
    assertTrue(VCFParser.matchesPattern("1 12345 . A T"));
    assertTrue(VCFParser.matchesPattern("chr1 12345 rs123 A T"));
    assertTrue(VCFParser.matchesPattern("X 54321 . G C"));
    assertTrue(VCFParser.matchesPattern("MT 16569 . T C"));
    assertTrue(VCFParser.matchesPattern("1 12345 . A T 60 PASS DP=100"));

    // Invalid patterns should not match
    assertFalse(VCFParser.matchesPattern("1 12345 . A"));        // Missing ALT
    assertFalse(VCFParser.matchesPattern("1-12345-.-A-T"));      // Wrong separator
    assertFalse(VCFParser.matchesPattern("1:12345:.:A:T"));      // Wrong separator
    assertFalse(VCFParser.matchesPattern("12345 . A T"));        // Missing CHROM
    assertFalse(VCFParser.matchesPattern("1 0 . A T"));          // Invalid position
    assertFalse(VCFParser.matchesPattern("1 12345 . ATCG T"));   // Multi-base not allowed
  }

  @Test
  @DisplayName("Test Invalid Formats - Missing Components")
  public void testInvalidMissingComponents() {
    // Missing ALT
    assertInvalid("1 12345 . A");
    assertInvalid("chr1 12345 rs123 A");
    assertInvalid("X 54321 . G");

    // Missing REF and ALT
    assertInvalid("1 12345 .");
    assertInvalid("chr1 12345 rs123");
    assertInvalid("MT 16569 variant1");

    // Missing ID, REF, and ALT
    assertInvalid("1 12345");
    assertInvalid("chr1 12345");

    // Missing CHROM
    assertInvalid("12345 . A T");
    assertInvalid("54321 rs123 G C");

    // Empty components
    assertInvalid("1  . A T");          // Empty position (double space)
    assertInvalid("1 12345  A T");      // Empty ID (double space)
    assertInvalid("1 12345 .  T");      // Empty ref (double space)
    assertInvalid("1 12345 . A ");      // Empty alt (trailing space)
    assertInvalid(" 12345 . A T");      // Empty chr (leading space)
  }

  @Test
  @DisplayName("Test Invalid Formats - Wrong Separators")
  public void testInvalidWrongSeparators() {
    // Tab characters (should work - spaces include tabs)
    assertResult("1\t12345\t.\tA\tT", "1", 12345, ".", "A", "T");
    assertResult("chr1\t12345\trs123\tA\tT", "1", 12345, "rs123", "A", "T");

    // Other separators (should fail)
    assertInvalid("1-12345-.-A-T");      // Dash (GnomAD format)
    assertInvalid("1:12345:.:A:T");      // Colon
    assertInvalid("1;12345;.;A;T");      // Semicolon
    assertInvalid("1,12345,.,A,T");      // Comma
    assertInvalid("1_12345_._A_T");      // Underscore
    assertInvalid("1|12345|.|A|T");      // Pipe
  }

  @Test
  @DisplayName("Test Invalid Chromosomes")
  public void testInvalidChromosomes() {
    // Invalid numeric chromosomes
    assertInvalid("0 12345 . A T");        // chr0 doesn't exist
    assertInvalid("chr0 12345 . A T");     // chr0 doesn't exist
    assertInvalid("23 12345 . A T");       // chr23 doesn't exist
    assertInvalid("chr23 12345 . A T");    // chr23 doesn't exist
    assertInvalid("100 12345 . A T");      // chr100 doesn't exist

    // Invalid letter chromosomes
    assertInvalid("Z 12345 . A T");        // chrZ doesn't exist
    assertInvalid("chrZ 12345 . A T");     // chrZ doesn't exist
    assertInvalid("W 12345 . A T");        // chrW doesn't exist

    // Invalid names
    assertInvalid("invalid 12345 . A T");
    assertInvalid("chromosome1 12345 . A T");
    assertInvalid("chr1a 12345 . A T");
  }

  @Test
  @DisplayName("Test Invalid Positions")
  public void testInvalidPositions() {
    // Zero position
    assertInvalid("1 0 . A T");
    assertInvalid("1 00 . A T");
    assertInvalid("1 000 . A T");

    // Non-numeric positions
    assertInvalid("1 ABC . A T");         // Non-numeric
    assertInvalid("1 12.5 . A T");        // Decimal
    assertInvalid("1 12,345 . A T");      // Comma in number
    assertInvalid("1 -123 . A T");        // Negative (would be parsed as two fields)
  }

  @Test
  @DisplayName("Test Invalid Bases")
  public void testInvalidBases() {
    // Invalid reference bases
    assertInvalid("1 12345 . X T");       // X not valid
    assertInvalid("1 12345 . N T");       // N not valid
    assertInvalid("1 12345 . U T");       // U not valid (RNA)
    assertInvalid("1 12345 . R T");       // IUPAC ambiguous codes not valid
    assertInvalid("1 12345 . Y T");       // IUPAC ambiguous codes not valid

    // Invalid alternate bases
    assertInvalid("1 12345 . A X");       // X not valid
    assertInvalid("1 12345 . A N");       // N not valid
    assertInvalid("1 12345 . A U");       // U not valid (RNA)
    assertInvalid("1 12345 . A R");       // IUPAC ambiguous codes not valid
    assertInvalid("1 12345 . A Y");       // IUPAC ambiguous codes not valid

    // Multi-base sequences (not allowed)
    assertInvalid("1 12345 . ATCG T");    // Multi-base ref not allowed
    assertInvalid("1 12345 . A TCGC");    // Multi-base alt not allowed
    assertInvalid("1 12345 . AT CG");     // Multi-base both not allowed

    // Numbers and special characters
    assertInvalid("1 12345 . 1 T");       // Number as base
    assertInvalid("1 12345 . A 2");       // Number as base
    assertInvalid("1 12345 . * T");       // Special character
    assertInvalid("1 12345 . A -");       // Special character
  }

  @Test
  @DisplayName("Test Malformed Input")
  public void testMalformedInput() {
    // Empty string
    assertInvalid("");

    // Only spaces
    assertInvalid("   ");
    assertInvalid("\t\t\t");

    // Single field
    assertInvalid("1");
    assertInvalid("chr1");

    // Two fields
    assertInvalid("1 12345");

    // Three fields
    assertInvalid("1 12345 .");

    // Four fields
    assertInvalid("1 12345 . A");

    // Whitespace handling
    assertInvalid(" 1 12345 . A T");      // Leading space should fail (^ anchor)
    assertResult("1 12345 . A T ", "1", 12345, ".", "A", "T");      // Trailing space works
    assertResult("1 12345 . A T   ", "1", 12345, ".", "A", "T");    // Multiple trailing spaces work
  }

  @Test
  @DisplayName("Test Edge Cases")
  public void testEdgeCases() {
    // Minimum valid values
    assertResult("1 1 . A T", "1", 1, ".", "A", "T");

    // Maximum chromosome
    assertResult("22 1 . A T", "22", 1, ".", "A", "T");

    // Very long ID
    String longId = "very_long_variant_id_with_many_characters_" + "x".repeat(100);
    assertResult("1 12345 " + longId + " A T", "1", 12345, longId, "A", "T");

    // ID with special characters (but no spaces)
    assertResult("1 12345 rs123:A>T A T", "1", 12345, "rs123:A>T", "A", "T");
    assertResult("1 12345 variant-001 A T", "1", 12345, "variant-001", "A", "T");
    assertResult("1 12345 SNP_123.456 A T", "1", 12345, "SNP_123.456", "A", "T");

    // All chromosome types
    for (int i = 1; i <= 22; i++) {
      assertResult(i + " 12345 . A T", String.valueOf(i), 12345, ".", "A", "T");
    }
    assertResult("X 12345 . A T", "X", 12345, ".", "A", "T");
    assertResult("Y 12345 . A T", "Y", 12345, ".", "A", "T");
    assertResult("MT 12345 . A T", "MT", 12345, ".", "A", "T");
  }

  // Helper method to assert valid parsing results
  private void assertResult(String input, String expectedChr, Integer expectedPos, String expectedId,
                            String expectedRef, String expectedAlt) {
    GenomicInput result = VCFParser.parse(input);
    assertNotNull(result, "Parser should never return null: " + input);
    assertTrue(result.isValid(), "Input should be valid: " + input);
    assertEquals(VariantFormat.VCF, result.getFormat(), "Wrong format for: " + input);
    assertEquals(expectedChr, result.getChromosome(), "Wrong chromosome for: " + input);
    assertEquals(expectedPos, result.getPosition(), "Wrong position for: " + input);
    assertEquals(expectedId, result.getId(), "Wrong ID for: " + input);
    assertEquals(expectedRef, result.getRefBase(), "Wrong reference for: " + input);
    assertEquals(expectedAlt, result.getAltBase(), "Wrong alternate for: " + input);
  }

  // Helper method to assert invalid input
  private void assertInvalid(String input) {
    GenomicInput result = VCFParser.parse(input);
    assertNotNull(result, "Parser should never return null: " + input);
    assertFalse(result.isValid(), "Input should be invalid: " + input);
    assertEquals(input, result.getInputStr(), "Input string should be preserved: " + input);
  }
}