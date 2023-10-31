package uk.ac.ebi.protvar.utils;

import lombok.Getter;

import java.util.regex.Pattern;

/**
 * Supported RefSeq accession prefixes for HGVS inputs
 * Source: The NCBI Handbook.
 */

@Getter
public class RefSeqUtils {
    public static final String NC = "NC_"; // Genomic, Complete genomic molecule, usually reference assembly
    public static final String NM = "NM_"; // mRNA, Protein-coding transcripts (usually curated) e.g. NM_001145445.1
    public static final String NP = "NP_"; // Protein, Associated with an NM_ or NC_ accession e.g. NP_001138917.1


    public static final String RS_ACC_PREFIX = "("+ NC +"|"+ NM +"|"+ NP +")";
    public static final String RS_ACC_NUM_PART = "(\\d+)(\\.\\d+)?"; // RefSeq number part regex (i.e. xxx in NC_xxx)

    public static final String RS_ACC = RS_ACC_PREFIX + RS_ACC_NUM_PART;


    public static boolean validRefSeqPrefix(String id) {
        return Pattern.matches("^"+ RS_ACC_PREFIX, id);
    }

    public static boolean validRefSeqId(String id) {
        return Pattern.matches("^"+ RS_ACC +"$", id);
    }

}
