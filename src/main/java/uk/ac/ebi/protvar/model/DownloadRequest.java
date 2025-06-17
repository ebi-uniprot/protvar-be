package uk.ac.ebi.protvar.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import uk.ac.ebi.protvar.constants.PagedMapping;
import uk.ac.ebi.protvar.types.IdentifierType;
import uk.ac.ebi.protvar.utils.ChecksumUtils;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@Schema(description = "Request payload for initiating a download")
public class DownloadRequest {
    @Schema(description = "Identifier or single variant")
    @NotBlank(message = "Input must not be null or empty")
    private String input;

    @Schema(
            description = """
                    Type of identifier.
                    If null, the input is treated as a single variant.
                    """,
            example = "UNIPROT",
            allowableValues = {"ENSEMBL", "UNIPROT", "PDB", "REFSEQ", "CUSTOM_INPUT", "GENE"}
    )
    private IdentifierType type;

    @Schema(
            description = """
                Page number indicating which subset of inputs to include in the download.
                If null, the entire set of inputs will be included (full download).
                If specified, only the inputs for that page will be included.
            """,
            example = "1"
    )
    private Integer page;

    @Schema(
            description = """
            Number of results per page.
            Only relevant if `page` is specified. 
            If null, the default page size will be used.
            """,
            example = "1000"
    )
    private Integer pageSize;

    @Schema(description = "Include functional annotations", defaultValue = "false")
    private boolean function;

    @Schema(description = "Include population annotations", defaultValue = "false")
    private boolean population;

    @Schema(description = "Include structural annotations", defaultValue = "false")
    private boolean structure;

    @Schema(
            description = "Genome assembly version. 'AUTO' will let the system auto-detect the build for genomic inputs.",
            defaultValue = "AUTO",
            allowableValues = {"AUTO", "GRCh37", "GRCh38"}
    )
    private String assembly;

    @Schema(description = "Optional email address for status updates", example = "user@example.com")
    @Email
    private String email;

    @Schema(description = "Optional job name for tracking the download", example = "protvar-run-001")
    private String jobName;

    @Schema(description = "Optional advanced filtering criteria")
    private AdvancedFilter advancedFilter;

    // Additional derived fields (not part of request payload)
    private String fname;
    private String url;
    private LocalDateTime timestamp;

    public Integer getPageSize() {
        return pageSize == null ? PagedMapping.DEFAULT_PAGE_SIZE : pageSize;
    }

    /**
     * filename format:
     * <pref>[-fun][-pop][-str][-PAGE][-PAGE_SIZE][-ASSEMBLY][-advancedFilterHash]
     *
     * pref is the checksum of the input if type is null (single variant),
     * or one of custom user input id, protein accession, etc (identifier type)
     * specified in input.
     */
    public void buildAndSetFilename() {
        String pref = type == null // single variant
                ? ChecksumUtils.checksum(input.getBytes(StandardCharsets.UTF_8))
                : sanitizeForFilename(input);

        StringBuilder filename = new StringBuilder(pref);
        if (function) filename.append("-fun");
        if (population) filename.append("-pop");
        if (structure) filename.append("-str");

        if (page != null) {
            filename.append("-").append(page);
            filename.append("-").append(getPageSize());
        }

        if (assembly != null && !assembly.equals("AUTO")) {
            filename.append("-").append(assembly);
        }

        if (advancedFilter != null) {
            String advancedFilterString = advancedFilter.toString();
            String hash = ChecksumUtils.checksum(advancedFilterString.getBytes(StandardCharsets.UTF_8));
            String shortHash = hash != null ? hash.substring(0, 6) : "";
            filename.append("-").append(shortHash);
        }

        fname = filename.toString();
    }

    public static String sanitizeForFilename(String input) {
        if (input == null || input.isEmpty()) {
            return "unknown";
        }
        // Replace characters that are problematic in filenames
        return input.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
