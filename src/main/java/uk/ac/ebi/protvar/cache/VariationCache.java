package uk.ac.ebi.protvar.cache;

public class VariationCache {

    private static final String VAR_CACHE_PREFIX = "VAR-";

    public static String keyOf(String accLoc) {
        return VAR_CACHE_PREFIX+accLoc;
    }

}
