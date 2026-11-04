package uk.ac.ebi.protvar.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;
import uk.ac.ebi.protvar.cache.InputBuild;
import uk.ac.ebi.protvar.input.MessageCode;
import uk.ac.ebi.protvar.input.VariantInput;
import uk.ac.ebi.protvar.input.parser.genomic.GenomicParser;
import uk.ac.ebi.protvar.input.processor.BuildProcessor;
import uk.ac.ebi.protvar.types.Assembly;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InputServiceQueryBuildTest {

    private final BuildProcessor buildProcessor = mock(BuildProcessor.class);
    private final InputService service = new InputService(mock(AsyncTaskExecutor.class),
            mock(UploadCacheService.class), mock(UploadFilterService.class), buildProcessor);

    private void given(String line, Assembly detected) {
        List<VariantInput> genomic = List.of(GenomicParser.parse(line));
        when(buildProcessor.filterGenomicInputs(List.of(line))).thenReturn(genomic);
        when(buildProcessor.detect(any())).thenReturn(new InputBuild(detected,
                detected == Assembly.GRCH37 ? MessageCode.PV502_ASSEMBLY_GRCH37.message("100.00", "0.00") : null));
    }

    @Test
    void grch37LineIsConverted() { // TP53 R175H in GRCh37 coordinates
        given("17 7578406 C T", Assembly.GRCH37);
        InputBuild build = service.detectQueryBuild(List.of("17 7578406 C T"));
        assertNotNull(build);
        assertEquals(Assembly.GRCH37, build.getAssembly());
        assertNotNull(build.getMessage());
    }

    @Test
    void grch38OrUnclearStaysAsBefore() {
        given("17 7675088 C T", Assembly.GRCH38);
        assertNull(service.detectQueryBuild(List.of("17 7675088 C T")));
    }

    @Test
    void noReferenceBaseIsNotDetected() {
        given("17 7675088", Assembly.GRCH37);
        assertNull(service.detectQueryBuild(List.of("17 7675088")));
        verify(buildProcessor, never()).detect(any());
    }
}
