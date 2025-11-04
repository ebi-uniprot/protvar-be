package uk.ac.ebi.protvar.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import uk.ac.ebi.protvar.constants.PageUtils;
import uk.ac.ebi.protvar.types.*;

import jakarta.validation.constraints.Min;

import java.util.List;

@Data
@SuperBuilder
@Schema(description = "Base request used for mapping")
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // Exclude null fields from JSON
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

    @Schema(
            description = """
            Search terms to query variants. Can be:
            - A single variant in any supported format
            - An input ID (32-character identifier for a previously submitted variant list)
            - One or more biological identifiers (UniProt accession, gene symbol, Ensembl ID, PDB ID, or RefSeq ID)
            - Omitted entirely for filter-only queries across all variants
            """,
            nullable = true
    )
    private List<SearchTerm> searchTerms;

    @Schema(description = PAGE_DESC, example = PageUtils.PAGE)
    @Min(value = 1, message = "Page number must be greater than 0")
    protected Integer page;

    @Schema(description = PAGE_SIZE_DESC, example = PageUtils.PAGE_SIZE)
    @Min(value = PageUtils.PAGE_SIZE_MIN, message = "Page size must be at least 10")
    @Max(value = PageUtils.PAGE_SIZE_MAX, message = "Page size must not exceed 1000")
    protected Integer pageSize;

    @Schema(
            description = "Genome assembly version. 'AUTO' lets the system auto-detect the build for genomic inputs.",
            example = "AUTO",
            allowableValues = {"AUTO", "GRCh37", "GRCh38"}
    )
    protected String assembly;

    // Advanced filtering criteria
    // Variant Type
    @Schema(
            description = "Show only known variants. Defaults to true if not specified.",
            defaultValue = "true",
            nullable = true
    )
    private Boolean known;

    // Functional (not yet implemented - placeholders for future)
    @Schema(description = "Filter by PTM sites", nullable = true)
    private Boolean ptm;
    @Schema(description = "Filter by mutagenesis sites", nullable = true)
    private Boolean mutagenesis;

    @Schema(description = "Minimum conservation score (0-1, inclusive)", nullable = true)
    @Min(value = 0, message = "Conservation minimum must be at least 0")
    @Max(value = 1, message = "Conservation minimum must not exceed 1")
    private Double conservationMin;

    @Schema(description = "Maximum conservation score (0-1, inclusive)", nullable = true)
    @Min(value = 0, message = "Conservation maximum must be at least 0")
    @Max(value = 1, message = "Conservation maximum must not exceed 1")
    private Double conservationMax;

    @Schema(description = "Filter by functional domain", nullable = true)
    private Boolean functionalDomain;

    // Population
    @Schema(description = "Filter by disease association", nullable = true)
    private Boolean diseaseAssociation; // Not yet implemented

    @Schema(description = "Allele frequency categories", nullable = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<AlleleFreqCategory> alleleFreq;

    // Structural
    @Schema(description = "Show only variants with experimental structural model", nullable = true)
    private Boolean experimentalModel;

    @Schema(description = "Show only variants in P-P interaction", nullable = true)
    private Boolean interact;

    @Schema(description = "Show only variants in predicted pocket", nullable = true)
    private Boolean pocket;

    @Schema(description = "Stability change filter", nullable = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<StabilityChange> stability;

    // Consequence
    @Schema(description = "CADD score filter categories", nullable = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<CaddCategory> cadd;

    @Schema(description = "AlphaMissense pathogenicity class filter", nullable = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<AmClass> am;

    @Schema(description = "popEVE score filter categories", nullable = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<PopEveClass> popeve;

    @Schema(description = "Minimum ESM1b score (-25 to 0, inclusive)", nullable = true)
    @Min(value = -25, message = "ESM1b minimum must be at least -25")
    @Max(value = 0, message = "ESM1b minimum must not exceed 0")
    private Double esm1bMin;

    @Schema(description = "Maximum ESM1b score (-25 to 0, inclusive)", nullable = true)
    @Min(value = -25, message = "ESM1b maximum must be at least -25")
    @Max(value = 0, message = "ESM1b maximum must not exceed 0")
    private Double esm1bMax;

    // Sorting
    @Schema(description = "Sort field: 'cadd', 'am', 'popeve', or 'esm1b'", nullable = true)
    private String sort;

    @Schema(description = "Sort direction: 'asc' or 'desc'", nullable = true)
    private String order;

    // Override Lombok getters for page and pageSize

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

    // ============================================
    // CONVENIENCE METHODS
    // ============================================

    /**
     * @return true if no search terms specified (filter-only or advanced search with filters only)
     */
    @Schema(hidden = true)
    @JsonIgnore
    public boolean hasNoSearchTerms() {
        return searchTerms == null || searchTerms.isEmpty();
    }

    /**
     * @return true if this is a single variant query
     */
    @Schema(hidden = true)
    @JsonIgnore
    public boolean isSingleVariant() {
        return searchTerms != null &&
                searchTerms.size() == 1 &&
                searchTerms.get(0).getType() == SearchType.VARIANT;
    }

    /**
     * @return true if this is an input ID query
     */
    @Schema(hidden = true)
    @JsonIgnore
    public boolean isInputIdQuery() {
        return searchTerms != null &&
                searchTerms.size() == 1 &&
                searchTerms.get(0).getType() == SearchType.INPUT_ID;
    }

    /**
     * @return true if this is an advanced search query (identifiers and/or filters)
     */
    @Schema(hidden = true)
    @JsonIgnore
    public boolean isAdvancedSearch() {
        return hasNoSearchTerms() ||
                (searchTerms != null && searchTerms.stream()
                        .allMatch(term -> term.getType().isIdentifier()));
    }

    /**
     * @return list of identifier search terms only
     */
    @Schema(hidden = true)
    @JsonIgnore
    public List<SearchTerm> getIdentifierTerms() {
        if (searchTerms == null) {
            return List.of();
        }
        return searchTerms.stream()
                .filter(term -> term.getType().isIdentifier())
                .toList();
    }

    /**
     * Returns the effective 'known' filter value.
     * Defaults to true if not explicitly specified.
     */
    @Schema(hidden = true)
    @JsonIgnore
    public boolean isKnownFilter() {
        return known == null || known; // null treated as true
    }

    /**
     * Returns true if the 'known' filter was explicitly set by the user.
     * Used for context messages and filename generation.
     */
    @Schema(hidden = true)
    @JsonIgnore
    public boolean isKnownExplicitlySet() {
        return known != null;
    }

    /**
     * @return true if any filter is specified
     */
    @Schema(hidden = true)
    @JsonIgnore
    public boolean hasAnyFilter() {
        return known != null ||
                ptm != null ||
                mutagenesis != null ||
                conservationMin != null ||
                conservationMax != null ||
                functionalDomain != null ||
                diseaseAssociation != null ||
                (alleleFreq != null && !alleleFreq.isEmpty()) ||
                experimentalModel != null ||
                interact != null ||
                pocket != null ||
                (stability != null && !stability.isEmpty()) ||
                (cadd != null && !cadd.isEmpty()) ||
                (am != null && !am.isEmpty()) ||
                (popeve != null && !popeve.isEmpty()) ||
                esm1bMin != null ||
                esm1bMax != null;
    }
}
