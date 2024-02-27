package uk.ac.ebi.protvar.input;

public enum ErrorConstants {

    DBSNP_ID_INVALID("", "Invalid dbSNP ID. Prefix 'rs' prefix should follow one or more digits."),
    DBSNP_ID_NO_MAPPING("", "No mapping found for dbSNP ID."),
    CLINVAR_ID_INVALID("", "Invalid ClinVar ID. Prefix 'RCV' or 'VCV' should follow 9 numerals and an optional version number (e.g. '.1')."),
    CLINVAR_ID_NO_MAPPING("", "No mapping found for ClinVar ID."),
    COSMIC_ID_INVALID("", "Invalid COSMIC ID. Prefix 'COSV', 'COSM' or 'COSN' should follow one or more digits."),
    COSMIC_ID_NO_MAPPING("", "No mapping found for COSMIC ID."),

    GEN_ASSEMBLY_CONVERT_INFO("", "%d input%s marked for GRCh37 to GRCh38 conversion."),
    GEN_ASSEMBLY_CONVERT_ERR_NOT_FOUND("", "Unable to map GRCh37 to GRCh38 coordinate."),

    // HGVS input parsing errors
    HGVS_UNSUPPORTED_PREFIX_NG("", "NG prefix (gene or genomic region) is not supported. Please use a reference sequence with NC (genomic), NM (cDNA) or NP (protein) prefix."),
    HGVS_UNSUPPORTED_PREFIX_LRG("", "LRG prefix (Locus Reference Genomic) is not supported. Please use a reference sequence with NC (genomic), NM (cDNA) or NP (protein) prefix."),
    HGVS_UNSUPPORTED_PREFIX_NR("", "NR prefix (non-protein-coding RNA) is not supported. Please use a reference sequence with NC (genomic), NM (cDNA) or NP (protein) prefix."),

    HGVS_UNSUPPORTED_PREFIX("", "HGVS reference sequence prefix is not supported. Please use a reference sequence with NC (genomic), NM (cDNA) or NP (protein) prefix."),

    HGVS_UNSUPPORTED_SCHEME_N("", "Non-coding scheme (n.) not supported. Please use a supported scheme - g. (genomic), c. (cDNA) or p. (protein)."),
    HGVS_UNSUPPORTED_SCHEME_M("", "Mitochondrial scheme (m.) not supported. Please use a supported scheme - g. (genomic), c. (cDNA) or p. (protein)."),
    HGVS_UNSUPPORTED_SCHEME_R("", "RNA scheme (r.) not supported. Supported scheme - g. (genomic), c. (cDNA) or p. (protein)."),

    HGVS_INVALID_SCHEME("", "Invalid HGVS scheme."),

    HGVS_GENERIC_ERROR("", "Unable to parse HGVS input. Please check if the format is correct."),

    HGVS_G_REFSEQ_NOT_MAP_TO_CHR("", "The reference sequence does not map to a chromosome in GRCh37 or 38."),

    HGVS_G_REFSEQ_INVALID("", "The reference sequence for HGVS g. input should be a valid NC accession, for e.g. NC_000002.12."),

    HGVS_G_VARDESC_INVALID("", "The variant description for HGVS g. input should contain a valid position, reference and alternate allele, for e.g. g.123A>T."),

    HGVS_C_REFSEQ_INVALID("", "The reference sequence for HGVS c. input should be a valid NM or NP accession, for e.g. NM_017547.4."),

    HGVS_C_VARDESC_INVALID("", "The variant description for HGVS c. input should contain a valid position, reference and alternate allele (optionally, the protein substitution) e.g. c.1289A>G p.(Asn430Ser)."),

    HGVS_P_REFSEQ_INVALID("", "The reference sequence for HGVS p. input should be a valid NP or NM accession, for e.g. NP_001305738.1."),

