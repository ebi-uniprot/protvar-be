package uk.ac.ebi.protvar.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import uk.ac.ebi.protvar.mapper.InputMapper;
import uk.ac.ebi.protvar.model.DownloadRequest;
import uk.ac.ebi.protvar.model.Identifier;
import uk.ac.ebi.protvar.model.MappingRequest;
import uk.ac.ebi.protvar.repo.CodingVariantRepo;
import uk.ac.ebi.protvar.types.IdentifierType;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class MappingServiceRoutingTest {

    private final CodingVariantRepo codingVariant = mock(CodingVariantRepo.class);
    private final MappingService service = new MappingService(mock(InputService.class), mock(UploadCacheService.class),
            codingVariant, mock(InputMapper.class), mock(UploadFilterService.class));

    private MappingRequest filterOnlyRequest() {
        MappingRequest r = new MappingRequest();
        r.setPocket(true);
        return r;
    }

    private MappingRequest idsRequest() {
        MappingRequest r = new MappingRequest();
        r.setIds(List.of(new Identifier(IdentifierType.GENE, "BRCA2")));
        r.setPage(1); r.setPageSize(10);
        return r;
    }

    @Test
    void downloadCountIsCountedAsABrowse() {
        // the repo skips the count for a DownloadRequest, so the pre-queue cap check must not pass one
        DownloadRequest download = new DownloadRequest();
        download.setPocket(true);
        download.setFull(true);
        when(codingVariant.get(any(), any())).thenReturn(Page.empty());
        service.countInputs(download);
        verify(codingVariant).get(argThat(r -> !(r instanceof DownloadRequest) && Boolean.TRUE.equals(r.getPocket())), any());
    }

    @Test
    void filterOnlyAndIdentifiersUseCodingVariant() {
        when(codingVariant.get(any(), any())).thenReturn(Page.empty());
        service.getInputs(filterOnlyRequest());
        service.countInputs(filterOnlyRequest());
        service.getInputs(idsRequest());   // a single identifier with no filter too
        service.countInputs(idsRequest());
        verify(codingVariant, times(4)).get(any(), any());
    }
}
