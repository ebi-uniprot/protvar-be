package uk.ac.ebi.protvar.input.parser.hgvs;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.HGVSCodingInput;
import uk.ac.ebi.protvar.input.VariantFormat;

import static org.junit.jupiter.api.Assertions.*;

public class HGVScParserTest {

    @Test
    public void testMatchesStructure_ValidFormats() {
        assertTrue(HGVScParser.matchesStructure("NM_000001.1:c.123A>T"));
        assertTrue(HGVScParser.matchesStructure("NP_000001.1:c.456C>G"));
        assertTrue(HGVScParser.matchesStructure("NM_123456.1: c.789T>A")); // with space
        assertTrue(HGVScParser.matchesStructure("NM_000001.1(GENE):c.123A>T")); // with gene
        assertTrue(HGVScParser.matchesStructure("NM_017547.4(FOXRED1):c.1289A>G p.(Asn430Ser)")); // with protein
    }

    @Test
    public void testMatchesStructure_InvalidFormats() {
        assertFalse(HGVScParser.matchesStructure(null));
        assertFalse(HGVScParser.matchesStructure(""));
        assertFalse(HGVScParser.matchesStructure("NM_123456.1:g.123A>T")); // wrong scheme
        assertFalse(HGVScParser.matchesStructure("invalid_format"));
    }

    @Test
    public void testMatchesPattern_ValidFormats() {
        assertTrue(HGVScParser.matchesPattern("NM_000001.1:c.123A>T"));
        assertTrue(HGVScParser.matchesPattern("NP_000001.1:c.456C>G"));
        assertTrue(HGVScParser.matchesPattern("NM_000001.1:c.123A>T p.(Arg41Ser)"));
        assertTrue(HGVScParser.matchesPattern("NM_000001.1:c.123A>T (p.Arg41Ser)"));
    }

