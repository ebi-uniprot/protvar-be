package uk.ac.ebi.protvar.model.response;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IsoformOrderTest {

    private static Isoform iso(String accession, boolean canonical) {
        return Isoform.builder().accession(accession).canonical(canonical).build();
    }

    private static List<String> sorted(Isoform... isoforms) {
        return Stream.of(isoforms).sorted().map(Isoform::getAccession).toList();
    }

    @Test
    void reviewedIsoformLeadsUnreviewedEntryWhenNoCanonical() {
        // chr21:33572551 (SON): only H7C257 (unreviewed) and P18583-4 (reviewed isoform) map there
        assertEquals(List.of("P18583-4", "H7C257"), sorted(iso("H7C257", false), iso("P18583-4", false)));
    }

    @Test
    void canonicalThenIsoformsThenRest() {
        assertEquals(List.of("Q9NYP3", "Q9NYP3-1", "Q9NYP3-2", "F8W8A5", "H7C1C1"),
                sorted(iso("H7C1C1", false), iso("Q9NYP3-2", false), iso("F8W8A5", false),
                        iso("Q9NYP3", true), iso("Q9NYP3-1", false)));
    }

    @Test
    void isoformsInNaturalSuffixOrder() {
        assertEquals(List.of("P12345-2", "P12345-10"), sorted(iso("P12345-10", false), iso("P12345-2", false)));
    }
}
