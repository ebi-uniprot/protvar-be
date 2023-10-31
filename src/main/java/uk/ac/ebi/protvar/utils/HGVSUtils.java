package uk.ac.ebi.protvar.utils;

import uk.ac.ebi.protvar.input.format.coding.HGVSc;
import uk.ac.ebi.protvar.input.format.genomic.HGVSg;
import uk.ac.ebi.protvar.input.format.protein.HGVSp;

/**
 * Refer to HGVS doc: https://varnomen.hgvs.org/bg-material/simple/
 */
public class HGVSUtils {

    public static final String COLON = ":";
    public static final String SUB_SIGN = ">";
    public static boolean maybeHGVS(String prefix, String scheme, String input) {
        String in = input.toUpperCase();
        return in.startsWith(prefix) && in.contains(scheme);
    }

    /**
     * Returns true if starts with one of the supported prefixes - NC_, NM_ or NP_
     * @param input
     * @return
     */
    public static boolean startsWithPrefix(String input) {
        String in = input.toUpperCase();
        if (in.startsWith(HGVSg.PREFIX)
              || in.startsWith(HGVSc.PREFIX)
              || in.startsWith(HGVSp.PREFIX)
        )
            return true;
        return false;
    }
}