    @Test
    public void testParse_ValidInput() {
        HGVSCodingInput result = HGVScParser.parse("NM_000001.1:c.123A>T");

        assertEquals("NM_000001.1", result.getRefseqId());
        assertEquals(123, result.getPosition());
        assertEquals("A", result.getRefBase());
        assertEquals("T", result.getAltBase());
        assertEquals(VariantFormat.HGVS_CODING, result.getFormat());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void testParse_WithGeneSymbol() {
        HGVSCodingInput result = HGVScParser.parse("NM_000001.1 (GENE1):c.123A>T");

        assertEquals("NM_000001.1", result.getRefseqId());
        assertEquals("GENE1", result.getGeneSymbol());
        assertEquals(123, result.getPosition());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void testParse_ProteinAnnotationVariants() {
        // Test all your example formats

        // Format 1: NM_017547.4(FOXRED1):c.1289A>Gp.(Asn430Ser)
        HGVSCodingInput result = HGVScParser.parse("NM_017547.4(FOXRED1):c.1289A>Gp.(Asn430Ser)");
        assertEquals("NM_017547.4", result.getRefseqId());
        assertEquals("FOXRED1", result.getGeneSymbol());
        assertEquals(1289, result.getPosition());
        assertEquals("A", result.getRefBase());
        assertEquals("G", result.getAltBase());
        assertEquals("Asn", result.getRefAA());
        assertEquals("Ser", result.getAltAA());
        assertEquals(Integer.valueOf(430), result.getAaPos());
        assertTrue(result.getErrors().isEmpty());

        // Format 2: NM_017547.4 (FOXRED1):c.1289A>G p.(Asn430Ser)
        result = HGVScParser.parse("NM_017547.4 (FOXRED1):c.1289A>G p.(Asn430Ser)");
        assertEquals("NM_017547.4", result.getRefseqId());
        assertEquals("FOXRED1", result.getGeneSymbol());
        assertEquals(1289, result.getPosition());
        assertEquals("A", result.getRefBase());
        assertEquals("G", result.getAltBase());
        assertEquals("Asn", result.getRefAA());
        assertEquals("Ser", result.getAltAA());
        assertEquals(Integer.valueOf(430), result.getAaPos());
        assertTrue(result.getErrors().isEmpty());

        // Format 3: NM_017547.4(FOXRED1):c.1289A>G p.(Asn430Ser)
        result = HGVScParser.parse("NM_017547.4(FOXRED1):c.1289A>G p.(Asn430Ser)");
        assertEquals("NM_017547.4", result.getRefseqId());
        assertEquals("FOXRED1", result.getGeneSymbol());
        assertEquals(1289, result.getPosition());
        assertEquals("A", result.getRefBase());
        assertEquals("G", result.getAltBase());
        assertEquals("Asn", result.getRefAA());
        assertEquals("Ser", result.getAltAA());
        assertEquals(Integer.valueOf(430), result.getAaPos());
        assertTrue(result.getErrors().isEmpty());

        // Format 4: NM_017547.4:c.1289A>G (p.Asn430Ser)
        result = HGVScParser.parse("NM_017547.4:c.1289A>G (p.Asn430Ser)");
        assertEquals("NM_017547.4", result.getRefseqId());
        assertNull(result.getGeneSymbol()); // No gene symbol in this format
        assertEquals(1289, result.getPosition());
        assertEquals("A", result.getRefBase());
        assertEquals("G", result.getAltBase());
        assertEquals("Asn", result.getRefAA());
        assertEquals("Ser", result.getAltAA());
        assertEquals(Integer.valueOf(430), result.getAaPos());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void testParse_ProteinAnnotationWithEquals() {
        HGVSCodingInput result = HGVScParser.parse("NM_000001.1:c.123A>A p.(Arg41=)");

        assertEquals("NM_000001.1", result.getRefseqId());
        assertEquals(123, result.getPosition());
        assertEquals("A", result.getRefBase());
        assertEquals("A", result.getAltBase());
        assertEquals("Arg", result.getRefAA());
        assertEquals("Arg", result.getAltAA()); // Should be normalized from "=" to "Arg"
        assertEquals(Integer.valueOf(41), result.getAaPos());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void testParse_ProteinAnnotationWithStopCodon() {
        HGVSCodingInput result = HGVScParser.parse("NM_000001.1:c.123A>T (p.Arg41*)");

        assertEquals("NM_000001.1", result.getRefseqId());
        assertEquals(123, result.getPosition());
        assertEquals("A", result.getRefBase());
        assertEquals("T", result.getAltBase());
        assertEquals("Arg", result.getRefAA());
        assertEquals("*", result.getAltAA());
        assertEquals(Integer.valueOf(41), result.getAaPos());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void testParse_UnsupportedPositions() {
        // 5' UTR
        HGVSCodingInput result = HGVScParser.parse("NM_000001.1:c.-128A>G");
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().get(0).contains("5' UTR positions are not supported"));

        // Intronic 5' side
        result = HGVScParser.parse("NM_000001.1:c.128+1G>A");
        assertTrue(result.getErrors().get(0).contains("Intronic positions (5' side) are not supported"));

        // Intronic 3' side
        result = HGVScParser.parse("NM_000001.1:c.128-1G>A");
        assertTrue(result.getErrors().get(0).contains("Intronic positions (3' side) are not supported"));

        // 3' UTR
        result = HGVScParser.parse("NM_000001.1:c.*128A>G");
        assertTrue(result.getErrors().get(0).contains("3' UTR positions are not supported"));
    }

    @Test
    public void testParse_InvalidRefSeq() {
        HGVSCodingInput result = HGVScParser.parse("INVALID_123:c.123A>T");

        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().contains(ErrorConstants.HGVS_C_REFSEQ_INVALID.toString()));
    }

    @Test
    public void testParse_InvalidVarDesc() {
        HGVSCodingInput result = HGVScParser.parse("NM_000001.1:c.INVALID");

        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().contains(ErrorConstants.HGVS_C_VARDESC_INVALID.toString()));
    }

    @Test
    public void testParse_InvalidStructure() {
        HGVSCodingInput result = HGVScParser.parse("completely_invalid_format");

        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().contains("HGVS c. invalid"));
    }
}