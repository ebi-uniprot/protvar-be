package uk.ac.ebi.protvar.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;
import uk.ac.ebi.protvar.input.VariantInput;
import uk.ac.ebi.protvar.mapper.InputMapper;
import uk.ac.ebi.protvar.model.MappingRequest;
import uk.ac.ebi.protvar.repo.UploadVariantRepo;
import uk.ac.ebi.protvar.repo.UploadVariantRepo.Passing;
import uk.ac.ebi.protvar.repo.UploadVariantRepo.Upload;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UploadFilterChunksTest {

    private static final String ID = "abc";
    private final UploadVariantRepo repo = mock(UploadVariantRepo.class);
    private final UploadCacheService cache = mock(UploadCacheService.class);
    private final UploadFilterService service = new UploadFilterService(repo, cache, mock(InputMapper.class),
            mock(AsyncTaskExecutor.class));

    private static MappingRequest request() {
        MappingRequest r = new MappingRequest();
        r.setResultId(ID);
        r.setKnown(true);
        return r;
    }

    @Test
    void chunksPageThroughPassingLinesInOrder() {
        when(cache.getInput(ID)).thenReturn(List.of("1 100 A G", "P22304 A205P", "rs1042779", "19 1010539 G C"));
        when(repo.find(eq(ID), eq("AUTO"), any(), anyInt()))
                .thenReturn(Optional.of(new Upload(7, UploadVariantRepo.READY, 4, 4, false)));
        when(repo.pageLines(eq(7), any(), eq(0L), eq(2))).thenReturn(List.of(3, 1));
        when(repo.pageLines(eq(7), any(), eq(2L), eq(2))).thenReturn(List.of(0));
        when(repo.pageLines(eq(7), any(), eq(4L), eq(2))).thenReturn(List.of());
        Passing p = new Passing(3, "19", 1010539, "C", "P80404");
        when(repo.passingRows(eq(7), any(), eq(List.of(3, 1)))).thenReturn(List.of(p));
        when(repo.passingRows(eq(7), any(), eq(List.of(0)))).thenReturn(List.of());

        var chunks = service.chunks(request(), 2).toList();

        assertEquals(2, chunks.size());
        assertEquals(List.of(3, 1), chunks.get(0).page().getContent().stream().map(VariantInput::getOriginalIndex).toList());
        assertEquals(Set.of(p), chunks.get(0).passing());
        assertEquals(List.of(0), chunks.get(1).page().getContent().stream().map(VariantInput::getOriginalIndex).toList());
        verify(repo).touch(7);
    }

    @Test
    void chunksRefuseAnUnpreparedUpload() {
        when(cache.getInput(ID)).thenReturn(List.of("rs1042779"));
        when(repo.find(eq(ID), eq("AUTO"), any(), anyInt()))
                .thenReturn(Optional.of(new Upload(7, UploadVariantRepo.BUILDING, 1, 0, false)));
        assertThrows(IllegalStateException.class, () -> service.chunks(request(), 2));
    }
}
