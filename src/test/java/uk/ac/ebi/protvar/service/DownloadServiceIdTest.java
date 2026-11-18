package uk.ac.ebi.protvar.service;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import uk.ac.ebi.protvar.model.response.DownloadState;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DownloadServiceIdTest {

    @Test
    void onlyUuidsAreJobIds() {
        assertTrue(DownloadService.isJobId("cc8a9a02-d270-431b-8166-4dfc3e418e5e"));
        assertTrue(DownloadService.isJobId("CC8A9A02-D270-431B-8166-4DFC3E418E5E"));
        assertFalse(DownloadService.isJobId(null));
        assertFalse(DownloadService.isJobId(".."));
        assertFalse(DownloadService.isJobId("../etc/passwd"));
        assertFalse(DownloadService.isJobId("cc8a9a02-d270-431b-8166-4dfc3e418e5e.csv"));
    }

    @Test
    void nonUuidIsNeverLookedUp() {
        DownloadStatusService status = mock(DownloadStatusService.class);
        DownloadService svc = new DownloadService(mock(RabbitTemplate.class), status, mock(DevReportService.class));
        assertNull(svc.getFileResource("../secret"));
        assertEquals(DownloadState.EXPIRED, svc.getDownloadStatus(List.of("bxss.me")).get("bxss.me").getState());
        verifyNoInteractions(status);
    }
}
