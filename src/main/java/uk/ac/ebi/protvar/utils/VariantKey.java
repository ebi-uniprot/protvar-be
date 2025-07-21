package uk.ac.ebi.protvar.utils;

import uk.ac.ebi.protvar.input.GenomicInput;
import uk.ac.ebi.protvar.model.score.ScoreType;

public class VariantKey {
    private static final String SEP = ":";

    private VariantKey() {
        // utility class; prevent instantiation
    }

    public static String protein(String accession, Integer position) {
        return String.join(SEP, safe(accession), safe(position));
    }

    public static String protein(String accession, String position) {
        return String.join(SEP, safe(accession), safe(position));
    }

    public static String protein(String accession, Integer position, String altAa) {
        return String.join(SEP, safe(accession), safe(position), safe(altAa));
    }

    public static String protein(ScoreType scoreType, String accession, Integer position, String mutatedType) {
        return String.join(SEP, safe(scoreType), safe(accession), safe(position), safe(mutatedType));
    }

    public static String genomic(String chromosome, Integer position) {
        return String.join(SEP, safe(chromosome), safe(position));
    }

    public static String genomic(String chromosome, Integer position, String altBase) {
        return String.join(SEP, safe(chromosome), safe(position), safe(altBase));
    }

    public static String genomic(GenomicInput input) {
        return String.join(SEP, safe(input.getChr()), safe(input.getPos()));
    }

    public static String genomicWithAlt(GenomicInput input) {
        return String.join(SEP, safe(input.getChr()), safe(input.getPos()), safe(input.getAlt()));
    }

    private static String safe(Object value) {
        return value == null ? "null" : value.toString();
    }
}
