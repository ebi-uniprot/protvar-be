package uk.ac.ebi.protvar.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.model.response.DownloadState;
import uk.ac.ebi.protvar.model.response.DownloadStatus;

import java.time.Duration;
import java.time.Instant;

/**
 * Reads/writes download lifecycle status in Redis. The Redis entry is the
 * source of truth; the on-disk ZIP is the file artifact. Entry TTL is 7 days.
 */
@Service
@RequiredArgsConstructor
public class DownloadStatusService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadStatusService.class);
    private static final String KEY_PREFIX = "download:status:";
    private static final Duration TTL = Duration.ofDays(7);

    private final RedisTemplate<String, Object> redisTemplate;

    private static String key(String id) {
        return KEY_PREFIX + id;
    }

    public DownloadStatus get(String id) {
        try {
            Object raw = redisTemplate.opsForValue().get(key(id));
            return raw instanceof DownloadStatus ds ? ds : null;
        } catch (Exception e) {
            LOGGER.warn("Failed to read status for {}: {}", id, e.getMessage());
            return null;
        }
    }

    public void put(String id, DownloadStatus status) {
        try {
            redisTemplate.opsForValue().set(key(id), status, TTL);
        } catch (Exception e) {
            LOGGER.warn("Failed to write status for {}: {}", id, e.getMessage());
        }
    }

    public void markQueued(String id) {
        put(id, DownloadStatus.builder()
                .state(DownloadState.QUEUED)
                .queuedAt(Instant.now())
                .build());
    }

    public void markProcessing(String id) {
        DownloadStatus current = get(id);
        DownloadStatus.DownloadStatusBuilder builder = current != null
                ? current.toBuilder()
                : DownloadStatus.builder();
        put(id, builder
                .state(DownloadState.PROCESSING)
                .startedAt(Instant.now())
                .build());
    }

    public void markReady(String id, long bytes) {
        DownloadStatus current = get(id);
        DownloadStatus.DownloadStatusBuilder builder = current != null
                ? current.toBuilder()
                : DownloadStatus.builder();
        put(id, builder
                .state(DownloadState.READY)
                .bytes(bytes)
                .finishedAt(Instant.now())
                .build());
    }

    public void markFailed(String id, String message) {
        DownloadStatus current = get(id);
        DownloadStatus.DownloadStatusBuilder builder = current != null
                ? current.toBuilder()
                : DownloadStatus.builder();
        put(id, builder
                .state(DownloadState.FAILED)
                .message(message)
                .finishedAt(Instant.now())
                .build());
    }
}
