package uk.ac.ebi.protvar.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import uk.ac.ebi.protvar.config.RetentionProperties;
import uk.ac.ebi.protvar.model.response.DownloadState;
import uk.ac.ebi.protvar.model.response.DownloadStatus;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DownloadStatusProgressTest {

    private final AtomicReference<Object> stored = new AtomicReference<>();
    private final DownloadStatusService svc;

    @SuppressWarnings("unchecked")
    DownloadStatusProgressTest() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        ValueOperations<String, Object> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenAnswer(i -> stored.get());
        doAnswer(i -> { stored.set(i.getArgument(1)); return null; })
                .when(ops).set(anyString(), any(), any(Duration.class));
        svc = new DownloadStatusService(redis, new RetentionProperties());
        ReflectionTestUtils.setField(svc, "cacheVersion", "v1");
    }

    private DownloadStatus status() {
        return (DownloadStatus) stored.get();
    }

    @Test
    void progressThenReady() {
        svc.markQueued("j");
        svc.markProgress("j", 10);             // not processing yet: ignored
        assertNull(status().getProgress());
        svc.markProcessing("j", 1);
        svc.markProgress("j", 40);
        assertEquals(40, status().getProgress());
        svc.markReady("j", 123);
        assertEquals(DownloadState.READY, status().getState());
        assertEquals(100, status().getProgress());
    }

    @Test
    void pageDownloadReadyHasNoProgress() {
        svc.markQueued("p");
        svc.markProcessing("p", 1);
        svc.markReady("p", 1);
        assertNull(status().getProgress());
    }

    @Test
    void redeliveryStartsAgain() {
        svc.markQueued("r");
        svc.markProcessing("r", 1);
        svc.markProgress("r", 70);
        svc.markProcessing("r", 2);
        assertNull(status().getProgress());
    }
}
