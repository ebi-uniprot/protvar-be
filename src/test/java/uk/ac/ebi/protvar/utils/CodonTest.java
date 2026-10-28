package uk.ac.ebi.protvar.utils;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.types.AminoAcid;
import uk.ac.ebi.protvar.types.Codon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class CodonTest {

    // rnaBaseForStrand must match the SQL rna_base_for_strand(dna_base, reverse_strand) in protvar-production db/db_finalise.sql
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

    // genomicBase: genome reference base from the RNA codon (inverse of rnaBaseForStrand)
    @Test
    void testGenomicBase() {
        // forward: U->T, others unchanged; position is 1-based; case-insensitive (codons store mixed case)
        assertEquals("A", Codon.genomicBase("AUG", 1, false));
        assertEquals("T", Codon.genomicBase("AUG", 2, false));
        assertEquals("G", Codon.genomicBase("aUg", 3, false));
        // reverse: complement back to the forward strand (A->T, U->A, G->C, C->G)
        assertEquals("T", Codon.genomicBase("AUG", 1, true));
        assertEquals("A", Codon.genomicBase("AUG", 2, true));
        assertEquals("C", Codon.genomicBase("AUG", 3, true));
        // real row: Q9NYP3 at 21:33579418, codon "Gua", codon_position 1, reverse strand -> genome C (CADD ref)
        assertEquals("C", Codon.genomicBase("Gua", 1, true));
        // round trip with rnaBaseForStrand, both strands, every base
        for (String dna : new String[]{"A", "C", "G", "T"}) {
            for (boolean reverse : new boolean[]{false, true}) {
                String rna = Codon.rnaBaseForStrand(dna, reverse);
                assertEquals(dna, Codon.genomicBase(rna + "AA", 1, reverse));
            }
        }
    }

    @Test
    void testGenomicBaseNullSafe() {
        assertNull(Codon.genomicBase(null, 1, false));
        assertNull(Codon.genomicBase("AU", 1, false));
        assertNull(Codon.genomicBase("AUG", 0, false));
        assertNull(Codon.genomicBase("AUG", 4, true));
        assertNull(Codon.genomicBase("NUG", 1, false));
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
