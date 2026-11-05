package uk.ac.ebi.protvar.service;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.model.data.Foldx;
import uk.ac.ebi.protvar.model.response.PredictionInfo;
import uk.ac.ebi.protvar.model.score.*;
import uk.ac.ebi.protvar.repo.FoldxRepo;
import uk.ac.ebi.protvar.repo.InteractionRepo;
import uk.ac.ebi.protvar.repo.PocketRepo;
import uk.ac.ebi.protvar.repo.ScoreRepo;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PredictionServiceTest {

    private final ScoreRepo scores = mock(ScoreRepo.class);
    private final PocketRepo pockets = mock(PocketRepo.class);
    private final InteractionRepo interactions = mock(InteractionRepo.class);
    private final FoldxRepo foldx = mock(FoldxRepo.class);
    private final PredictionService service = new PredictionService(scores, pockets, interactions, foldx);

    @Test
    void includeQueriesOnlyTheRequestedSources() {
        when(foldx.getFoldxs("P22304", 205, "P")).thenReturn(Map.of("k",
                List.of(new Foldx("P22304", 205, "F1", 205, "A", "P", 6.06, 98.9, 1))));
        PredictionInfo p = service.get("P22304", 205, "P", Set.of("foldx"));
        assertEquals(6.06, p.getFoldx().get(0).getFoldxDdg());
        assertTrue(p.getAlphamissense() == null || p.getAlphamissense().isEmpty());   // left out of the JSON
        verifyNoInteractions(scores, pockets, interactions);
    }

    @Test
    void scoresComeFromOneCallForTheRequestedTypesAndAreNarrowedToTheVariant() {
        when(scores.getScores(any(), any(), eq(EnumSet.of(ScoreType.CONSERV, ScoreType.AM))))
                .thenReturn(List.of(new ConservScore("P22304", 205, 0.918),
                        new AmScore("P22304", 205, "P", 0.99, null), new AmScore("P22304", 205, "G", 0.5, null)));
        PredictionInfo p = service.get("P22304", 205, "P", Set.of("conserv", "alphamissense"));
        assertEquals(0.918, p.getConserv().getScore());               // no variant: kept
        assertEquals(1, p.getAlphamissense().size());                 // G dropped
        assertEquals("P", p.getAlphamissense().get(0).getMt());
        verify(scores, times(1)).getScores(any(), any(), any());
        verifyNoInteractions(pockets, interactions, foldx);
    }
}
