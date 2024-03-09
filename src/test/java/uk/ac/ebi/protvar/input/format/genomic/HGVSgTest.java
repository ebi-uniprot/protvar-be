package uk.ac.ebi.protvar.input.format.genomic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uk.ac.ebi.protvar.input.ErrorConstants;

import static org.junit.jupiter.api.Assertions.*;

class HGVSgTest {

    @Test
    void test_valid() {
        HGVSg g = HGVSg.parse("NC_000023.11:g.149498202C>G");
        assertEquals(g.getChr(), "X");
        assertEquals(g.getPos(), 149498202);
        assertEquals(g.getRef(), "C");
        assertEquals(g.getAlt(), "G");
    }

    @Test
    void test_refseq_not_mapped_to_chr() {
        HGVSg g = HGVSg.parse("NC_000027.11:g.149498202C>G");
        assertEquals(g.getErrors().get(0), ErrorConstants.HGVS_G_REFSEQ_NOT_MAP_TO_CHR.toString());
    }

    @Test
    void test_invalid_refseq() {
        HGVSg g = HGVSg.parse("NX_000023.11:g.149498202C>G");
        assertEquals(g.getErrors().get(0), ErrorConstants.HGVS_G_REFSEQ_INVALID.toString());
    }

    @Test
    void test_invalid_ref() {
        HGVSg g = HGVSg.parse("NC_000023.11:g.149498202F>G");
        assertEquals(g.getErrors().get(0), ErrorConstants.HGVS_G_VARDESC_INVALID.toString());
    }

    @Test
    void test_invalid_alt() {
        HGVSg g = HGVSg.parse("NC_000023.11:g.149498202C>H");
        assertEquals(g.getErrors().get(0), ErrorConstants.HGVS_G_VARDESC_INVALID.toString());
    }

    @Test
    void test_invalid_var_desc() {
        HGVSg g = HGVSg.parse("NC_000023.11:g.1010");
        assertEquals(g.getErrors().get(0), ErrorConstants.HGVS_G_VARDESC_INVALID.toString());
    }

    @ParameterizedTest
    @CsvSource({ "NC_000010.11:g.104259641G>C,104259641" })
    void testExtractLocation(String hgvs, String expected) {
        Integer actual = HGVSg.extractLocation(hgvs);
        assertEquals(Integer.parseInt(expected), actual);
    }
}