package uk.ac.ebi.protvar.controller;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.repo.DatasetRepo;
import uk.ac.ebi.protvar.repo.DatasetRepo.Dataset;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatasetControllerTest {

    private final DatasetRepo repo = mock(DatasetRepo.class);
    private final DatasetController controller = new DatasetController(repo);

    @Test
    void aDatasetByNameOrNotFound() {
        Dataset humsavar = new Dataset("humsavar", "UniProt humsavar", "Curated variants", "UniProt 2026_03", 83859,
                "b9e311f4b64e6fa88cc0cb207965f63d");
        when(repo.list()).thenReturn(List.of(humsavar));
        assertEquals(humsavar, controller.dataset("humsavar").getBody());
        assertEquals(404, controller.dataset("clinvar").getStatusCode().value());
    }
}
