package uk.ac.ebi.protvar.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import uk.ac.ebi.protvar.constants.PageUtils;
import uk.ac.ebi.protvar.types.AmClass;
import uk.ac.ebi.protvar.types.CaddCategory;
import uk.ac.ebi.protvar.types.InputType;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Data
@SuperBuilder
@Schema(description = "Base request used for mapping")
// TODO decapitalise all params like CADD, ASC, DESC and other enums

// todo rename to/align with MappingRequest
public class Request {
    @Schema(description = "The input. This can be inputId, single variant or one of the other input types (UniProt accession, gene symbol, Ensembl, PDB, or RefSeq ID).")
    @NotBlank(message = "Input must not be null or empty") // todo: actually we should allow empty so only filters will apply
    protected String input; // rename to searchTerm? (search=?&type=?)

    @Schema(
            description = "Input type. If null, the input is treated as a single variant.", // todo: verify this!! need to add a SINGLE_VARIANT type
            example = "UNIPROT"
    )
    @NotNull(message = "Input type must not be null")
    protected InputType type; // searchTermType? expand to FREE_TEXT (to allow similarity?)

    @Schema(
            description = """
                Page number (1-based) indicating which page of inputs to include in the response or download.

                Defaults to first page (1) if not specified.
                Must be greater than 0.
                """,
            example = "1"
    )
    @Min(value = 1, message = "Page number must be greater than 0")
    protected Integer page;

    @Schema(
            description = """
                    Number of results per page.

                    Must be between 10 and 1000.
                    Defaults to the system's default page size if not specified.
                    """,
            example = "25"
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
