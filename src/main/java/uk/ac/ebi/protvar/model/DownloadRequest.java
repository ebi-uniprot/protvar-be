package uk.ac.ebi.protvar.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
// callSuper so the inherited input identity (resultId / q / ids) and filters
// from MappingRequest appear in toString — without it the request logged in
// error emails (Email.notifyDevErr) showed only fname/url/jobName, making it
// impossible to trace a failed download back to its input.
@ToString(callSuper = true)
@Schema(description = "Request payload for initiating a download")
public class DownloadRequest extends MappingRequest {

    @Schema(description = "Include functional annotations", defaultValue = "false")
    protected Boolean function;

    @Schema(description = "Include population annotations", defaultValue = "false")
    protected Boolean population;

    @Schema(description = "Include structural annotations", defaultValue = "false")
    protected Boolean structure;

    @Schema(description = "Optional email address for status updates", example = "user@example.com")
    //@Email(message = "Must be a valid email address") // default jakarta validation is lenient, allows abc@xyz (without domain)
    @Pattern(
            regexp = "^\\s*$|^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$",
            message = "Must be a valid email address"
    )
    private String email;

    @Schema(description = "Optional job name for tracking the download", example = "protvar-run-001")
    private String jobName;

    @Schema(
            description = """
            If true, download all inputs ignoring pagination.
            If false or omitted, pagination will be applied using page and pageSize,
            which default to 1 and default page size respectively.
            """,
            defaultValue = "false"
    )
    @lombok.Builder.Default
    private Boolean full = false;

    // Additional derived fields (not part of request payload)
    // hidden from Swagger and prevent clients from sending them
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private String fname;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private String url;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime timestamp;

    /**
     * Short human-readable reference to the input this download is for, for
     * log lines keyed by the (opaque) fname. Surfaces whichever input identity
     * was supplied — uploaded resultId, a single query, or browse ids.
     */
    @Schema(hidden = true)
    public String inputRef() {
        if (getResultId() != null && !getResultId().isBlank()) return "resultId=" + getResultId();
        if (getQ() != null && !getQ().isBlank()) return "q=" + getQ();
        if (getIds() != null && !getIds().isEmpty()) return "ids=" + getIds();
        return "no-input-ref";
    }

}