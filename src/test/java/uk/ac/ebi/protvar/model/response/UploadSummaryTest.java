package uk.ac.ebi.protvar.model.response;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UploadSummaryTest {

    private final UploadSummary base = UploadSummary.of(10, Map.of("protein", 8, "genomic", 2));

    @Test
    void whilePreparingKeepsLinesAndFormatsWithProgress() {
        UploadSummary s = base.with(UploadSummary.preparing(40));
        assertEquals(10, s.lines());
        assertEquals(Map.of("protein", 8, "genomic", 2), s.formats());
        assertEquals(40, s.preparing());
        assertNull(s.mapped());
    }

    @Test
    void preparedAddsCounts() {
        UploadSummary s = base.with(UploadSummary.prepared(10, 8, 3, Map.of("PV-110", 2L)));
        assertNull(s.preparing());
        assertEquals(8, s.mapped());
        assertEquals(2, s.notMapped());
        assertEquals(3, s.withNotes());
        assertEquals(Map.of("PV-110", 2L), s.codes());
        assertEquals(Map.of("protein", 8, "genomic", 2), s.formats());
    }
}
