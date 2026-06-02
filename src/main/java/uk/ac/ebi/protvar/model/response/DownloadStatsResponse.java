package uk.ac.ebi.protvar.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Response payload for {@code GET /download/stats}. Carries per-event counters
 * (queued/ready/failed totals + per-day) and a snapshot of the last on-disk
 * cleanup sweep so the two related observability facts live together.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DownloadStatsResponse(
        Map<String, Long> counters,
        Cleanup cleanup
) {
    public record Cleanup(Instant lastRun, long filesDeleted, long bytesFreed) {}
}
