package uk.ac.ebi.protvar.input.parser.protein;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import uk.ac.ebi.protvar.input.ProteinInput;
import uk.ac.ebi.protvar.input.VariantFormat;

public class ProteinParserTest {

    @Test
    @DisplayName("Test Format 1: ACC POS (Position Only)")
    public void testPositionOnlyFormat() {
        // Valid cases
        assertResult("P22304 205", "P22304", 205, null, null);
        assertResult("P07949 783", "P07949", 783, null, null);
        assertResult("Q9UBX0 501", "Q9UBX0", 501, null, null);

        // With isoforms
        assertResult("P22304-1 205", "P22304-1", 205, null, null);
        assertResult("P07949-2 783", "P07949-2", 783, null, null);

        // Case insensitive
        assertResult("p22304 205", "P22304", 205, null, null);
        assertResult("P22304 205", "P22304", 205, null, null);

        // Large positions
        assertResult("P22304 999999", "P22304", 999999, null, null);
    }

    @Test
    @DisplayName("Test Format 2: ACC POS REF [ALT] - Single Letter")
    public void testSingleLetterSpacedFormat() {
        // REF only
        assertResult("P22304 205 A", "P22304", 205, "A", null);
        assertResult("P07949 783 N", "P07949", 783, "N", null);
        assertResult("Q9UBX0 501 K", "Q9UBX0", 501, "K", null);

        // REF ALT with space
        assertResult("P22304 205 A P", "P22304", 205, "A", "P");
        assertResult("P07949 783 N T", "P07949", 783, "N", "T");
        assertResult("Q9UBX0 501 K R", "Q9UBX0", 501, "K", "R");

        // REF ALT with slash
        assertResult("P22304 205 A/P", "P22304", 205, "A", "P");
        assertResult("P07949 783 N/T", "P07949", 783, "N", "T");
        assertResult("Q9UBX0 501 K/R", "Q9UBX0", 501, "K", "R");

        // With stop codon
        assertResult("P22304 205 A *", "P22304", 205, "A", "*");
        assertResult("P07949 783 N/*", "P07949", 783, "N", "*");

        // With unchanged notation
        assertResult("P22304 205 A =", "P22304", 205, "A", "A"); // = should become A
        assertResult("P07949 783 N/=", "P07949", 783, "N", "N"); // = should become N

        // Case insensitive
        assertResult("P22304 205 a p", "P22304", 205, "A", "P");
        assertResult("p22304 205 A/t", "P22304", 205, "A", "T");
    }

    @Test
    @DisplayName("Test Format 3: ACC POS REF [ALT] - Three Letter")
    public void testThreeLetterSpacedFormat() {
        // REF only
        assertResult("P22304 205 Ala", "P22304", 205, "A", null);
        assertResult("P07949 783 Asn", "P07949", 783, "N", null);
        assertResult("Q9UBX0 501 Lys", "Q9UBX0", 501, "K", null);

        // REF ALT with space
        assertResult("P22304 205 Ala Pro", "P22304", 205, "A", "P");
        assertResult("P07949 783 Asn Thr", "P07949", 783, "N", "T");
        assertResult("Q9UBX0 501 Lys Arg", "Q9UBX0", 501, "K", "R");

        // REF ALT with slash
        assertResult("P22304 205 Ala/Pro", "P22304", 205, "A", "P");
        assertResult("P07949 783 Asn/Thr", "P07949", 783, "N", "T");
        assertResult("Q9UBX0 501 Lys/Arg", "Q9UBX0", 501, "K", "R");

        // With stop codon (*)
        assertResult("P22304 205 Ala *", "P22304", 205, "A", "*");
        assertResult("P07949 783 Asn/*", "P07949", 783, "N", "*");

        // With stop codon (Ter)
        assertResult("P22304 205 Ala Ter", "P22304", 205, "A", "*");
        assertResult("P07949 783 Asn/Ter", "P07949", 783, "N", "*");

        // With unchanged notation
        assertResult("P22304 205 Ala =", "P22304", 205, "A", "A");
        assertResult("P07949 783 Asn/=", "P07949", 783, "N", "N");

        // Case insensitive
        assertResult("P22304 205 ala pro", "P22304", 205, "A", "P");
        assertResult("p22304 205 ALA/thr", "P22304", 205, "A", "T");
    }

