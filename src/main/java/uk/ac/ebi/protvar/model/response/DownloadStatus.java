package uk.ac.ebi.protvar.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Lifecycle snapshot of a download job. Persisted in Redis under
 * {@code download:status:<id>} from submission until TTL expiry.
 *
 * <p>Field semantics:
 * <ul>
 *   <li>{@code state} is always populated</li>
 *   <li>{@code message} carries the failure reason when state=FAILED, or
 *       optional info on success</li>
 *   <li>{@code size} is set only when state=READY (file size in bytes)</li>
 *   <li>{@code queuedAt} is set at submission, {@code startedAt} when a
 *       worker picks up the job, {@code finishedAt} on terminal state</li>
 * </ul>
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DownloadStatus {
    private DownloadState state;
    private String message;
    private Long size;
    private Instant queuedAt;
    private Instant startedAt;
    private Instant finishedAt;
}
