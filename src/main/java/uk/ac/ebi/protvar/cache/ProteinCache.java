package uk.ac.ebi.protvar.cache;

public class ProteinCache {

    public static final String PROT_CACHE_PREFIX = "PROT-";

    public static String keyOf(String acc) {
        return PROT_CACHE_PREFIX+acc;
    }

}
