package uk.ac.ebi.protvar.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HGVSTest {

    @Test
    void test_generalPattern() {
        assertFalse(HGVS.generalPattern("")); // empty
        assertFalse(HGVS.generalPattern(":")); // single colon
        assertFalse(HGVS.generalPattern("xxx:")); // left side only
        assertFalse(HGVS.generalPattern(":xxx")); // right side only
        assertFalse(HGVS.generalPattern("xxx:yyy")); // no scheme
        assertFalse(HGVS.generalPattern("xxx:xxx:zzz")); // multi colon

        assertTrue(HGVS.generalPattern("xxx:g.yyy"));
        assertTrue(HGVS.generalPattern("xxx:p.yyy"));
        assertTrue(HGVS.generalPattern("xxx:c.yyy"));
        assertTrue(HGVS.generalPattern("xxx:n.yyy"));
        assertTrue(HGVS.generalPattern("xxx:m.yyy"));
        assertTrue(HGVS.generalPattern("xxx:r.yyy"));
        assertTrue(HGVS.generalPattern("xxx:x.yyy"));
        assertTrue(HGVS.generalPattern("xxx: x.yyy")); // single space after : (lenient HGVS)
        assertTrue(HGVS.generalPattern("xxx:  x.yyy")); // multiple space after : (lenient HGVS)
        assertTrue(HGVS.generalPattern("xxx:    x.yyy")); // tab after : (lenient HGVS)
        assertFalse(HGVS.generalPattern("xxx:*.yyy"));
    }

    @Test
    void test_splitRegexScheme() {
        String input = "xxx:g.yyy";
        String[] params = input.split(":g.");
        assertTrue(params.length == 2);
        assertTrue(params[0].equals("xxx"));
        assertTrue(params[1].equals("yyy"));
    }

    @Test
    void supportedPrefix_valid() {
        assertTrue(HGVS.supportedPrefix("NC_"));
        assertTrue(HGVS.supportedPrefix("NM_"));
        assertTrue(HGVS.supportedPrefix("NP_"));
        assertTrue(HGVS.supportedPrefix("np_"));
    }
    @Test
    void supportedPrefix_invalid() {
        assertFalse(HGVS.supportedPrefix("NG_"));
        assertFalse(HGVS.supportedPrefix("XX_"));
    }

    @Test
    void validRefSeq() {
        assertTrue(HGVS.validRefSeq("NC_000023.11"));
        assertTrue(HGVS.validRefSeq("NM_001145445.1"));
        assertTrue(HGVS.validRefSeq("NP_001138917.1"));
        assertTrue(HGVS.validRefSeq("NM_001145445"));
        assertTrue(HGVS.validRefSeq("NP_001138917"));
        assertFalse(HGVS.validRefSeq("NP_001138917."));
    }

}