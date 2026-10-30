package uk.ac.ebi.protvar.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.AsyncTaskExecutor;
import uk.ac.ebi.protvar.mapper.InputMapper;
import uk.ac.ebi.protvar.repo.UploadVariantRepo;
import uk.ac.ebi.protvar.repo.UploadVariantRepo.Row;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UploadFilterBuildTest {

    private final UploadVariantRepo repo = mock(UploadVariantRepo.class);
    private final InputMapper inputMapper = mock(InputMapper.class);
    private final UploadFilterService service = new UploadFilterService(repo, mock(UploadCacheService.class),
            inputMapper, mock(AsyncTaskExecutor.class));

    @Test
    @SuppressWarnings("unchecked")
    void rowsTakeTheReferenceBaseFromCodingVariant() {
        when(inputMapper.resolveAltBases(any(), any(), any())).thenCallRealMethod();
        when(repo.refBases(any(), any())).thenReturn(Map.of("1:100", "A", "1:200", "G", "1:300", "C"));
        when(repo.insertFromCodingVariant(anyInt(), anyList())).thenAnswer(i -> ((List<?>) i.getArgument(1)).size());

        service.build(7, List.of("1 100 A G", "1 200 C T", "1 300", "2 400 A G"), "AUTO", null);

        ArgumentCaptor<String[]> chrs = ArgumentCaptor.forClass(String[].class);
        ArgumentCaptor<Integer[]> poss = ArgumentCaptor.forClass(Integer[].class);
        verify(repo).refBases(chrs.capture(), poss.capture());
        assertEquals(Set.of("1:100", "1:200", "1:300", "2:400"), IntStream.range(0, chrs.getValue().length)
                .mapToObj(i -> chrs.getValue()[i] + ":" + poss.getValue()[i]).collect(Collectors.toSet()));

        ArgumentCaptor<List<Row>> rows = ArgumentCaptor.forClass(List.class);
        verify(repo).insertFromCodingVariant(eq(7), rows.capture());
        assertEquals(Set.of(
                new Row(0, "1", 100, "A", "G"),
                new Row(1, "1", 200, "G", "T"),            // input ref C corrected to G (PV-201)
                new Row(2, "1", 300, "C", "A"), new Row(2, "1", 300, "C", "G"), new Row(2, "1", 300, "C", "T")),
                Set.copyOf(rows.getValue()));              // 2:400 not in coding_variant: no rows, no notes

        ArgumentCaptor<Map<Integer, List<String>>> codes = ArgumentCaptor.forClass(Map.class);
        verify(repo).insertLines(eq(7), codes.capture());
        assertEquals(Map.of(1, List.of("PV-201"), 2, List.of("PV-202")), codes.getValue());
        verify(repo).finish(7, 5);
        verify(repo, never()).fail(anyInt());
    }
}
