package uk.ac.ebi.protvar.input.parser.hgvs;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.GenomicInput;
import uk.ac.ebi.protvar.input.VariantFormat;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for HGVSgParser
 */
public class HGVSgParserTest {

    @Test
    public void testMatchesStructure_ValidFormats() {
        assertTrue(HGVSgParser.matchesStructure("NC_000001.11:g.123A>T"));
        assertTrue(HGVSgParser.matchesStructure("NC_000023.10:g.456C>G"));
        assertTrue(HGVSgParser.matchesStructure("NC_123456: g.789T>A")); // with space
    }

    @Test
    public void testMatchesStructure_InvalidFormats() {
        assertFalse(HGVSgParser.matchesStructure(null));
        assertFalse(HGVSgParser.matchesStructure(""));
        assertFalse(HGVSgParser.matchesStructure("NC_123456.1:c.123A>T")); // wrong scheme
        assertFalse(HGVSgParser.matchesStructure("invalid_format"));
    }

    @Test
    public void testMatchesPattern_ValidFormats() {
        assertTrue(HGVSgParser.matchesPattern("NC_000001.11:g.123A>T"));
        assertTrue(HGVSgParser.matchesPattern("NC_000023.10:g.456C>G"));
        assertTrue(HGVSgParser.matchesPattern("NC_999999.99:g.123456789T>A"));
    }

    @Test
    public void testMatchesPattern_InvalidFormats() {
        assertFalse(HGVSgParser.matchesPattern(null));
        assertFalse(HGVSgParser.matchesPattern("NM_123456.1:g.123A>T")); // wrong prefix
        assertFalse(HGVSgParser.matchesPattern("NC_123456.1:c.123A>T")); // wrong scheme
        assertFalse(HGVSgParser.matchesPattern("NC_123456.1:g.123X>T")); // invalid base
    }

    @Test
    public void testParse_ValidInput() {
        GenomicInput result = HGVSgParser.parse("NC_000001.11:g.123A>T");

        assertEquals("NC_000001.11", result.getRefseqId());
        assertEquals(123, result.getPosition());
        assertEquals("A", result.getRefBase());
        assertEquals("T", result.getAltBase());
        assertEquals(VariantFormat.HGVS_GENOMIC, result.getFormat());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void testParse_WithSpaceAfterColon() {
        GenomicInput result = HGVSgParser.parse("NC_000001.11: g.123A>T");

        assertEquals("NC_000001.11", result.getRefseqId());
        assertEquals(123, result.getPosition());
        assertEquals("A", result.getRefBase());
        assertEquals("T", result.getAltBase());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void testParse_InvalidRefSeq() {
        GenomicInput result = HGVSgParser.parse("INVALID_123:g.123A>T");

        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().contains(ErrorConstants.HGVS_G_REFSEQ_INVALID.toString()));
    }

    @Test
    public void testParse_InvalidVarDesc() {
        GenomicInput result = HGVSgParser.parse("NC_000001.11:g.INVALID");

        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().contains(ErrorConstants.HGVS_G_VARDESC_INVALID.toString()));
    }

    @Test
    public void testParse_InvalidStructure() {
        GenomicInput result = HGVSgParser.parse("completely_invalid_format");

        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().contains("HGVS g. invalid"));
    }
}