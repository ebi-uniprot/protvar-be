package uk.ac.ebi.protvar.types;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssemblyTest {

    @Test
    void autodetectWhenAutoOrNoneGiven() {
        assertTrue(Assembly.autodetect(null));   // the web sends no assembly for its default (auto)
        assertTrue(Assembly.autodetect("auto"));
        assertFalse(Assembly.autodetect("GRCh37"));
        assertFalse(Assembly.autodetect("GRCh38"));
    }
}
