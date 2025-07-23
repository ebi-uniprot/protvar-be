package uk.ac.ebi.protvar.input;

public enum VariantFormat {
    // HGVS formats
    HGVS_GENOMIC,     // g. notation
    HGVS_CODING,      // c. notation
    HGVS_PROTEIN,     // p. notation

    // Internal formats
    INTERNAL_GENOMIC, // e.g., chr pos [ref] [alt]
    INTERNAL_PROTEIN, // e.g., acc pos [refAA] [altAA]

    // External formats
    VCF,              // e.g., chr pos id ref alt ... (tab-separated VCF format)
    GNOMAD,           // gnomAD-style strings or IDs (e.g., 1-55516888-G-T)

    // ID-based formats
    DBSNP,            // rs12345
    CLINVAR,          // RCV/CV IDs
    COSMIC,           // COSV...

    INVALID;

    public VariantType getType() {
        return switch (this) {
            case HGVS_GENOMIC, INTERNAL_GENOMIC, VCF, GNOMAD -> VariantType.GENOMIC;
            case HGVS_CODING -> VariantType.CODING_DNA;
            case HGVS_PROTEIN, INTERNAL_PROTEIN -> VariantType.PROTEIN;
            case DBSNP, CLINVAR, COSMIC -> VariantType.VARIANT_ID;
            case INVALID -> VariantType.INVALID;
        };
    }

    public static VariantFormat fromString(String value) {
        if (value == null || value.isBlank()) {
            return INVALID;
        }
        try {
            return VariantFormat.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return INVALID;
        }
    }

}
