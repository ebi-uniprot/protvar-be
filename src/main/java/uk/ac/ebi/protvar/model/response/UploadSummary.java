package uk.ac.ebi.protvar.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * What happened to an upload's lines. Known from the start: lines, and lines per input format (e.g.
 * protein → 83859). While the upload is being prepared: its progress (0–99). Once prepared: lines mapped and
 * not mapped, lines with any note, and lines per note code (a line counts once per code).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UploadSummary(long lines, Map<String, Integer> formats, Integer preparing,
                            Long mapped, Long notMapped, Long withNotes, Map<String, Long> codes) {

    public static UploadSummary of(long lines, Map<String, Integer> formats) {
        return new UploadSummary(lines, formats, null, null, null, null, null);
    }

    public static UploadSummary preparing(int percent) {
        return new UploadSummary(0, null, percent, null, null, null, null);
    }

    /** The prepared counts. */
    public static UploadSummary prepared(long lines, long mapped, long withNotes, Map<String, Long> codes) {
        return new UploadSummary(lines, null, null, mapped, lines - mapped, withNotes, codes);
    }

    /** These lines and formats, with the build's progress or counts. */
    public UploadSummary with(UploadSummary build) {
        return new UploadSummary(build.mapped != null ? build.lines : lines, formats, build.preparing,
                build.mapped, build.notMapped, build.withNotes, build.codes);
    }
}
