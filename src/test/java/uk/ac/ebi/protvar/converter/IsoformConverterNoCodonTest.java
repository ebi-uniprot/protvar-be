package uk.ac.ebi.protvar.converter;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.model.data.GenomeToProteinMapping;
import uk.ac.ebi.protvar.model.response.Isoform;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IsoformConverterNoCodonTest {

    private final IsoformConverter converter = new IsoformConverter();

    private static GenomeToProteinMapping row(String acc, String enst, String codon) {
        return GenomeToProteinMapping.builder().chromosome("21").genomeLocation(33735041).baseNucleotide("G")
                .accession(acc).enst(enst).isoformPosition(1).aa("M").codon(codon).codonPosition(1)
                .isCanonical(false).build();
    }

    @Test
    void noCodonKeepsTheIsoformWithoutTheChange() {
        List<Isoform> isoforms = converter.createIsoforms("A", List.of(row("H7C3F1", "ENST1", null)), Map.of());
        assertEquals(1, isoforms.size(), "a mapping without a codon is kept");
        Isoform iso = isoforms.get(0);
        assertEquals("H7C3F1", iso.getAccession());
        assertEquals(1, iso.getIsoformPosition());
        assertEquals("Met", iso.getRefAA());
        assertNull(iso.getVariantAA());
        assertNull(iso.getRefCodon());
        assertNull(iso.getConsequences());
        assertEquals("Met/?", iso.getAminoAcidChange());
        assertEquals("", iso.getCodonChange());
    }

    @Test
    void canonicalWithoutCodonStillLinksItsAnnotations() {
        GenomeToProteinMapping m = GenomeToProteinMapping.builder().chromosome("21").genomeLocation(33735041)
                .baseNucleotide("G").accession("P1").enst("ENST1").isoformPosition(5).aa("M").codonPosition(1)
                .isCanonical(true).build();
        Isoform iso = converter.createIsoforms("A", List.of(m), Map.of()).get(0);
        assertEquals("/function/P1/5", iso.getReferenceFunctionUri());
        assertEquals("/population/P1/5?genomicVariant=21-33735041-G-A", iso.getPopulationObservationsUri());
        assertEquals("/structure/P1/5", iso.getProteinStructureUri());
    }

    @Test
    void anotherTranscriptsCodonIsUsedWhenListedFirst() {
        // MappingRepo orders rows with a codon first; the converter then works the change out as before
        List<Isoform> isoforms = converter.createIsoforms("A",
                List.of(row("P1", "ENST2", "AUG"), row("P1", "ENST1", null)), Map.of());
        assertEquals("AUG", isoforms.get(0).getRefCodon());
        assertNotNull(isoforms.get(0).getVariantAA());
    }
}
