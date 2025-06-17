package uk.ac.ebi.protvar.variant;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GenomicVariant {
    private String chromosome;
    private Integer position;  // 1-based coordinate
    private String referenceAllele;
    private String alternateAllele;

    // additional annotation fields
    private String geneSymbol;
    private String transcriptId;
    private String consequence;
    private Double caddScore;
    // ... add more as needed
}
