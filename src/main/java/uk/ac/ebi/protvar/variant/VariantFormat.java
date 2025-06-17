package uk.ac.ebi.protvar.variant;

public enum VariantFormat {
    // HGVS formats
    HGVS_GENOMIC,     // g. notation
    HGVS_CODING,      // c. notation
    HGVS_PROTEIN,     // p. notation

    // Custom internal formats
    CUSTOM_GENOMIC,   // e.g., chr-pos[-ref[-alt]]
    CUSTOM_PROTEIN,   // e.g., acc-pos-refAA-altAA

    // External formats
    VCF,              //  e.g., chr pos ref alt ... (tab-separated VCF format)
    GNOMAD,           // gnomAD-style strings or IDs (e.g., 1-55516888-G-T)

    // ID-based formats
    DBSNP,            // rs12345
    CLINVAR,          // RCV/CV IDs
    COSMIC,            // COSV...
    INVALID;

    //VariantFormat format = VariantFormat.GNOMAD;
    //VariantType type = format.getType();  // GENOMIC

    public VariantType getType() {
        return switch (this) {
            case HGVS_GENOMIC, CUSTOM_GENOMIC, VCF, GNOMAD -> VariantType.GENOMIC;
            case HGVS_CODING -> VariantType.CODING_DNA;
            case HGVS_PROTEIN, CUSTOM_PROTEIN -> VariantType.PROTEIN;
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

    // TODO complete!

    public static VariantFormat detectFormat(String input) {
        if (input.matches("rs\\d+")) return VariantFormat.DBSNP;
        if (input.startsWith("NM_") && input.contains(":c.")) return VariantFormat.HGVS_CODING;
        if (input.matches("chr\\d+-\\d+(-[ACGT]+)?(-[ACGT]+)?")) return VariantFormat.CUSTOM_GENOMIC;
        //...
        return VariantFormat.INVALID; // fallback
    }
}