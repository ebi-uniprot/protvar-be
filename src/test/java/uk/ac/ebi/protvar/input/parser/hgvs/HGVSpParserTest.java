package uk.ac.ebi.protvar.input.parser.hgvs;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.input.ProteinInput;
import uk.ac.ebi.protvar.input.VariantFormat;

import static org.junit.jupiter.api.Assertions.*;

public class HGVSpParserTest {

    // ===== STRUCTURE VALIDATION TESTS =====

    @Test
    public void testMatchesStructure_ValidFormats() {
        assertTrue(HGVSpParser.matchesStructure("NP_123456.1:p.R490S"));
        assertTrue(HGVSpParser.matchesStructure("NM_000001.1:p.Met1Val"));
        assertTrue(HGVSpParser.matchesStructure("NP_123: p.R490S")); // with space
        assertTrue(HGVSpParser.matchesStructure("NP_123456.1:p.Arg490Ser"));
    }

    @Test
    public void testMatchesStructure_InvalidFormats() {
        assertFalse(HGVSpParser.matchesStructure(null));
        assertFalse(HGVSpParser.matchesStructure(""));
        assertFalse(HGVSpParser.matchesStructure("   "));
        assertFalse(HGVSpParser.matchesStructure("NP_123456.1:c.123A>T")); // wrong scheme
        assertFalse(HGVSpParser.matchesStructure("invalid_format"));
    }

    // ===== FULL PATTERN VALIDATION TESTS =====

    @Test
    public void testMatchesPattern_ValidSingleLetterAA() {
        assertTrue(HGVSpParser.matchesPattern("NP_123456.1:p.R490S"));
        assertTrue(HGVSpParser.matchesPattern("NM_000001.1:p.M1V"));
        assertTrue(HGVSpParser.matchesPattern("NP_999999.99:p.A123*")); // stop codon
    }

    @Test
    public void testMatchesPattern_ValidThreeLetterAA() {
        assertTrue(HGVSpParser.matchesPattern("NP_123456.1:p.Arg490Ser"));
        assertTrue(HGVSpParser.matchesPattern("NM_000001.1:p.Met1Val"));
        assertTrue(HGVSpParser.matchesPattern("NP_123456.1:p.Lys100Ter"));
    }

    @Test
    public void testMatchesPattern_SynonymousVariant() {
        assertTrue(HGVSpParser.matchesPattern("NP_123456.1:p.R490="));
    }

    @Test
    public void testMatchesPattern_WithParentheses() {
        assertTrue(HGVSpParser.matchesPattern("NP_123456.1:p.(R490S)"));
        assertTrue(HGVSpParser.matchesPattern("NP_123456.1:p.(Arg490Ser)"));
    }

    @Test
    public void testMatchesPattern_InvalidFormats() {
        assertFalse(HGVSpParser.matchesPattern(null));
        assertFalse(HGVSpParser.matchesPattern(""));
        assertFalse(HGVSpParser.matchesPattern("INVALID_123:p.R490S")); // invalid RefSeq
        assertFalse(HGVSpParser.matchesPattern("NP_123456.1:c.123A>T")); // wrong scheme
        assertFalse(HGVSpParser.matchesPattern("NP_123456.1:p.X490#")); // invalid AA
        assertFalse(HGVSpParser.matchesPattern("NP_123456.1:p.R490")); // missing alt
    }

    // ===== PARSING TESTS =====

    @Test
    public void testParse_ValidSingleLetterAA() {
        ProteinInput result = HGVSpParser.parse("NP_123456.1:p.R490S");

        assertEquals("NP_123456.1", result.getRefseqId());
        assertEquals(490, result.getPosition());
        assertEquals("R", result.getRefAA());
        assertEquals("S", result.getAltAA());
        assertEquals(VariantFormat.HGVS_PROTEIN, result.getFormat());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void testParse_ValidThreeLetterAA() {
        ProteinInput result = HGVSpParser.parse("NP_123456.1:p.Arg490Ser");

        assertEquals("NP_123456.1", result.getRefseqId());
        assertEquals(490, result.getPosition());
        assertEquals("R", result.getRefAA()); // normalized to single letter
        assertEquals("S", result.getAltAA()); // normalized to single letter
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void testParse_SynonymousVariant() {
        ProteinInput result = HGVSpParser.parse("NP_123456.1:p.R490=");

        assertEquals("NP_123456.1", result.getRefseqId());
        assertEquals(490, result.getPosition());
        assertEquals("R", result.getRefAA());
        assertEquals("R", result.getAltAA()); // normalized from "=" to ref AA
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void testParse_WithSpaceAfterColon() {
        ProteinInput result = HGVSpParser.parse("NP_123456.1: p.R490S");

        assertEquals("NP_123456.1", result.getRefseqId());
        assertEquals(490, result.getPosition());
        assertEquals("R", result.getRefAA());
        assertEquals("S", result.getAltAA());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void testParse_WithParentheses() {
        ProteinInput result = HGVSpParser.parse("NP_123456.1:p.(R490S)");

        assertEquals("NP_123456.1", result.getRefseqId());
        assertEquals(490, result.getPosition());
        assertEquals("R", result.getRefAA());
        assertEquals("S", result.getAltAA());
        assertTrue(result.getErrors().isEmpty());
    }

    // ===== ERROR HANDLING TESTS =====

    @Test
    public void testParse_InvalidRefSeq() {
        ProteinInput result = HGVSpParser.parse("INVALID_123:p.R490S");

        assertFalse(result.getErrors().isEmpty());
    }

    @Test
    public void testParse_InvalidVarDesc() {
        ProteinInput result = HGVSpParser.parse("NP_123456.1:p.INVALID");

        assertFalse(result.getErrors().isEmpty());
    }

    @Test
    public void testParse_InvalidStructure() {
        ProteinInput result = HGVSpParser.parse("completely_invalid_format");

        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().contains("HGVS p. invalid"));
    }

    @Test
    public void testParse_EmptyInput() {
        ProteinInput result = HGVSpParser.parse("");

        assertFalse(result.getErrors().isEmpty());
        // Should handle gracefully without throwing exception
    }

    // ===== EDGE CASES =====

    @Test
    public void testParse_StopCodon() {
        ProteinInput result = HGVSpParser.parse("NP_123456.1:p.R490*");

        assertEquals("NP_123456.1", result.getRefseqId());
        assertEquals(490, result.getPosition());
        assertEquals("R", result.getRefAA());
        assertEquals("*", result.getAltAA());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void testParse_StartCodon() {
        ProteinInput result = HGVSpParser.parse("NP_123456.1:p.M1V");

        assertEquals("NP_123456.1", result.getRefseqId());
        assertEquals(1, result.getPosition());
        assertEquals("M", result.getRefAA());
        assertEquals("V", result.getAltAA());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void testParse_HighPosition() {
        ProteinInput result = HGVSpParser.parse("NP_123456.1:p.R9999S");

        assertEquals("NP_123456.1", result.getRefseqId());
        assertEquals(9999, result.getPosition());
        assertEquals("R", result.getRefAA());
        assertEquals("S", result.getAltAA());
        assertTrue(result.getErrors().isEmpty());
    }
}