package uk.ac.ebi.protvar.cache;

/**
 * Usage:
 * String key1 = CacheKey.of(CacheKey.PROT_CACHE_PREFIX, accession);
 * String key2 = CacheKey.protein(accession);
 */
public class CacheKey {

    public static final String INPUT_CACHE_PREFIX = "INPUT-";
    public static final String BUILD_CACHE_PREFIX = "BUILD-";
    public static final String SUMMARY_CACHE_PREFIX = "SUMMARY-";
    // add other prefixes as needed
    public static final String VARIANT_CACHE_PREFIX = "VAR-";


    public static String of(String prefix, String suffix) {
        return prefix + suffix;
    }

    // shortcut for common types
    public static String input(String id) {
        return of(INPUT_CACHE_PREFIX, id);
    }
    public static String inputBuild(String id) {
        return of(BUILD_CACHE_PREFIX, id);
    }
    public static String inputSummary(String id) {
        return of(SUMMARY_CACHE_PREFIX, id);
    }


    public static String variant(String accLoc) {
        return of(VARIANT_CACHE_PREFIX, accLoc);
    }
}
