package uk.ac.ebi.protvar.input.parser.hgvs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.GenomicInput;

import static org.junit.jupiter.api.Assertions.*;

class HGVSgParserTest {

    @Test
    void test_valid() {
        GenomicInput g = HGVSgParser.parse("NC_000023.11:g.149498202C>G");
        assertEquals(g.getChromosome(), "X");
        assertEquals(g.getPosition(), 149498202);
        assertEquals(g.getRefBase(), "C");
        assertEquals(g.getAltBase(), "G");
    }

    @Test
    void test_refseq_not_mapped_to_chr() {
        GenomicInput g = HGVSgParser.parse("NC_000027.11:g.149498202C>G");
        assertEquals(g.getErrors().get(0), ErrorConstants.HGVS_G_REFSEQ_NOT_MAP_TO_CHR.toString());
    }

    @Test
    void test_invalid_refseq() {
        GenomicInput g = HGVSgParser.parse("NX_000023.11:g.149498202C>G");
        assertEquals(g.getErrors().get(0), ErrorConstants.HGVS_G_REFSEQ_INVALID.toString());
    }

    @Test
    void test_invalid_ref() {
        GenomicInput g = HGVSgParser.parse("NC_000023.11:g.149498202F>G");
        assertEquals(g.getErrors().get(0), ErrorConstants.HGVS_G_VARDESC_INVALID.toString());
    }

    @Test
    void test_invalid_alt() {
        GenomicInput g = HGVSgParser.parse("NC_000023.11:g.149498202C>H");
        assertEquals(g.getErrors().get(0), ErrorConstants.HGVS_G_VARDESC_INVALID.toString());
    }

    @Test
    void test_invalid_var_desc() {
        GenomicInput g = HGVSgParser.parse("NC_000023.11:g.1010");
        assertEquals(g.getErrors().get(0), ErrorConstants.HGVS_G_VARDESC_INVALID.toString());
    }

    @ParameterizedTest
    @CsvSource({ "NC_000010.11:g.104259641G>C,104259641" })
    void testExtractLocation(String hgvs, String expected) {
        Integer actual = HGVSgParser.extractLocation(hgvs);
        assertEquals(Integer.parseInt(expected), actual);
    }
}