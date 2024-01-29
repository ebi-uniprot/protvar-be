package uk.ac.ebi.protvar.input;

import java.util.Map;

public class ErrorConstants {

    public static final int ERR_CODE_REF_MISMATCH = 100;
    public static final int ERR_CODE_REF_EMPTY = 101;
    public static final int ERR_CODE_VARIANT_NON_SNV = 102;
    public static final int ERR_CODE_VAR_EMPTY = 103;
    public static final int ERR_CODE_REF_AND_VAR_EMPTY = 104;

    public static final String ERR_REF_ALLELE_EMPTY = "User input reference and variant allele empty. ProtVar will use the reference allele at the genomic location.";
    public static final String ERR_VAR_ALLELE_EMPTY = "User input variant allele empty.";
    public static final String ERR_REF_ALLELE_MISMATCH = "User input reference allele (%s) does not match the UniProt sequence (%s) at the genomic location.";

    public static final String ERR_REF_AND_VAR_ALLELE_SAME = "Reference and alternate are the same. ProtVar will show all possible alternates of the reference.";
    public static final Map<Integer, String> ERROR_MESSAGE = Map.of(
            // reference mismatch
            ERR_CODE_REF_MISMATCH, "User input reference amino acid (%s) does not match the UniProt sequence (%s) at position %d. ProtVar will use the reference amino acid (%s).",
            // reference empty - ACC POS
            ERR_CODE_REF_EMPTY, "User input reference amino acid empty. ProtVar will use the reference amino acid at position %d (%s).",
            // variant non SNV
            ERR_CODE_VARIANT_NON_SNV, "User input variant amino acid (%s) cannot be caused by an SNV from the reference amino acid (%s), therefore the returned results are position but not variant specific.",
            // variant empty - ACC POS REF
            ERR_CODE_VAR_EMPTY, "User input variant amino acid empty, therefore the returned results are position but not variant specific."
            // Ref and var empty - ACC POS (not needed, captured with ref & var empty above)
            //ERR_CODE_REF_AND_VAR_EMPTY, "User input reference and variant amino acids empty, therefore the returned results are position but not variant specific."
    );
}
