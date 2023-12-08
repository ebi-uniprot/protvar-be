package uk.ac.ebi.protvar.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefSeqUtilsTest {

    @Test
    void validRefSeqPrefix() {
        assertTrue(RefSeqUtils.validRefSeqPrefix("NC_"));
        assertTrue(RefSeqUtils.validRefSeqPrefix("NM_"));
        assertTrue(RefSeqUtils.validRefSeqPrefix("NP_"));
        assertFalse(RefSeqUtils.validRefSeqPrefix("XX_"));
    }

    @Test
    void validRefSeqId() {
        assertTrue(RefSeqUtils.validRefSeqId("NC_000023.11"));
        assertTrue(RefSeqUtils.validRefSeqId("NM_001145445.1"));
        assertTrue(RefSeqUtils.validRefSeqId("NP_001138917.1"));
        assertTrue(RefSeqUtils.validRefSeqId("NM_001145445"));
        assertTrue(RefSeqUtils.validRefSeqId("NP_001138917"));
        assertFalse(RefSeqUtils.validRefSeqId("NP_001138917."));
    }
}