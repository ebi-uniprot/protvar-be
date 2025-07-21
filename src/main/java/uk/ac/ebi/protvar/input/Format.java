package uk.ac.ebi.protvar.input;

public enum Format { // todo: rename to VariantFormat
    // Genomic formats                // Parsed as
    VCF(Type.GENOMIC),                // - GenomicInput
    GNOMAD(Type.GENOMIC),             // - GenomicInput
    INTERNAL_GENOMIC(Type.GENOMIC),   // - GenomicInput
    HGVS_GEN(Type.GENOMIC),           // - GenomicInput

    // Coding formats
    HGVS_CODING(Type.CODING),         // - HGVSCodingInput

    // Protein formats
    HGVS_PROT(Type.PROTEIN),          // - ProteinInput
    INTERNAL_PROTEIN(Type.PROTEIN),   // - ProteinInput

    // ID formats
    DBSNP(Type.ID),                   // - UserInput
    CLINVAR(Type.ID),                 // - UserInput
    COSMIC(Type.ID);                  // - UserInput

    Type type;
    Format(Type type) {
        this.type = type;
    }

}
