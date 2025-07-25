package uk.ac.ebi.protvar.input.parser.hgvs;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.input.VariantInput;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for HGVS utility class
 */
class HGVSTest {
    @Test
    void test_generalPattern() {
        assertFalse(HGVS.matchesStructure("")); // empty
        assertFalse(HGVS.matchesStructure(":")); // single colon
        assertFalse(HGVS.matchesStructure("xxx:")); // left side only
        assertFalse(HGVS.matchesStructure(":xxx")); // right side only
        assertFalse(HGVS.matchesStructure("xxx:yyy")); // no scheme
        assertFalse(HGVS.matchesStructure("xxx:xxx:zzz")); // multi colon

        assertTrue(HGVS.matchesStructure("xxx:g.yyy"));
        assertTrue(HGVS.matchesStructure("xxx:p.yyy"));
        assertTrue(HGVS.matchesStructure("xxx:c.yyy"));
        assertTrue(HGVS.matchesStructure("xxx:n.yyy"));
        assertTrue(HGVS.matchesStructure("xxx:m.yyy"));
        assertTrue(HGVS.matchesStructure("xxx:r.yyy"));
        assertTrue(HGVS.matchesStructure("xxx:x.yyy"));
        assertTrue(HGVS.matchesStructure("xxx: x.yyy")); // single space after : (lenient HGVS)
        assertTrue(HGVS.matchesStructure("xxx:  x.yyy")); // multiple space after : (lenient HGVS)
        assertTrue(HGVS.matchesStructure("xxx:    x.yyy")); // tab after : (lenient HGVS)
        assertFalse(HGVS.matchesStructure("xxx:*.yyy"));
    }

    @Test
    public void testMatchesStructure_ValidFormats() {
        assertTrue(HGVS.matchesStructure("NC_000001.11:g.123A>T"));
        assertTrue(HGVS.matchesStructure("NM_000001.1:c.123A>T"));
        assertTrue(HGVS.matchesStructure("NP_000001.1:p.R123S"));
        assertTrue(HGVS.matchesStructure("NC_123: g.456C>G")); // with space
    }

    @Test
    public void testMatchesStructure_InvalidFormats() {
        assertFalse(HGVS.matchesStructure(null));
        assertFalse(HGVS.matchesStructure(""));
        assertFalse(HGVS.matchesStructure("   "));
        assertFalse(HGVS.matchesStructure("no_colon_format"));
        assertFalse(HGVS.matchesStructure("NC_123:invalid"));
    }

    @Test
    public void testInvalid_UnsupportedSchemes() {
        VariantInput result = HGVS.invalid("NM_123456.1:n.123A>T");
        assertFalse(result.getErrors().isEmpty());

        result = HGVS.invalid("NC_123456.1:m.123A>T");
        assertFalse(result.getErrors().isEmpty());

        result = HGVS.invalid("NM_123456.1:r.123A>T");
        assertFalse(result.getErrors().isEmpty());
    }

    @Test
    public void testInvalid_UnsupportedPrefixes() {
        VariantInput result = HGVS.invalid("NG_123456.1:g.123A>T");
        assertFalse(result.getErrors().isEmpty());

        result = HGVS.invalid("LRG_123:g.123A>T");
        assertFalse(result.getErrors().isEmpty());

        result = HGVS.invalid("NR_123456.1:n.123A>T");
        assertFalse(result.getErrors().isEmpty());
    }

    @Test
    public void testInvalid_InvalidScheme() {
        VariantInput result = HGVS.invalid("NC_123456.1:x.123A>T");
        assertFalse(result.getErrors().isEmpty());
    }
}