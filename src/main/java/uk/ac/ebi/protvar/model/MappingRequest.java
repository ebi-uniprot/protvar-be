package uk.ac.ebi.protvar.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import uk.ac.ebi.protvar.constants.PageUtils;
import uk.ac.ebi.protvar.types.AmClass;
import uk.ac.ebi.protvar.types.CaddCategory;
import uk.ac.ebi.protvar.types.InputType;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import uk.ac.ebi.protvar.types.StabilityChange;

import java.util.List;

@Data
@SuperBuilder
@Schema(description = "Base request used for mapping")
@NoArgsConstructor
// TODO decapitalise all params like CADD, ASC, DESC and other enums
public class MappingRequest {

    public final static String PAGE_DESC = """
            Page number (1-based) indicating which page of inputs to include in the response or download.
            Defaults to first page (1) if not specified.
            Must be greater than 0.
            """;

    public final static String PAGE_SIZE_DESC = """
            Number of results per page.
            Must be between 10 and 1000.
            Defaults to the system's default page size if not specified.
            """;

    public final static String ASSEMBLY_DESC = """
            Genome assembly to use for mapping: 'AUTO', 'GRCh37', or 'GRCh38'.
            If set to 'AUTO', the system will attempt to detect the appropriate assembly for genomic inputs.
            Defaults to 'AUTO' if not specified.
            """;

    @Schema(description = "The input. This can be inputId, single variant or one of the other input types (UniProt accession, gene symbol, Ensembl, PDB, or RefSeq ID).")
    @NotBlank(message = "Input must not be null or empty")
    // todo: actually we should allow empty so only filters will apply
    protected String input; // rename to searchTerm? (search=?&type=?)

    @Schema(
            description = "Input type. If null, the system will try to infer it automatically.",
            example = "UNIPROT"
    )
    //@NotNull(message = "Input type must not be null")
    protected InputType type; // searchTermType? expand to FREE_TEXT (to allow similarity?)

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
            description = "Genome assembly version. 'AUTO' lets the system auto-detect the build for genomic inputs.",
            defaultValue = "AUTO",
            allowableValues = {"AUTO", "GRCh37", "GRCh38"}
    )
    protected String assembly;

    // Optional advanced filtering criteria
    @Schema(description = "CADD score filter categories", example = "[]")
    private List<CaddCategory> cadd;

    @Schema(description = "AlphaMissense pathogenicity class filter", example = "[]")
    private List<AmClass> am;

    @Schema(description = "Show only known variants (default: potential included)", example = "false")
    private Boolean known;

    @Schema(description = "Show only variants in predicted pocket", example = "false")
    private Boolean pocket;

    @Schema(description = "Show only variants in P-P interaction", example = "false")
    private Boolean interact;

    @Schema(description = "Stability change filter", example = "[]")
    private List<StabilityChange> stability;

    @Schema(description = "Sort field: 'cadd' or 'am'", example = "cadd")
    private String sort;

    @Schema(description = "Sort direction: 'asc' or 'desc'", example = "asc")
    private String order;

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
