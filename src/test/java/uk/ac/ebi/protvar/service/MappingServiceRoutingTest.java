package uk.ac.ebi.protvar.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.test.util.ReflectionTestUtils;
import uk.ac.ebi.protvar.mapper.InputMapper;
import uk.ac.ebi.protvar.model.Identifier;
import uk.ac.ebi.protvar.model.MappingRequest;
import uk.ac.ebi.protvar.repo.CodingVariantRepo;
import uk.ac.ebi.protvar.repo.GenomicVariantRepo;
import uk.ac.ebi.protvar.repo.MappingRepo;
import uk.ac.ebi.protvar.types.IdentifierType;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MappingServiceRoutingTest {

    private final GenomicVariantRepo legacy = mock(GenomicVariantRepo.class);
    private final CodingVariantRepo codingVariant = mock(CodingVariantRepo.class);
    private final MappingRepo mappingRepo = mock(MappingRepo.class);
    private final MappingService service = new MappingService(mock(InputService.class), mock(UploadCacheService.class),
            mappingRepo, legacy, codingVariant, mock(InputMapper.class), mock(UploadFilterService.class));

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

    private MappingRequest idsRequest() {
        MappingRequest r = new MappingRequest();
        r.setIds(List.of(new Identifier(IdentifierType.GENE, "BRCA2")));
        r.setPage(1); r.setPageSize(10);
        return r;
    }

    @Test
    void idsUseMappingRepoByDefault() {
        when(mappingRepo.getGenomicVariantsForInput(any(), any())).thenReturn(Page.empty());
        service.getInputs(idsRequest());
        verify(mappingRepo).getGenomicVariantsForInput(any(), any());
        verifyNoInteractions(codingVariant);
    }

    @Test
    void idsUseCodingVariantWhenEnabledAndServed() {
        ReflectionTestUtils.setField(service, "filterOnCodingVariant", true);
        when(codingVariant.servesIds(any())).thenReturn(true);
        when(codingVariant.get(any(), any())).thenReturn(Page.empty());
        service.getInputs(idsRequest());
        service.countInputs(idsRequest());
        verify(codingVariant, times(2)).get(any(), any());
        verifyNoInteractions(mappingRepo);
    }

    @Test
    void idsStayOnMappingRepoWhenNotServed() {
        ReflectionTestUtils.setField(service, "filterOnCodingVariant", true);
        when(codingVariant.servesIds(any())).thenReturn(false);
        when(mappingRepo.getGenomicVariantsForInput(any(), any())).thenReturn(Page.empty());
        service.getInputs(idsRequest());
        verify(mappingRepo).getGenomicVariantsForInput(any(), any());
        verify(codingVariant, never()).get(any(), any());
    }
}
