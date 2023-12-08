package uk.ac.ebi.protvar.input.format.genomic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GnomadTest {

    @Test
    void test_valid() {
        assertTrue(Gnomad.isValid("X-149498202-C-G")); // upper case
        assertTrue(Gnomad.isValid("x-149498202-c-g")); // lower case / ignore case
    }

    @Test
    void test_invalid() {
        assertFalse(Gnomad.isValid(""));  // empty
        assertFalse(Gnomad.isValid("X 149498202 C G"));  // diff sep
        assertFalse(Gnomad.isValid("H-149498202-C-G"));  // invalid chr
        assertFalse(Gnomad.isValid("X-pos-C-G"));  // invalid pos
        assertFalse(Gnomad.isValid("X-149498202-E-G"));  // invalid dna
    }

}