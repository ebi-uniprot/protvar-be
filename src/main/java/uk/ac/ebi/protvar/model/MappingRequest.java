package uk.ac.ebi.protvar.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import uk.ac.ebi.protvar.constants.PageUtils;
import uk.ac.ebi.protvar.types.AlleleFreqCategory;
import uk.ac.ebi.protvar.types.AmClass;
import uk.ac.ebi.protvar.types.CaddCategory;
import uk.ac.ebi.protvar.types.PopEveClass;
import uk.ac.ebi.protvar.types.StabilityChange;
import jakarta.validation.constraints.Min;

import java.util.List;

@Data
@SuperBuilder
@Schema(description = "Base request used for mapping")
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MappingRequest {

    public final static String PAGE_DESC = """
            Page number, from 1 (default 1).
            """;

    public final static String PAGE_SIZE_DESC = """
            Results per page, 10–1000.
            """;

    public final static String ASSEMBLY_DESC = """
            Assembly of genomic inputs: AUTO (default, detected), GRCh37 or GRCh38.
            """;

    @Schema(description = "A single variant in any supported format (genomic, protein, HGVS, dbSNP…).")
    protected String q;

    @Schema(description = "inputId from /input. Takes precedence over ids.")
    protected String resultId;

    @Schema(description = "Identifiers to browse. type is optional (detected from value) but recommended.",
            example = "[{\"type\": \"uniprot\", \"value\": \"P22304\"}, {\"value\": \"BRCA2\"}]")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    protected List<Identifier> ids;

    @Schema(
            description = PAGE_DESC,
            example = PageUtils.PAGE
    )
    @Min(value = 1, message = "Page number must be greater than 0")
    protected Integer page;

    @Schema(
            description = PAGE_SIZE_DESC,
            example = PageUtils.PAGE_SIZE
    )
    @Min(value = PageUtils.PAGE_SIZE_MIN, message = "Page size must be at least 10")
    @Max(value = PageUtils.PAGE_SIZE_MAX, message = "Page size must not exceed 1000")
    protected Integer pageSize;

    @Schema(
            description = ASSEMBLY_DESC,
            defaultValue = "AUTO",
            allowableValues = {"AUTO", "GRCh37", "GRCh38"}
    )
    protected String assembly;

    // Advanced filtering criteria
    // Variant Type
    @Schema(description = "Only known variants (default includes all possible)", example = "false")
    private Boolean known;

    // Function-side feature filters (mapped via FeatureGroup → function_feature.type)
    @Schema(description = "Only variants at a PTM site", example = "false")
    private Boolean ptm;

    @Schema(description = "Only variants at a mutagenesis site", example = "false")
    private Boolean mutagen;

    @Schema(description = "Only variants in a domain, region or site", example = "false")
    private Boolean domain;

    @Schema(description = "Only variants at a binding site", example = "false")
    private Boolean binding;

    @Schema(description = "Only variants at an active site", example = "false")
    private Boolean actsite;

    @Schema(description = "Only variants in a transmembrane region", example = "false")
    private Boolean transmem;

    @Schema(description = "Minimum conservation (0–1)", example = "0.0")
    @Min(value = 0, message = "Conservation minimum must be at least 0")
    @Max(value = 1, message = "Conservation minimum must not exceed 1")
    private Double conservationMin;

    @Schema(description = "Maximum conservation (0–1)", example = "1.0")
    @Min(value = 0, message = "Conservation maximum must be at least 0")
    @Max(value = 1, message = "Conservation maximum must not exceed 1")
    private Double conservationMax;

    // Population
    @Schema(description = "Only residues with a disease-associated variant", example = "false")
    private Boolean diseaseAssociation;

    @Schema(description = "Allele frequency categories", example = "[]")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<AlleleFreqCategory> alleleFreq;

    // Structural
    @Schema(description = "Only variants with an experimental structure", example = "false")
    private Boolean experimentalModel;

    @Schema(description = "Only variants in a predicted protein–protein interface", example = "false")
    private Boolean interact;

    @Schema(description = "Only variants in a predicted pocket", example = "false")
    private Boolean pocket;

    @Schema(description = "FoldX stability change categories", example = "[]")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<StabilityChange> stability;

    // Consequence
    @Schema(description = "CADD categories", example = "[]")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<CaddCategory> cadd;

    @Schema(description = "Minimum AVI (AlphaGenome Variant Impact) PHRED score", example = "20")
    private Double aviMin;

    @Schema(description = "AlphaMissense classes", example = "[]")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<AmClass> am;

    @Schema(description = "popEVE categories", example = "[]")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<PopEveClass> popeve;

    @Schema(description = "Minimum ESM1b (-25 to 0)", example = "-25.0")
    @Min(value = -25, message = "ESM1b minimum must be at least -25")
    @Max(value = 0, message = "ESM1b minimum must not exceed 0")
    private Double esm1bMin;

    @Schema(description = "Maximum ESM1b (-25 to 0)", example = "0.0")
    @Min(value = -25, message = "ESM1b maximum must be at least -25")
    @Max(value = 0, message = "ESM1b maximum must not exceed 0")
    private Double esm1bMax;

    // Position range filter (single UniProt accession browse only; ignored otherwise with a warning)
    @Schema(description = "Protein position range start (single UniProt accession browse only)", example = "558")
    @Min(value = 1, message = "startPos must be at least 1")
    private Integer startPos;

    @Schema(description = "Protein position range end (with startPos)", example = "600")
    @Min(value = 1, message = "endPos must be at least 1")
    private Integer endPos;

    // Sorting
    @Schema(description = "Sort by: cadd, avi, am, popeve or esm1b", example = "cadd")
    private String sort;

    @Schema(description = "asc or desc", example = "asc")
    private String order;

    // Notes on uploaded lines (uploaded result only)
    @Schema(description = "Uploaded result only: lines with notes (any) or without (none)", example = "any")
    private String notes;

    @Schema(description = "Uploaded result only: lines with any of these note codes", example = "[\"PV-110\"]")
    private List<String> codes;

    // override Lombok getter for page

    /**
     * Returns the effective page number, defaulting to 1 if null.
     */
    public int getPage() {
        return page != null ? page : PageUtils.DEFAULT_PAGE;
    }

    // override Lombok getter for pageSize

    /**
     * Returns the effective page size, defaulting to configured default if null.
     */
    public int getPageSize() {
        return pageSize != null ? pageSize : PageUtils.DEFAULT_PAGE_SIZE;
    }

}
