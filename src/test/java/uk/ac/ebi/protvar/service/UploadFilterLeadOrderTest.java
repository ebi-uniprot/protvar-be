package uk.ac.ebi.protvar.service;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.model.data.GenomeToProteinMapping;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UploadFilterLeadOrderTest {

    private static GenomeToProteinMapping m(String acc, boolean canonical) {
        return GenomeToProteinMapping.builder().accession(acc).isCanonical(canonical).build();
    }

    @Test
    void canonicalThenReviewedIsoformThenRest() {
        List<String> order = Stream.of(m("A0A5F9ZHB7", false), m("P04637-10", false), m("P04637-2", false),
                        m("P04637", true))
                .sorted(UploadFilterService.LEAD_ORDER).map(GenomeToProteinMapping::getAccession).toList();
        assertEquals(List.of("P04637", "P04637-2", "P04637-10", "A0A5F9ZHB7"), order);
    }

    @Test
    void reviewedIsoformLeadsWhenNoCanonical() {
        assertEquals("Q9Y6K9-3", Stream.of(m("A0A024R0Y4", false), m("Q9Y6K9-3", false))
                .min(UploadFilterService.LEAD_ORDER).orElseThrow().getAccession());
    }
}
