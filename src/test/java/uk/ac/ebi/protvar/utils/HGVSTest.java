package uk.ac.ebi.protvar.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HGVSTest {

    @Test
    void test_generalRefSeqVarDesc() {
        assertFalse(HGVS.generalRefSeqVarDesc("")); // empty
        assertFalse(HGVS.generalRefSeqVarDesc(":")); // single colon
        assertFalse(HGVS.generalRefSeqVarDesc("xxx:")); // left side only
        assertFalse(HGVS.generalRefSeqVarDesc(":xxx")); // right side only
        assertTrue(HGVS.generalRefSeqVarDesc("xxx:yyy")); // VALID
        assertFalse(HGVS.generalRefSeqVarDesc("xxx:xxx:zzz")); // multi colon
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