    @Test
    @DisplayName("Test Format 4: ACC [p.]REF POS ALT - Compact Single Letter")
    public void testCompactSingleLetterFormat() {
        // Without p. prefix
        assertResult("P22304 A205P", "P22304", 205, "A", "P");
        assertResult("P07949 N783T", "P07949", 783, "N", "T");
        assertResult("Q9UBX0 K501R", "Q9UBX0", 501, "K", "R");

        // With p. prefix
        assertResult("P22304 p.A205P", "P22304", 205, "A", "P");
        assertResult("P07949 p.N783T", "P07949", 783, "N", "T");
        assertResult("Q9UBX0 p.K501R", "Q9UBX0", 501, "K", "R");

        // With stop codon
        assertResult("P22304 A205*", "P22304", 205, "A", "*");
        assertResult("P07949 p.N783*", "P07949", 783, "N", "*");

        // With unchanged notation
        assertResult("P22304 A205=", "P22304", 205, "A", "A");
        assertResult("P07949 p.N783=", "P07949", 783, "N", "N");

        // Case insensitive
        assertResult("p22304 a205p", "P22304", 205, "A", "P");
        assertResult("P22304 P.A205T", "P22304", 205, "A", "T");

        // With isoforms
        assertResult("P22304-1 A205P", "P22304-1", 205, "A", "P");
        assertResult("P07949-2 p.N783T", "P07949-2", 783, "N", "T");
    }

    @Test
    @DisplayName("Test Format 5: ACC [p.]REF POS ALT - Compact Three Letter")
    public void testCompactThreeLetterFormat() {
        // Without p. prefix
        assertResult("P22304 Ala205Pro", "P22304", 205, "A", "P");
        assertResult("P07949 Asn783Thr", "P07949", 783, "N", "T");
        assertResult("Q9UBX0 Lys501Arg", "Q9UBX0", 501, "K", "R");

        // With p. prefix
        assertResult("P22304 p.Ala205Pro", "P22304", 205, "A", "P");
        assertResult("P07949 p.Asn783Thr", "P07949", 783, "N", "T");
        assertResult("Q9UBX0 p.Lys501Arg", "Q9UBX0", 501, "K", "R");

        // With stop codon (*)
        assertResult("P22304 Ala205*", "P22304", 205, "A", "*");
        assertResult("P07949 p.Asn783*", "P07949", 783, "N", "*");

        // With stop codon (Ter)
        assertResult("P22304 Ala205Ter", "P22304", 205, "A", "*");
        assertResult("P07949 p.Asn783Ter", "P07949", 783, "N", "*");

        // With unchanged notation
        assertResult("P22304 Ala205=", "P22304", 205, "A", "A");
        assertResult("P07949 p.Asn783=", "P07949", 783, "N", "N");

        // Case insensitive
        assertResult("p22304 ala205pro", "P22304", 205, "A", "P");
        assertResult("P22304 P.ALA205thr", "P22304", 205, "A", "T");

        // With isoforms
        assertResult("P22304-1 Ala205Pro", "P22304-1", 205, "A", "P");
        assertResult("P07949-2 p.Asn783Thr", "P07949-2", 783, "N", "T");
    }

    @Test
    @DisplayName("Test Extended Amino Acids")
    public void testExtendedAminoAcids() {
        // Single letter extended amino acids
        assertResult("P22304 205 B U", "P22304", 205, "B", "U"); // Asx -> Sec
        assertResult("P07949 783 Z/X", "P07949", 783, "Z", "X"); // Glx -> Unk
        assertResult("Q9UBX0 501 O J", "Q9UBX0", 501, "O", "J"); // Pyl -> Xle

        // Three letter extended amino acids
        assertResult("P22304 205 Asx Sec", "P22304", 205, "B", "U");
        assertResult("P07949 783 Glx/Unk", "P07949", 783, "Z", "X");
        assertResult("Q9UBX0 501 Pyl Xle", "Q9UBX0", 501, "O", "J");

        // Compact extended amino acids
        assertResult("P22304 B205U", "P22304", 205, "B", "U");
        assertResult("P07949 p.Asx783Sec", "P07949", 783, "B", "U");
    }

