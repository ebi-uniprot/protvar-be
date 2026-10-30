package uk.ac.ebi.protvar.input.processor;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import uk.ac.ebi.protvar.repo.CrossmapRepo;
import uk.ac.ebi.protvar.types.Assembly;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class BuildProcessorTest {

    @Test
    @SuppressWarnings("unchecked")
    void repeatedLinesCountOnceInTheAssemblyMatch() {
        CrossmapRepo crossmap = mock(CrossmapRepo.class);
        when(crossmap.getPercentageMatch(anyList(), eq("38"))).thenReturn(100.0);
        when(crossmap.getPercentageMatch(anyList(), eq("37"))).thenReturn(0.0);
        BuildProcessor processor = new BuildProcessor();
        ReflectionTestUtils.setField(processor, "crossmapRepo", crossmap);

        var inputs = processor.filterGenomicInputs(
                List.of("1 100 A G", "1 100 A G", "1 100 A G", "2 200 C T", "2 200 C T"));
        assertEquals(Assembly.GRCH38, processor.detect(inputs).getAssembly());

        ArgumentCaptor<List<Object[]>> sample = ArgumentCaptor.forClass(List.class);
        verify(crossmap).getPercentageMatch(sample.capture(), eq("38"));
        assertEquals(2, sample.getValue().size());   // the two distinct positions, not five lines
    }
}
