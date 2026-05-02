package uk.ac.ebi.protvar.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.config.RetentionProperties;
import uk.ac.ebi.protvar.model.response.DownloadState;
import uk.ac.ebi.protvar.model.response.DownloadStatus;

import java.time.Instant;

/**
 * Reads/writes download lifecycle status in Redis. The Redis entry is the
 * source of truth; the on-disk ZIP is the file artifact. Entry TTL comes from
 * {@link RetentionProperties#getDownloads()} (default 30 days) and is shared
 * with the on-disk file cleanup so the two stay in lockstep.
 *
 * <p>Failure messages stored on the {@link DownloadStatus#getMessage()} field
 * are user-facing — keep them short and free of technical detail. Stack traces
 * and exception messages still go to logs and the dev notification email.
 *
 * <p>Counters under the {@code download:counts:*} key space are incremented at
 * lifecycle transitions for monitoring (BE writes, prod DB is read-only). Both
 * a running total and a per-day count are tracked. Read via
 * {@link #getCounters()}.
 */
@Service
@RequiredArgsConstructor
public class DownloadStatusService {

    public static final String MSG_QUEUE_FAILED =
            "Could not submit your download. Please try again.";
    public static final String MSG_PROCESSING_FAILED =
            "Download failed. Please try again, or contact protvar@ebi.ac.uk if the issue persists.";
    public static final String MSG_TOO_LARGE =
            "Your download is too large. Please refine your filters.";

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadStatusService.class);
    private static final String KEY_PREFIX = "download:status:";
    private static final String COUNTER_PREFIX = "download:counts:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final RetentionProperties retention;

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
            redisTemplate.opsForValue().set(key(id), status, retention.getDownloads());
        } catch (Exception e) {
            LOGGER.warn("Failed to write status for {}: {}", id, e.getMessage());
        }
    }

    public void markQueued(String id) {
        put(id, DownloadStatus.builder()
                .state(DownloadState.QUEUED)
                .queuedAt(Instant.now())
                .build());
        increment("queued");
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

    public void markReady(String id, long size) {
        DownloadStatus current = get(id);
        DownloadStatus.DownloadStatusBuilder builder = current != null
                ? current.toBuilder()
                : DownloadStatus.builder();
        put(id, builder
                .state(DownloadState.READY)
                .size(size)
                .finishedAt(Instant.now())
                .build());
        increment("ready");
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
        increment("failed");
    }

    /** Bumps both the running total and a per-day counter. */
    public void increment(String name) {
        try {
            String today = java.time.LocalDate.now().toString();
            redisTemplate.opsForValue().increment(COUNTER_PREFIX + name + ":total");
            redisTemplate.opsForValue().increment(COUNTER_PREFIX + name + ":by-day:" + today);
        } catch (Exception e) {
            LOGGER.warn("Failed to increment counter '{}': {}", name, e.getMessage());
        }
    }

    /**
     * Snapshot of all download counters (totals and recent per-day).
     * Returns a map keyed by the bare counter name (after the
     * {@code download:counts:} prefix).
     */
    public java.util.Map<String, Long> getCounters() {
        java.util.Map<String, Long> out = new java.util.LinkedHashMap<>();
        try {
            java.util.Set<String> keys = redisTemplate.keys(COUNTER_PREFIX + "*");
            if (keys == null) return out;
            for (String key : new java.util.TreeSet<>(keys)) {
                Object v = redisTemplate.opsForValue().get(key);
                long n = v instanceof Number ? ((Number) v).longValue()
                        : v != null ? Long.parseLong(v.toString()) : 0L;
                out.put(key.substring(COUNTER_PREFIX.length()), n);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to read counters: {}", e.getMessage());
        }
        return out;
    }
}