    HGVS_P_VARDESC_INVALID("", "The variant description for HGVS p. should contain a valid reference and alternate (1- or 3-letter) amino acid and a position, e.g. p.Arg490Ser or p.R490S."),

    //////////////////

    HGVS_USE_DIFF_REFSEQ_VERSION("", "ProtVar is using a different version of the RefSeq accession %s."),

    HGVS_C_POS_NOT_MATCHED("", "Derived protein position from coding position doesn't match."),

    // HGVS input processing (retrieval) errors

    HGVS_REFSEQ_MAPPED_TO_PROTEIN("", "RefSeq ID mapped to Uniprot protein %s."),
    HGVS_REFSEQ_MULTIPLE_PROTEINS("", "RefSeq ID mapped to multiple Uniprot accessions: %s. ProtVar will use accession %t."),

    HGVS_REFSEQ_NO_PROTEIN("", "Could not map RefSeq ID to a Uniprot protein."),

    HGVS_UNIPROT_ACC_NOT_FOUND("", "We mapped %s to %s but the accession cannot be found in UniProt. "),
    PROT_UNIPROT_ACC_NOT_FOUND("", "UniProt accession not found %s. "),

    PROT_NO_GEN_MAPPING("", "Could not map protein input to genomic coordinate(s). "),
    CDNA_NO_GEN_MAPPING("", "Could not map cDNA input to genomic coordinate(s). "),

    INVALID_GNOMAD("", "Invalid gnomAD input."),

    INVALID_CHR("", "Invalid chromosome. Acceptable values are 1-22, X, Y and MT."),
    INVALID_POS("", "Invalid position. Enter a natural number."),
    INVALID_REF("", "Invalid reference allele. Enter one of A, C, T, and G."),
    INVALID_ALT("", "Invalid alternate allele. Enter one of A, C, T, and G."),

    INVALID_PROTEIN_INPUT("", "Invalid protein input. Check the format is correct and includes a valid protein position, reference and alternate amino acid. "),
    INVALID_VCF_INPUT("", "Invalid VCF input. Check the format is correct and includes a chromosome, position, variant ID, reference and alternate allele. "),

    INVALID_GENOMIC_INPUT("", "Invalid Genomic input."),

    INVALID_GENERIC_INPUT("", "Invalid input."),


    ///

    ERR_REF_ALLELE_EMPTY("", "User input reference and variant allele empty. ProtVar will use the reference allele at the genomic location."),
    ERR_VAR_ALLELE_EMPTY("", "User input variant allele empty."),
    ERR_REF_ALLELE_MISMATCH("", "User input reference allele (%s) does not match the UniProt sequence (%s) at the genomic location."),

    ERR_REF_AND_VAR_ALLELE_SAME("", "Reference and alternate are the same. ProtVar will show all possible alternates of the reference."),

    //###
    // reference mismatch
    ERR_CODE_REF_MISMATCH("100", "User input reference amino acid (%s) does not match the UniProt sequence (%s) at position %d. ProtVar will use the reference amino acid (%s)."),
    // reference empty - ACC POS
    ERR_CODE_REF_EMPTY("101", "User input reference amino acid empty. ProtVar will use the reference amino acid at position %d (%s)."),
    // variant non SNV
    ERR_CODE_VARIANT_NON_SNV("102", "User input variant amino acid (%s) cannot be caused by an SNV from the reference amino acid (%s), therefore the returned results are position but not variant specific."),
    // variant empty - ACC POS REF
    ERR_CODE_VAR_EMPTY("103", "User input variant amino acid empty, therefore the returned results are position but not variant specific."),
    // Ref and var empty - ACC POS (not needed, captured with ref & var empty above)
    //ERR_CODE_REF_AND_VAR_EMPTY("104", "User input reference and variant amino acids empty, therefore the returned results are position but not variant specific."

    ;


    private final String errorCode;
    private final String errorMessage;

    ErrorConstants(String errorCode, String errorMessage) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return this.errorMessage;
    }

}
