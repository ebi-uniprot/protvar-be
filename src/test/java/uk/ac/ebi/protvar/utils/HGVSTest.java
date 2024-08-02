package uk.ac.ebi.protvar.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HGVSTest {

    @Test
    void test_generalPattern() {
        assertFalse(HGVS.matchesPattern("")); // empty
        assertFalse(HGVS.matchesPattern(":")); // single colon
        assertFalse(HGVS.matchesPattern("xxx:")); // left side only
        assertFalse(HGVS.matchesPattern(":xxx")); // right side only
        assertFalse(HGVS.matchesPattern("xxx:yyy")); // no scheme
        assertFalse(HGVS.matchesPattern("xxx:xxx:zzz")); // multi colon

        assertTrue(HGVS.matchesPattern("xxx:g.yyy"));
        assertTrue(HGVS.matchesPattern("xxx:p.yyy"));
        assertTrue(HGVS.matchesPattern("xxx:c.yyy"));
        assertTrue(HGVS.matchesPattern("xxx:n.yyy"));
        assertTrue(HGVS.matchesPattern("xxx:m.yyy"));
        assertTrue(HGVS.matchesPattern("xxx:r.yyy"));
        assertTrue(HGVS.matchesPattern("xxx:x.yyy"));
        assertTrue(HGVS.matchesPattern("xxx: x.yyy")); // single space after : (lenient HGVS)
        assertTrue(HGVS.matchesPattern("xxx:  x.yyy")); // multiple space after : (lenient HGVS)
        assertTrue(HGVS.matchesPattern("xxx:    x.yyy")); // tab after : (lenient HGVS)
        assertFalse(HGVS.matchesPattern("xxx:*.yyy"));
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