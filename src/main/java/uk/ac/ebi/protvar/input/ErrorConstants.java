package uk.ac.ebi.protvar.input;

public enum ErrorConstants {

    DBSNP_ID_INVALID("", "Invalid dbSNP ID. Prefix 'rs' prefix should follow one or more digits."),
    DBSNP_ID_NO_MAPPING("", "No mapping found for dbSNP ID."),
    CLINVAR_ID_INVALID("", "Invalid ClinVar ID. Prefix 'RCV' or 'VCV' should follow 9 numerals and an optional version number (e.g. '.1')."),
    CLINVAR_ID_NO_MAPPING("", "No mapping found for ClinVar ID."),
    COSMIC_ID_INVALID("", "Invalid COSMIC ID. Prefix 'COSV', 'COSM' or 'COSN' should follow one or more digits."),
    COSMIC_ID_NO_MAPPING("", "No mapping found for COSMIC ID."),

    GEN_ASSEMBLY_CONVERT_INFO("", "Assembly GRCh37 to GRCh38 conversion (37->38)."),
    GEN_ASSEMBLY_CONVERT_ERR_NOT_FOUND("", "No GRCh38 equivalent found for input coordinate."),

    GEN_ASSEMBLY_CONVERT_ERR_MULTIPLE("", "Multiple GRCh38 equivalents found for input coordinate."),

    // HGVS input parsing errors
    HGVS_UNSUPPORTED_PREFIX_NG("", "HGVS Unsupported NG prefix (gene or genomic region). Please use a supported prefix - NC (genomic), NM (cDNA) or NP (protein)."),
    HGVS_UNSUPPORTED_PREFIX_LRG("", "HGVS Unsupported LRG prefix (Locus Reference Genomic). Please use a supported prefix - NC (genomic), NM (cDNA) or NP (protein)."),
    HGVS_UNSUPPORTED_PREFIX_NR("", "HGVS Unsupported NR prefix (non-protein-coding RNA). Please use a supported prefix - NC (genomic), NM (cDNA) or NP (protein)."),

    HGVS_UNSUPPORTED_PREFIX("", "HGVS Unsupported prefix. Please use a supported prefix - NC (genomic), NM (cDNA) or NP (protein)."),

    HGVS_UNSUPPORTED_SCHEME_N("", "Non-coding DNA scheme (n.) not supported. Please use a supported scheme - g. (genomic), c. (cDNA) or p. (protein)."),
    HGVS_UNSUPPORTED_SCHEME_M("", "Mitochondrial DNA scheme (m.) not supported. Please use a supported scheme - g. (genomic), c. (cDNA) or p. (protein)."),
    HGVS_UNSUPPORTED_SCHEME_R("", "RNA scheme (r.) not supported. Supported scheme - g. (genomic), c. (cDNA) or p. (protein)."),


    HGVS_INVALID_SCHEME("", "Invalid HGVS scheme."),

    HGVS_GENERIC_ERROR("", "Unable to parse HGVS input. Please check if the format is correct."),

    HGVS_G_REF_SEQ_NOT_MAPPED_TO_CHR("", "The RefSeq accession does not map to a chromosome in GRCh37 or 38."),

    HGVS_INVALID_REFSEQ("", "HGVS input does not have a valid RefSeq accession with prefix NC, NM or NP."),

    HGVS_INVALID_VAR_DESC_G("", "Invalid variant description for HGVS genomic input. Check the variant description contains a valid position, reference and alternate allele e.g. g.123A>T."),
    HGVS_INVALID_VAR_DESC_P("", "Invalid variant description for HGVS protein input. Check the variant description contains a valid reference and alternate (1- or 3-letter) amino acid and a position, e.g. p.Arg490Ser or p.R490S."),

    HGVS_INVALID_VAR_DESC_C("", "Invalid variant description for HGVS cDNA input. Check the variant description contains a valid position, reference and alternate allele (optionally, the protein substitution within brackets) e.g. c.1289A>G(p.Asn430Ser)."),

    HGVS_REFSEQ_G_SCHEME_MISMATCH("", "Expecting genomic (g.) scheme for NC accession."),
    HGVS_REFSEQ_C_SCHEME_MISMATCH("", "Expecting cDNA (c.) scheme for NM accession."),
    HGVS_REFSEQ_P_SCHEME_MISMATCH("", "Expecting protein (p.) scheme for NP accession."),


    //////////////////

    HGVS_USE_DIFF_REFSEQ_VERSION("", "ProtVar is using a different version of the RefSeq accession %s."),

    HGVS_C_POS_NOT_MATCHED("", "Derived protein position from coding position doesn't match."),

    // HGVS input processing (retrieval) errors

    HGVS_REFSEQ_MAPPPED_TO_PROTEIN("", "RefSeq ID mapped to Uniprot protein %s."),
    HGVS_REFSEQ_MULTIPLE_PROTEINS("", "RefSeq ID mapped to multiple Uniprot accessions: %s. ProtVar will show mapping for the first accession."),

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