    @Test
    @DisplayName("Test Structural Validation")
    public void testStructuralValidation() {
        // Valid structures
        assertTrue(ProteinParser.matchesStructure("P22304 205"));
        assertTrue(ProteinParser.matchesStructure("P22304 205 A"));
        assertTrue(ProteinParser.matchesStructure("P22304 205 A P"));
        assertTrue(ProteinParser.matchesStructure("P22304 A205P"));
        assertTrue(ProteinParser.matchesStructure("Q9UBX0-1 501 K R"));

        // Invalid structures
        assertFalse(ProteinParser.matchesStructure("P22304"));          // Just accession
        assertFalse(ProteinParser.matchesStructure("InvalidAcc 205"));  // Invalid accession
        assertFalse(ProteinParser.matchesStructure(""));                // Empty
        assertFalse(ProteinParser.matchesStructure(null));              // Null
        assertFalse(ProteinParser.matchesStructure("205"));             // Just position
    }

    @Test
    @DisplayName("Test Pattern Validation")
    public void testPatternValidation() {
        // Valid patterns
        assertTrue(ProteinParser.matchesPattern("P22304 205"));
        assertTrue(ProteinParser.matchesPattern("P22304 205 A P"));
        assertTrue(ProteinParser.matchesPattern("P22304 205 Ala Pro"));
        assertTrue(ProteinParser.matchesPattern("P22304 A205P"));
        assertTrue(ProteinParser.matchesPattern("P22304 p.Ala205Pro"));

        // Invalid patterns
        assertFalse(ProteinParser.matchesPattern("P22304"));            // Just accession
        assertFalse(ProteinParser.matchesPattern("InvalidAcc 205"));    // Invalid accession
        assertFalse(ProteinParser.matchesPattern("P22304 abc"));        // Invalid position
        assertFalse(ProteinParser.matchesPattern("P22304 205 #"));      // Invalid amino acid
        assertFalse(ProteinParser.matchesPattern("P22304 205 A B C"));  // Too many components
    }

    @Test
    @DisplayName("Test Invalid Formats")
    public void testInvalidFormats() {
        // Invalid accessions
        assertInvalid("InvalidAcc 205 A P");    // Invalid accession format
        assertInvalid("P2230 205 A P");         // Too short
        assertInvalid("P223041 205 A P");       // Too long
        assertInvalid("123456 205 A P");        // All numbers

        // Invalid positions
        assertInvalid("P22304 0 A P");          // Zero position
        assertInvalid("P22304 abc A P");        // Non-numeric position
        assertInvalid("P22304 -123 A P");       // Negative position
        assertInvalid("P22304 12.5 A P");       // Decimal position

        // Invalid amino acids - using characters not in VALID_AA_SINGLE [ACDEFGHIKLMNPQRSTVWY*BZUXOJ]
        assertInvalid("P22304 205 @ #");        // @ and # are not valid amino acid codes
        assertInvalid("P22304 205 1 2");        // Numbers are not valid amino acid codes
        assertInvalid("P22304 205 Ala Xyz");     // Xyz is not a valid three-letter code
        assertInvalid("P22304 @205#");           // Invalid compact format with special chars

        // Malformed input
        assertInvalid("P22304");                // Missing position
        assertInvalid("205 A P");               // Missing accession
        assertInvalid("P22304 205 A B C D");    // Too many components
        assertInvalid("");                      // Empty string
        assertInvalid("   ");                   // Only whitespace

        // Mixed formats (invalid)
        assertInvalid("P22304-205-A-P");        // Wrong separator
        assertInvalid("P22304:205:A:P");        // Wrong separator
        assertInvalid("P22304,205,A,P");        // Wrong separator
    }

    @Test
    @DisplayName("Test UniProt Accession Validation")
    public void testUniProtAccessionValidation() {
        // Valid P/Q/O format
        assertTrue(ProteinParser.validAccession("P22304"));
        assertTrue(ProteinParser.validAccession("Q9UBX0"));
        assertTrue(ProteinParser.validAccession("O43542"));

        // Valid other format
        assertTrue(ProteinParser.validAccession("A0A0A6YYL3"));
        assertTrue(ProteinParser.validAccession("B2R4R9"));
        assertTrue(ProteinParser.validAccession("C9JRZ8"));

        // Valid with isoforms
        assertTrue(ProteinParser.validAccession("P22304-1"));
        assertTrue(ProteinParser.validAccession("Q9UBX0-2"));
        assertTrue(ProteinParser.validAccession("A0A0A6YYL3-10"));

        // Invalid accessions
        assertFalse(ProteinParser.validAccession("P2230"));     // Too short
        assertFalse(ProteinParser.validAccession("P223041"));   // Too long for P format
        assertFalse(ProteinParser.validAccession("123456"));    // All numbers
        assertFalse(ProteinParser.validAccession("ABCDEF"));    // All letters
        assertFalse(ProteinParser.validAccession(""));          // Empty
        assertFalse(ProteinParser.validAccession(null));        // Null
    }

