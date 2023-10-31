package uk.ac.ebi.protvar.input.format.coding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HGVScTest {

    @Test
    void test_valid() {
        HGVSc g = HGVSc.parse("NM_004006.2:c.234C>G");
        assertEquals(g.getRsAcc(), "NM_004006.2");
        assertEquals(g.getCodingPos(), 234);
        assertEquals(g.getRef(), "C");
        assertEquals(g.getAlt(), "G");
    }
    @Test
    void test_invalid_prefix() {
        HGVSc g = HGVSc.parse("NX_004006.2:c.234C>G");
        assertTrue(g.hasError());
    }
}