package uk.ac.ebi.protvar.types;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AminoAcidInitOrderTest {

    // A fresh class loader lets the test choose which enum initialises first (in the app Codon can load first).
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean isSNV(boolean codonFirst, String ref, String alt) throws Exception {
        URL classes = AminoAcid.class.getProtectionDomain().getCodeSource().getLocation();
        try (URLClassLoader fresh = new URLClassLoader(new URL[]{classes}, null)) {
            String pkg = AminoAcid.class.getPackageName();
            Class codon = Class.forName(pkg + ".Codon", false, fresh);
            Class aa = Class.forName(pkg + ".AminoAcid", false, fresh);
            Class.forName((codonFirst ? codon : aa).getName(), true, fresh);
            return (boolean) aa.getMethod("isSNV", aa, aa)
                    .invoke(null, Enum.valueOf(aa, ref), Enum.valueOf(aa, alt));
        }
    }

    @Test
    void snvWhicheverEnumLoadsFirst() throws Exception {
        assertEquals(true, isSNV(true, "ARG", "HIS"));   // CGC -> CAC
        assertEquals(true, isSNV(false, "ARG", "HIS"));
        assertEquals(false, isSNV(true, "ARG", "PHE"));  // needs 2+ changes
    }
}
