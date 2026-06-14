package uk.ac.ebi.protvar.utils;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.types.AminoAcid;
import uk.ac.ebi.protvar.types.Codon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class CodonTest {

    // rnaBaseForStrand must match the SQL rna_base_for_strand(dna_base, reverse_strand) in function.sql
    @Test
    void testRnaBaseForStrand() {
        // forward: T->U only
        assertEquals("A", Codon.rnaBaseForStrand("A", false));
        assertEquals("U", Codon.rnaBaseForStrand("T", false));
        assertEquals("G", Codon.rnaBaseForStrand("G", false));
        assertEquals("C", Codon.rnaBaseForStrand("C", false));
        // reverse: complement then T->U  (A->U, T->A, C->G, G->C)
        assertEquals("U", Codon.rnaBaseForStrand("A", true));
        assertEquals("A", Codon.rnaBaseForStrand("T", true));
        assertEquals("C", Codon.rnaBaseForStrand("G", true));
        assertEquals("G", Codon.rnaBaseForStrand("C", true));
    }

    // altAA must match the SQL alt-codon CASE + codon_table lookup
    @Test
    void testAltAA() {
        // forward strand, ref codon AUG (Met)
        assertEquals(AminoAcid.MET, Codon.altAA("AUG", 1, "A", false)); // A->A : AUG synonymous
        assertEquals(AminoAcid.LEU, Codon.altAA("AUG", 1, "C", false)); // C    : CUG Leu
        assertEquals(AminoAcid.LYS, Codon.altAA("AUG", 2, "A", false)); // U->A : AAG Lys
        assertEquals(AminoAcid.ILE, Codon.altAA("AUG", 3, "A", false)); // G->A : AUA Ile
        assertEquals(AminoAcid.TER, Codon.altAA("UAC", 3, "A", false)); // C->A : UAA stop gained

        // reverse strand: the genomic DNA alt is complemented then T->U before splicing
        assertEquals(AminoAcid.MET, Codon.altAA("AUG", 1, "T", true));  // T->A : AUG Met
        assertEquals(AminoAcid.LEU, Codon.altAA("AUG", 1, "A", true));  // A->U : UUG Leu
        assertEquals(AminoAcid.MET, Codon.altAA("AUG", 3, "C", true));  // C->G : AUG Met
    }

    // null-tolerant on malformed input (mirrors SQL LEFT JOIN codon_table miss)
    @Test
    void testAltAANullSafe() {
        assertNull(Codon.altAA(null, 1, "A", false));
        assertNull(Codon.altAA("AU", 1, "A", false));   // codon not length 3
        assertNull(Codon.altAA("AUG", 0, "A", false));  // bad codon position
        assertNull(Codon.altAA("AUG", 4, "A", false));
        assertNull(Codon.altAA("AUG", 1, null, false));
    }

    @Test
    void testChangeCount() {
        final int expectedSnv = 576;
        final int expectedDouble = 1728;
        final int expectedTriple = 1728;
        final int expectedNoChange = 64;

        int snvCount = 0, snvSyno = 0, snvStop = 0, snvMiss = 0;
        int doubleCount = 0, doubleSyno = 0, doubleStop = 0, doubleMiss = 0;
        int tripleCount = 0, tripleSyno = 0, tripleStop = 0, tripleMiss = 0;
        int noChangeCount = 0;

        for (Codon from : Codon.values()) {
            for (Codon to : Codon.values()) {
                Codon.Change change = Codon.type(from, to);
                if (change == Codon.Change.SNV) {
                    snvCount += 1;
                    switch (AminoAcid.getConsequence(from.getAa(), to.getAa())) {
                        case "synonymous":
                            snvSyno += 1;
                            break;
                        case "stop gained":
                            snvStop += 1;
                            break;
                        case "missense":
                            snvMiss += 1;
                            break;
                    }
                }
                else if (change == Codon.Change.DOUBLE) {
                    doubleCount += 1;
                    switch (AminoAcid.getConsequence(from.getAa(), to.getAa())) {
                        case "synonymous":
                            doubleSyno += 1;
                            break;
                        case "stop gained":
                            doubleStop += 1;
                            break;
                        case "missense":
                            doubleMiss += 1;
                            break;
                    }
                }
                else if (change == Codon.Change.TRIPLE) {
                    tripleCount += 1;
                    switch (AminoAcid.getConsequence(from.getAa(), to.getAa())) {
                        case "synonymous":
                            tripleSyno += 1;
                            break;
                        case "stop gained":
                            tripleStop += 1;
                            break;
                        case "missense":
                            tripleMiss += 1;
                            break;
                    }
                }
                else
                    noChangeCount += 1;
            }
        }
        assert(expectedSnv == snvCount &&
                expectedDouble == doubleCount &&
                expectedTriple == tripleCount &&
                expectedNoChange == noChangeCount);
    }

}
