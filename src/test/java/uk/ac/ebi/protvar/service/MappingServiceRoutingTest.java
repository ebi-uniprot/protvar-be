package uk.ac.ebi.protvar.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.test.util.ReflectionTestUtils;
import uk.ac.ebi.protvar.mapper.InputMapper;
import uk.ac.ebi.protvar.model.MappingRequest;
import uk.ac.ebi.protvar.repo.CodingVariantRepo;
import uk.ac.ebi.protvar.repo.GenomicVariantRepo;
import uk.ac.ebi.protvar.repo.MappingRepo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MappingServiceRoutingTest {

    private final GenomicVariantRepo legacy = mock(GenomicVariantRepo.class);
    private final CodingVariantRepo codingVariant = mock(CodingVariantRepo.class);
    private final MappingService service = new MappingService(mock(InputService.class), mock(UploadCacheService.class),
            mock(MappingRepo.class), legacy, codingVariant, mock(InputMapper.class), mock(UploadFilterService.class));

    private MappingRequest filterOnlyRequest() {
        MappingRequest r = new MappingRequest();
        r.setPocket(true);
        return r;
    }

    @Test
    void filterOnlyUsesLegacyByDefault() {
        when(legacy.get(any(), any())).thenReturn(Page.empty());
        service.getInputs(filterOnlyRequest());
        verify(legacy).get(any(), any());
        verifyNoInteractions(codingVariant);
    }

    @Test
    void filterOnlyUsesCodingVariantWhenEnabled() {
        ReflectionTestUtils.setField(service, "filterOnCodingVariant", true);
        when(codingVariant.get(any(), any())).thenReturn(Page.empty());
        service.getInputs(filterOnlyRequest());
        service.countInputs(filterOnlyRequest());
        verify(codingVariant, times(2)).get(any(), any());
        verifyNoInteractions(legacy);
    }
}
