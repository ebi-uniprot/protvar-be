package uk.ac.ebi.protvar.input.format.genomic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
    void test_invalid_prefix() {
        HGVSg g = HGVSg.parse("NX_000023.11:g.149498202C>G");
        assertTrue(g.hasError());
    }

    @ParameterizedTest
    @CsvSource({ "NC_000010.11:g.104259641G>C,104259641" })
    void testExtractLocation(String hgvs, String expected) {
        Integer actual = HGVSg.extractLocation(hgvs);
        assertEquals(Integer.parseInt(expected), actual);
    }
}