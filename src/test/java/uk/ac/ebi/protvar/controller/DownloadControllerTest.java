package uk.ac.ebi.protvar.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import uk.ac.ebi.protvar.model.DownloadRequest;
import uk.ac.ebi.protvar.model.response.DownloadResponse;
import uk.ac.ebi.protvar.processor.DownloadFileCleanupTask;
import uk.ac.ebi.protvar.repo.GenomicVariantRepo;
import uk.ac.ebi.protvar.service.DownloadService;
import uk.ac.ebi.protvar.service.DownloadStatusService;
import uk.ac.ebi.protvar.service.MappingService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DownloadControllerTest {

    private final DownloadService downloadService = mock(DownloadService.class);
    private final MappingService mappingService = mock(MappingService.class);
    private final DownloadController controller = new DownloadController(downloadService,
            mock(DownloadStatusService.class), mock(DownloadFileCleanupTask.class), mappingService);
    private final HttpServletRequest http = mock(HttpServletRequest.class);

    DownloadControllerTest() {
        when(http.getRequestURL()).thenReturn(new StringBuffer("http://localhost/download"));
        when(downloadService.queueRequest(any())).thenReturn(new DownloadResponse());
    }

    private static DownloadRequest filteredUpload(boolean full) {
        DownloadRequest r = new DownloadRequest();
        r.setResultId("39458794254df4a3209a9180722dbf45");
        r.setKnown(true);
        r.setFull(full);
        return r;
    }

    private static DownloadRequest filterOnly() {
        DownloadRequest r = new DownloadRequest();
        r.setKnown(true);
        r.setFull(true);
        return r;
    }

    @Test
    void filteredUploadStillPreparingIsRefused() {
        when(mappingService.countFilteredUpload(any())).thenReturn(null);
        ResponseEntity<?> res = controller.handleDownload(filteredUpload(false), http);
        assertEquals(409, res.getStatusCode().value());
        verifyNoInteractions(downloadService);
    }

    @Test
    void filteredUploadFullUsesPassingLineCount() {
        // the upload itself may exceed the cap; its filtered lines don't
        when(mappingService.countFilteredUpload(any())).thenReturn(DownloadController.MAX_FULL_DOWNLOAD_ROWS);
        when(mappingService.countInputs(any())).thenReturn(DownloadController.MAX_FULL_DOWNLOAD_ROWS + 1);
        assertEquals(200, controller.handleDownload(filteredUpload(true), http).getStatusCode().value());
        verify(mappingService, never()).countInputs(any());
    }

    @Test
    void filteredUploadFullOverCapIsRefused() {
        when(mappingService.countFilteredUpload(any())).thenReturn(DownloadController.MAX_FULL_DOWNLOAD_ROWS + 1);
        assertEquals(400, controller.handleDownload(filteredUpload(true), http).getStatusCode().value());
        verifyNoInteractions(downloadService);
    }

    @Test
    void filterOnlyFullOverBrowseCapIsRefused() {
        when(mappingService.countInputs(any())).thenReturn(GenomicVariantRepo.COUNT_CAP + 1L);
        assertEquals(400, controller.handleDownload(filterOnly(), http).getStatusCode().value());
        verifyNoInteractions(downloadService);
    }

    @Test
    void filterOnlyFullWithinBrowseCapIsQueued() {
        when(mappingService.countInputs(any())).thenReturn((long) GenomicVariantRepo.COUNT_CAP);
        assertEquals(200, controller.handleDownload(filterOnly(), http).getStatusCode().value());
        verify(downloadService).queueRequest(any());
    }
}
