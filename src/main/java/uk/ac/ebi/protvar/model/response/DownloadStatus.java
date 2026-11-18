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
 *   <li>{@code attempts} counts how many times the processor picked up this
 *       job. Bumped each time {@link DownloadState#PROCESSING} is entered;
 *       used to cap redeliveries after a JVM crash mid-job</li>
 *   <li>{@code queuedAt} is set at submission, {@code startedAt} when a
 *       worker picks up the job, {@code finishedAt} on terminal state</li>
 *   <li>{@code progress} (0–100) is set for full downloads only: the share of
 *       inputs written, held at 99 while the file is merged and zipped,
 *       100 when ready</li>
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
    private Integer attempts;
    private Instant queuedAt;
    private Instant startedAt;
    private Instant finishedAt;
    private Integer progress;
}
