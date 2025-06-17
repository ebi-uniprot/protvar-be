package uk.ac.ebi.protvar.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.ToString;
import uk.ac.ebi.protvar.types.AmClass;
import uk.ac.ebi.protvar.types.CaddCategory;

import java.util.List;

@Data
@ToString
public class AdvancedFilter {
    @Schema(description = "CADD score filter categories", example = "[\"LIKELY_BENIGN\", \"HIGHLY_LIKELY_DELETERIOUS\"]")
    private List<CaddCategory> caddCategories;
    @Schema(description = "AlphaMissense pathogenicity class filter", example = "[\"AMBIGUOUS\", \"BENIGN\", \"PATHOGENIC\"]")
    private List<AmClass> amClasses;
    @Schema(description = "Show only known variants (default: potential included)", example = "false")
    private boolean known;
    @Schema(description = "Sort field: 'CADD' or 'AM'", example = "CADD")
    private String sort;
    @Schema(description = "Sort direction: 'ASC' or 'DESC'", example = "ASC")
    private String order;
}