    @Test
    @DisplayName("Test Edge Cases")
    public void testEdgeCases() {
        // Minimum valid position
        assertResult("P22304 1", "P22304", 1, null, null);
        assertResult("P22304 A1P", "P22304", 1, "A", "P");

        // Large positions
        assertResult("P22304 999999", "P22304", 999999, null, null);
        assertResult("P22304 A999999P", "P22304", 999999, "A", "P");

        // All standard amino acids
        String[] standardAAs = {"A", "C", "D", "E", "F", "G", "H", "I", "K", "L", "M", "N", "P", "Q", "R", "S", "T", "V", "W", "Y"};
        for (String aa1 : standardAAs) {
            for (String aa2 : standardAAs) {
                if (!aa1.equals(aa2)) {
                    assertResult("P22304 205 " + aa1 + " " + aa2, "P22304", 205, aa1, aa2);
                    assertResult("P22304 " + aa1 + "205" + aa2, "P22304", 205, aa1, aa2);
                }
            }
        }

        // Stop codon in all positions
        assertResult("P22304 205 A *", "P22304", 205, "A", "*");
        assertResult("P22304 A205*", "P22304", 205, "A", "*");
        assertResult("P22304 205 Ala Ter", "P22304", 205, "A", "*");
        assertResult("P22304 p.Ala205Ter", "P22304", 205, "A", "*");

        // Unchanged notation in all positions
        assertResult("P22304 205 A =", "P22304", 205, "A", "A");
        assertResult("P22304 A205=", "P22304", 205, "A", "A");
        assertResult("P22304 205 Ala =", "P22304", 205, "A", "A");
        assertResult("P22304 p.Ala205=", "P22304", 205, "A", "A");
    }

    // Helper method to assert valid parsing results
    private void assertResult(String input, String expectedAcc, Integer expectedPos,
                              String expectedRef, String expectedAlt) {
        ProteinInput result = ProteinParser.parse(input);
        assertNotNull(result, "Parser should never return null: " + input);
        assertTrue(result.isValid(), "Input should be valid: " + input);
        assertEquals(VariantFormat.INTERNAL_PROTEIN, result.getFormat(), "Wrong format for: " + input);
        assertEquals(expectedAcc, result.getAccession(), "Wrong accession for: " + input);
        assertEquals(expectedPos, result.getPosition(), "Wrong position for: " + input);
        assertEquals(expectedRef, result.getRefAA(), "Wrong reference AA for: " + input);
        assertEquals(expectedAlt, result.getAltAA(), "Wrong alternate AA for: " + input);
    }

    // Helper method to assert invalid input
    private void assertInvalid(String input) {
        ProteinInput result = ProteinParser.parse(input);
        assertNotNull(result, "Parser should never return null: " + input);
        assertFalse(result.isValid(), "Input should be invalid: " + input);
        assertEquals(input, result.getInputStr(), "Input string should be preserved: " + input);
        assertFalse(result.getErrors().isEmpty(), "Invalid input should have errors: " + input);
    }

    @Test
    @DisplayName("Test Comprehensive Format Coverage")
    public void testComprehensiveFormatCoverage() {
        // Ensure we test all documented formats from the class javadoc

        // Format 1: ACC POS
        assertResult("P22304 205", "P22304", 205, null, null);

        // Format 2: ACC POS X[ |/]Y (single letter)
        assertResult("P22304 205 A", "P22304", 205, "A", null);        // REF only
        assertResult("P22304 205 A P", "P22304", 205, "A", "P");       // REF ALT space
        assertResult("P22304 205 A/P", "P22304", 205, "A", "P");       // REF ALT slash

        // Format 3: ACC POS XXX[ |/]YYY (three letter)
        assertResult("P07949 783 Asn", "P07949", 783, "N", null);      // REF only
        assertResult("P07949 783 Asn Thr", "P07949", 783, "N", "T");   // REF ALT space
        assertResult("P07949 783 Asn/Thr", "P07949", 783, "N", "T");   // REF ALT slash

        // Format 4: ACC X999Y (compact single letter)
        assertResult("P22304 A205P", "P22304", 205, "A", "P");

        // Format 5: ACC [p.]XXX999YYY (compact three letter)
        assertResult("P07949 Asn783Thr", "P07949", 783, "N", "T");     // Without p.
        assertResult("P07949 p.Asn783Thr", "P07949", 783, "N", "T");   // With p.
    }
}