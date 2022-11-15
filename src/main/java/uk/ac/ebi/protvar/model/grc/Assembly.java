package uk.ac.ebi.protvar.model.grc;

import java.util.List;

public class Assembly {
    public static final String GRCh38 = "38";
    public static final String GRCh37 = "37";
    public static final List<String> VALID_ASSEMBLY_VERSIONS = List.of(GRCh38, GRCh37);
    public static final String DEFAULT_ASSEMBLY_VERSION = GRCh38;

    private static final String GRCH38_STR = "GRCH38";
    private static final String GRCH37_STR = "GRCH37";

    public static String get(String str) {
        str = str.toUpperCase();
        if (str.equals(GRCH38_STR))
            return GRCh38;
        if (str.equals(GRCH37_STR))
            return GRCh37;
        return null;
    }
}